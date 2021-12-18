package com.company;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Metadados {
    private int mss;
    private int metaSize;
    private int totalPackets;
    //0 -> comunicação inicial
    //1 -> pacotes de metadados



    public Metadados(){
        this.mss = 1024;
        this.metaSize = this.mss-6;
    }


    /**
     * Método que serializa os metadados de 1 ficheiro. Os primeiros 2 bytes do array representam o tamanho do nome do ficheiro
     * depois é inserido o nome do ficheiro convertido em bytes e por fim o lastmodified time convertido para segundos no formato long
     * @param attr file atributes do ficheiro em questão
     * @param name nome do ficheiro
     * @return
     */
    public byte[] serializeMeta(BasicFileAttributes attr,String name){
        long lastmodified = attr.lastModifiedTime().to(TimeUnit.SECONDS);
        byte [] nome = name.getBytes();
        short name_size = (short) nome.length;
        byte[] b = new byte[name_size+10];
        System.arraycopy(nome,0,b,2,nome.length);
        b[0] = (byte) (name_size & 0xff);
        b[1] = (byte) ((name_size >> 8) & 0xff);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(lastmodified);
        byte[] longBuffer = buffer.array();
        System.out.println();
        System.arraycopy(longBuffer,0,b,nome.length+2,8);
        return b;
    }

    /**
     * Método que faz a deserialização dos metadados de 1 ficheiro. Começa por tirar o tamanho do nome, alocando um array com o tamanho certo.
     * Converte o array de bytes do nome para uma string. Converte o long para lastmodified time.
     * @param b array de bytes
     * @param index posição em que é iniciada a leitura
     * @return Map entry com o tamanho total dos metadados como key e um map entry com o nome e filetime como valor.
     */
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

    /**
     * serializa o pacote que contem apenas o nome da pasta a que os ficheiros a sincronizar pertencem (para posterior comparação do lado do receptor)
     * O array pastaData tem como primeiro byte um byte de "código" que sinaliza que este datagrampacket irá conter metadados.
     * Para assinalar que o datagrampacket terá apenas uma pasta, o long que sucederia o nome do ficheiro é colocado a 0.
     * @param s
     * @param pasta
     * @return
     */
    private DatagramPacket serializePastaData (String s, String pasta, short totalPackets){

        byte [] pastaB = pasta.getBytes();             //byte array com o nome da pasta
        byte [] b = new byte[2];                       //cabeçalho para guardar o tamanho do nome da pasta
        short size_name_pasta = (short) pastaB.length;
        b[0] = (byte) (size_name_pasta & 0xff);
        b[1] = (byte) ((size_name_pasta >> 8) & 0xff);

        byte[] pastaData = new byte[mss];
        pastaData[0] = 1;

        System.arraycopy(b, 0, pastaData, 1, b.length);
        System.arraycopy(pastaB, 0, pastaData, 3, pastaB.length);


        long id = 0;
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(id);
        byte[] longBuffer = buffer.array();
        System.arraycopy(longBuffer, 0, pastaData, 1+pastaB.length + b.length, longBuffer.length);

        byte [] total = new byte[2];
        total[0] = (byte) (totalPackets & 0xff);
        total[1] = (byte) ((totalPackets >> 8) & 0xff);

        System.arraycopy(total, 0, pastaData, 1+pastaB.length+b.length+8, total.length);

        return new DatagramPacket(pastaData,mss);
    }

    /**
     * Método que fecha os arrays de bytes com terminação 00 mais o nº de sequência
     * @param packetBuffer buffer a encerrar
     * @param size_final tamanho final provisório
     * @param seq_number numero de sequencia
     * @return
     */
    private byte[] fechaPacote(byte[] packetBuffer, int size_final, int seq_number){
        packetBuffer[size_final++] = 0;
        packetBuffer[size_final++] = 0;
        packetBuffer[size_final++] = (byte) (seq_number & 0xff);
        packetBuffer[size_final] = (byte) ((seq_number >> 8) & 0xff);
        return packetBuffer.clone();
    }

    /**
     * Cria o DatagramPacket que tem como conteudo o byte array seguido do numero total de pacotes
     * @param e
     * @param total nº total de pacotes
     * @return  DatagramPacket finalizado
     */
    private DatagramPacket criaPacote(Map.Entry<Integer, byte[]> e, int total){
        byte [] toAdd = e.getValue();
        int indexA = e.getKey();
        toAdd[indexA++] = (byte) (total & 0xff);
        toAdd[indexA] = (byte) ((total >> 8) & 0xff);
        return new DatagramPacket(toAdd, toAdd.length);
    }


    public List<DatagramPacket> serializeFileMeta(String s, String pasta) throws IOException {
        ArrayList<DatagramPacket> resultSerialized = new ArrayList<>();
        ArrayList<Map.Entry<Integer,byte[]>> provisional = new ArrayList<>();

        File f = new File(s);
        int size_final;
        short seq_number = 1;
        int resultSerializedIndex = 0;



        byte[] packetBuffer = new byte[mss], packetBufferData;
        packetBuffer[0] = 1;
        size_final = 1;
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
                packetBufferData = fechaPacote(packetBuffer, size_final, seq_number++);
                provisional.add(new AbstractMap.SimpleEntry<>(size_final+4, packetBufferData));
                System.out.println("TAMANHO DO " + seq_number + "º PACOTE" + size_final);
                size_final = 1; // quando pacote fecha, temos que voltar ao inicio do proximo pacote.

            }
        }

        DatagramPacket pastaPacket = serializePastaData(s, pasta, seq_number); //serializa o nome da pasta
        resultSerialized.add(pastaPacket);

        System.out.println("Final de Pacote");
        packetBufferData = fechaPacote(packetBuffer, size_final, seq_number);
        provisional.add(new AbstractMap.SimpleEntry<>(size_final+4, packetBufferData));

        for (Map.Entry<Integer, byte[]> e : provisional){              //cria os datagramPackets com o tamanho o nº de sequencia nos 2 bytes depois da informaçao a passar
            DatagramPacket packetToAdd = criaPacote(e, seq_number);    //seq number vai ser o neste momento o número total de pacotes a enviar. Este nº vai ser colocado em todos os pacotes
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
        byte[] dadosBytes = dados.getData();

        /*
        short folder_size = (short) (((dadosBytes[0] & 0xFF) << 8) | (dadosBytes[1] & 0xFF));
        byte[] nomePasta = new byte[folder_size];
        System.arraycopy(dadosBytes, 2, nomePasta, 0, folder_size);
        String name = new String(nomePasta);
        finalMap.put(name, null);


         */

        short seq_number, total;
        List<Map.Entry<String, FileTime>> listaMeta = null;
        int index = 1;

        FileTime file_time, compare; //compare é criado para fazer a verificação do long que representa FileTime. Usado para a verificação do primeiro elemento (pasta ou ficheiro)
        long compare_long = 0;
        compare = FileTime.from(compare_long, TimeUnit.SECONDS);
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
                index += 2;
                System.out.println("Nº seq :" + seq_number);
                System.out.println(listaMeta);
                this.totalPackets = (short) (((dadosBytes[index+1] & 0xFF) << 8) | (dadosBytes[index] & 0xFF));
                System.out.println("TOTAL PACOTES: " + this.totalPackets);
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
                    this.totalPackets = (short) (((dadosBytes[index+1] & 0xFF) << 8) | (dadosBytes[index] & 0xFF));
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

    public int getTotalPackets(){
        return this.totalPackets;
    }



}
