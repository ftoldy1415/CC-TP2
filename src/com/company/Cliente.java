package com.company;
import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;


public class Cliente implements Runnable{
    private InetAddress ipEnviar;
    private int port;

    DatagramSocket s;

    public void Cliente(InetAddress ip, int port) throws SocketException {
        this.ipEnviar = ip;
        this.port     = port;
        this.s = new DatagramSocket(port,ip);

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

    public void escuta() throws IOException {
        byte[] b = new byte[1];
        b[0] = 0;
        DatagramPacket rp = new DatagramPacket(b,1);
        this.s.setSoTimeout(60000);
        this.s.receive(rp);

        //send metadata

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

    public void deserializeMeta(byte[] b){
        short name_size = (short)(((b[1] & 0xFF) << 8) | (b[0] & 0xFF));
        byte[] nomeFile = new byte[name_size];
        System.arraycopy(b,2,nomeFile,0,name_size);
        String name = new String(nomeFile);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        byte[] bufferBytes = new byte[8];
        System.arraycopy(b,2+name_size ,bufferBytes,0,8);
        buffer.put(bufferBytes);
        buffer.flip();//need flip
        long lastModifiedLong = buffer.getLong();
        FileTime LastModifiedTime = FileTime.from(lastModifiedLong,TimeUnit.SECONDS);
    }

    public ArrayList<byte[]> serializeFileMeta(String s) throws IOException {
        ArrayList<byte[]> resultSerialized = new ArrayList<>();
        File f = new File("/home/ftoldy/Área de Trabalho/prolog/");
        String[] fileNames = f.list();
        int i = 0;
        for(String x : Objects.requireNonNull(f.list())){
            BasicFileAttributes attr = Files.readAttributes(Path.of(s +x), BasicFileAttributes.class);
            byte[] serializado =  serializeMeta(attr,x);
            resultSerialized.add(serializado);
            i++;
        }
        return resultSerialized;
    }

    public void deserializeFileMeta(byte[] dados){
        
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
