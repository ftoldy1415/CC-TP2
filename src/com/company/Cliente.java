package com.company;
import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.Array;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;


public class Cliente implements Runnable{
    private InetAddress ipEnviar;
    private int port;
    private int mss;
    private int metaSize;

    DatagramSocket s;

    public Cliente(InetAddress ip, int port) throws SocketException {
        this.ipEnviar = ip;
        this.port     = port;
        this.s = new DatagramSocket(port,ip);
        this.mss = 1024;
        this.metaSize = this.mss - 6;
    }

    public static void serialize(DataOutputStream out, BasicFileAttributes attr) throws IOException {
        out.writeUTF(String.valueOf(attr.creationTime()));
        out.writeUTF(attr.lastModifiedTime().toString());
        out.writeInt(Integer.parseInt(String.valueOf(attr.size())));
    }

    public DatagramPacket enviaInicioCon() throws IOException{
        byte[] b = new byte[1];
        b[0] = 0;
        DatagramPacket dp = new DatagramPacket(b,1);
        DatagramPacket rp = new DatagramPacket(b,1);
        this.s.send(dp);
        this.s.setSoTimeout(150);
        this.s.receive(rp);
        return rp;
    }

    public void recebeMeta(){
        byte [] size = new byte[4];
        DatagramPacket rp = new DatagramPacket(size,4);
        int num = ByteBuffer.wrap(size).getInt();
        byte [] metadados = new byte[num];
        DatagramPacket metadadosPacket = new DatagramPacket(metadados, num);
        Map<String,String> mapMetadados = parser(metadados);
    }



    public Map<String,String> parser(byte[] b){
        HashMap<String,String> resultado = new HashMap<>();
        int index = 0;
        int name_size;
        while (index < b.length){
            name_size = b[index++];
            String Name = new String(Arrays.copyOfRange(b, index, name_size));
            index+=name_size;
            String lastAccess = "aaa";
        }
        return null;
    }

    public void escuta(String directory, String pasta) throws IOException {
        byte[] b = new byte[1];
        b[0] = 0;
        DatagramPacket rp = new DatagramPacket(b,1);
        //this.s.setSoTimeout(60000);
        this.s.receive(rp);
        //byte[] toSend = serializeFileMeta(directory,pasta);
        //byte[] finalSend = new byte[1+toSend.length];
        //finalSend[0] = 1;
        //System.arraycopy(toSend, 0, finalSend, 1, toSend.length);
        //DatagramPacket metaDados = new DatagramPacket(finalSend,finalSend.length);
    }

    public byte[] serializeMeta(BasicFileAttributes attr,String name){
        short name_size = (short) name.length();
        long lastmodified = attr.lastModifiedTime().to(TimeUnit.SECONDS);
        byte [] nome = name.getBytes();
        byte[] b = new byte[name_size+10];
        System.arraycopy(nome,0,b,2,nome.length);
        b[0] = (byte) (name_size & 0xff);
        b[1] = (byte) ((name_size >> 8) & 0xff);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(lastmodified);
        byte[] longBuffer = buffer.array();
        System.arraycopy(longBuffer,0,b,nome.length+2,8);
        return b;
    }

    public Map.Entry<Integer, Map.Entry<String, FileTime>> deserializeMeta(byte[] b, int index){

        short name_size = (short)(((b[index+1] & 0xFF) << 8) | (b[index] & 0xFF));
        System.out.println(b[index+1]);
        System.out.println(b[index]);
        byte[] nomeFile = new byte[name_size];
        System.out.println(name_size);
        System.arraycopy(b,index+2,nomeFile,0,name_size);
        String name = new String(nomeFile);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        byte[] bufferBytes = new byte[8];
        System.arraycopy(b,index + 2 + name_size ,bufferBytes,0,8);
        buffer.put(bufferBytes);
        buffer.flip(); //need flip
        long lastModifiedLong = buffer.getLong();
        FileTime lastModifiedTime = FileTime.from(lastModifiedLong,TimeUnit.SECONDS);
        index += 2 + name_size + 8;

        return new AbstractMap.SimpleEntry<>(index, new AbstractMap.SimpleEntry<>(name, lastModifiedTime));

    }

    public List<DatagramPacket> serializeFileMeta(String s, String pasta) throws IOException {
        ArrayList<DatagramPacket> resultSerialized = new ArrayList<>();
        ArrayList<Map.Entry<Integer,byte[]>> provisional = new ArrayList<>();

        File f = new File(s); //
        int size_final = 0;
        short seq_number = 1;
        byte [] pastaB = pasta.getBytes();
        byte [] b = new byte[2];
        short size_name_pasta = (short) pastaB.length;
        b[0] = (byte) (size_name_pasta & 0xff);
        b[1] = (byte) ((size_name_pasta >> 8) & 0xff);

        byte[] pastaData = new byte[mss];

        System.arraycopy(b, 0, pastaData, 0, b.length);
        System.arraycopy(pastaB, 0, pastaData, 2, pastaB.length);


        long id = 0;
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(id);
        byte[] longBuffer = buffer.array();
        System.arraycopy(longBuffer, 0, pastaData, pastaB.length + b.length, longBuffer.length);

        DatagramPacket pastaPacket = new DatagramPacket(pastaData,mss);
        resultSerialized.add(pastaPacket);

        byte[] packetBuffer = new byte[mss];
        byte[] packetBufferData;

        for(String x : Objects.requireNonNull(f.list())){
            BasicFileAttributes attr = Files.readAttributes(Path.of(s +x), BasicFileAttributes.class);
            byte[] serializado = serializeMeta(attr,x);

            if (serializado.length < metaSize-size_final){
                System.arraycopy(serializado, 0, packetBuffer, size_final, serializado.length);

                System.out.println("FICHEIRO : " + x + " Serialized size " + serializado.length);
                size_final += serializado.length;
            }
            else {                                  //flag final de pacote
                System.out.println("Final de Pacote");
                packetBuffer[size_final++] = 0;
                packetBuffer[size_final++] = 0;
                packetBuffer[size_final++] = (byte) (seq_number & 0xff);
                packetBuffer[size_final++] = (byte) ((seq_number >> 8) & 0xff);
                seq_number++;
                packetBufferData = packetBuffer.clone();
                provisional.add(new AbstractMap.SimpleEntry<>(size_final, packetBufferData));
                size_final = 0; // quando pacote fecha, temos que voltar ao inicio do proximo pacote.
            }
        }

        System.out.println("Final de Pacote");
        packetBuffer[size_final++] = 0;
        packetBuffer[size_final++] = 0;
        packetBuffer[size_final++] = (byte) (seq_number & 0xff);
        packetBuffer[size_final++] = (byte) ((seq_number >> 8) & 0xff);
        seq_number++;
        packetBufferData = packetBuffer.clone();
        provisional.add(new AbstractMap.SimpleEntry<>(size_final, packetBufferData));

        for (Map.Entry<Integer, byte[]> e : provisional){ //cria os datagramPackets com o tamanho o nº de sequencia nos 2 bytes depois da informaçao a passar
            byte [] toAdd = e.getValue();
            int indexA = e.getKey();
            toAdd[indexA++] = (byte) (seq_number & 0xff);
            toAdd[indexA] = (byte) ((seq_number >> 8) & 0xff);
            DatagramPacket packetToAdd = new DatagramPacket(toAdd, toAdd.length);
            resultSerialized.add(packetToAdd);
        }
        System.out.println("Nº de pacotes" + resultSerialized.size());
        return resultSerialized;
    }

    /**
     * Deserializa um DatagramPacket relativo a metadados
     * @param dados
     * @return um map entry que contem o nº de sequencia e a lista de metadados contidos no datagramPacket deserializado
     */
    public Map.Entry<Integer, List<Map.Entry<String, FileTime>>> deserializeFileMeta(DatagramPacket dados) {

        //Map<String, FileTime> finalMap = new HashMap<>();
        byte dadosBytes[] = dados.getData();

        /*
        short folder_size = (short) (((dadosBytes[0] & 0xFF) << 8) | (dadosBytes[1] & 0xFF));
        byte[] nomePasta = new byte[folder_size];
        System.arraycopy(dadosBytes, 2, nomePasta, 0, folder_size);
        String name = new String(nomePasta);
        finalMap.put(name, null);


         */

        short seq_number, total;
        List<Map.Entry<String, FileTime>> listaMeta = null;
        int index = 0;

        FileTime file_time, compare; //compare é criado para fazer a verificação do long que representa FileTime. Usado para a verificação do primeiro elemento (pasta ou ficheiro)
        long compare_long = 0;
        compare = FileTime.from(compare_long,TimeUnit.SECONDS);
        int i = 0;
        listaMeta = new ArrayList<>();
        while (index < dadosBytes.length) {
            System.out.println("PASSAGEM Nº -> " + i);
            System.out.println("Index: " + index);
            System.out.println("Dados Bytes" + dadosBytes.length);
            i++;
            System.out.println("Primeiro byte: " + dadosBytes[index] + " | Segundo Byte: " + dadosBytes[index+1]);
            if(dadosBytes[index] == 0 && dadosBytes[index+1] == 0) {
                index += 2;
                System.out.println("Acabou o pacote");
                seq_number = (short) (((dadosBytes[index+1] & 0xFF) << 8) | (dadosBytes[index] & 0xFF));
                System.out.println("Nº seq :" + seq_number);
                System.out.println(listaMeta);
                return new AbstractMap.SimpleEntry<>((int) seq_number, listaMeta);
            }
            else{
                Map.Entry<Integer, Map.Entry<String, FileTime>> ret = deserializeMeta(dadosBytes, index);
                System.out.println("FILE TIME " + ret.getValue().getValue());
                file_time = ret.getValue().getValue();
                if(file_time.compareTo(compare) == 0){
                    System.out.println("Deserialize Pasta");
                    listaMeta.add(new AbstractMap.SimpleEntry<>(ret.getValue().getKey(), ret.getValue().getValue()));
                    System.out.println(listaMeta);
                    return new AbstractMap.SimpleEntry<>(0, listaMeta);
                }
                else{
                    System.out.println("Deserialize Meta");
                    listaMeta.add(new AbstractMap.SimpleEntry<>(ret.getValue().getKey(), ret.getValue().getValue()));
                    System.out.println(listaMeta);
                }
                index = ret.getKey();
                System.out.println("DEPOIS DA PASSAGEM " + index);
            }
        }
        return null;
    }

    public List<List<Map.Entry<String, FileTime>>> deserializePackets(List<DatagramPacket> lp){
        List<List<Map.Entry<String, FileTime>>> listaFinal = new ArrayList<>();
        Map.Entry<Integer, List<Map.Entry<String, FileTime>>> entry;
        int i = 0;
        for (DatagramPacket dp : lp){
            entry = deserializeFileMeta(lp.get(i++));
            listaFinal.add(entry.getKey(), entry.getValue());
            if (entry.getValue() == null) System.out.println("vai dar merda vaaaaaaaaiiiii");
        }
        return listaFinal;
    }

    public List<String> compare(String s , Map<String, FileTime> map) throws IOException {
        String currentPath = new java.io.File(".").getCanonicalPath();
        String filepath = currentPath + "/" + s + "/";
        System.out.println(filepath);
        ArrayList<String> files = new ArrayList<>();
        for (Map.Entry<String,FileTime> e : map.entrySet()){
            try {
                File f = new File(filepath+e.getKey());
                if (e.getValue() == null);
                else{
                    BasicFileAttributes attr = Files.readAttributes(Path.of(filepath + e.getKey()), BasicFileAttributes.class);
                    if (attr.lastModifiedTime().compareTo(e.getValue()) < 0){
                        files.add(e.getKey());
                    }
                }
            } catch(IOException exception){
                files.add(e.getKey());
            }
        }
        return files;
    }

    public List <byte[]> fileToByte(File file) throws IOException {
        FileInputStream fl = new FileInputStream(file);
        int size = (int) file.length();
        int packetSize = 100;
        int lastPacketSize = size % packetSize;
        System.out.println(lastPacketSize);
        int fullPackets = size/packetSize;
        System.out.println(fullPackets);
        List<byte[]> lista = new ArrayList<>();
        byte[] arr = new byte[100];
        int offset = 0;
        while (offset != fullPackets * packetSize && fl.readNBytes(arr, 0, packetSize) > 0){
            lista.add(arr.clone());
            offset += packetSize;
            System.out.println(offset);
        }
        byte[] lastArr = new byte[lastPacketSize];
        fl.readNBytes(lastArr, 0, lastPacketSize);
        lista.add(lastArr);

        fl.close();

        return lista;
    }

    public void writeByte(List<byte[]> bytes) {
        File f = new File("teste.pl");
        try {
            OutputStream os = new FileOutputStream(f);
            int i = 0;
            int size = bytes.size();
            while (i < size){
                os.write(bytes.get(i));
                System.out.println(bytes.get(i));
                i++;
            }
            System.out.println("Successfully"
                    + " byte inserted");
            os.close();
        }
        catch (Exception e) {
            System.out.println("Exception: " + e);
        }
    }

    //Start Connection -> enviar packet e ficar à espera de uma resposta
    //                 -> implementar um timeout
    //                 -> caso não seja recebida nenhuma resposta forçar uma exceção e passar para o modo "servidor"

    //Criação dos metadados
    //Envio dos Metadados
    //Envio dos dados


    public void run() {

    }
}
