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
    private Demultiplexer dm;
    private DatagramSocket s;
    private Metadados m;

    public Cliente(InetAddress ip, int port) throws SocketException {
        this.ipEnviar = ip;
        this.port     = port;
        this.s        = new DatagramSocket(port,ip);
        this.dm       = new Demultiplexer(this.s);
        this.m        = new Metadados();
    }

    public void comunInicial(){
        byte[] buffer = new byte[1024];
        buffer[0] = 0;
        DatagramPacket pi = new DatagramPacket(buffer, buffer.length);
        this.dm.send(pi);
        try {
            this.s.setSoTimeout(100);
            DatagramPacket res = this.dm.receive(0);
            //enviaMetadados

            //criação thread (tag 1, envio de metadados)
        } catch (IOException e) {
            e.printStackTrace();
        }catch(InterruptedException e){
            //o seu peer nao estava à escuta portanto este fica à espera de uma pedra -> que significa que está pronto
            //ao receber a pedra responde tambem com um pedra , fica assim estabelecida a primeira comunicação que indica que ambos estao prontos
            //em seguida o peer vai enviar os metadados do ficheiro que serão recebidos
            //escuta();

            //criação thread (tag 1, receção de metadados)
        }
    }

    public void enviaMetadados(List<DatagramPacket> l){

        for(DatagramPacket p : l){
            p.setAddress(this.ipEnviar);                           //set do ip do peer
            p.setPort(this.port);                                  //set da porta do socket
            this.dm.send(p);
        }
        boolean response = false;                                  //flag da resposta de confirmação
        DatagramPacket receivePacket;
        byte[] missingPacketsData;
        short seqNum;
        int index = 1;

        while(!response){                                          //verifica se já foi recebida a resposta de confirmação
            try{
                this.s.setSoTimeout(100);                          //set do timeout da resposta
                receivePacket = this.dm.receive(1);             //bloqueia até receber uma resposta
                response = true;//

            }catch(IOException | InterruptedException e){
                byte[] controlBuffer = new byte[1024];              //buffer para pacote de controlo
                short control = -1;                                 //flag de controlo
                controlBuffer[0] = 1;                               //tag do pacote
                controlBuffer[1] = (byte) (control & 0xff);         //inicializar o primeiro byte da flag de controlo
                controlBuffer[2] = (byte) ((control >> 8) & 0xff);  //inicializar o segundo byte da flag de controlo
                DatagramPacket controlPacket = new DatagramPacket(controlBuffer, controlBuffer.length); //pacote a enviar
                controlPacket.setAddress(this.ipEnviar);            //set do endereço ip para onde vai ser enviado o pacote
                controlPacket.setPort(this.port);                   //set da porta para onde sera reencaminhado o pacote no destino
                this.dm.send(controlPacket);                        //enviar o pacote
                try {
                    receivePacket = this.dm.receive(1);          //receber pacote
                    missingPacketsData = receivePacket.getData();   //extrair os dados do pacote
                    while((seqNum = (short) (((missingPacketsData[index+1] & 0xFF) << 8) | (missingPacketsData[index] & 0xFF))) != -1){
                        //reenviar os pacotes que nao chegaram ao destino
                        DatagramPacket packet = l.get(seqNum);
                        packet.setAddress(this.ipEnviar);
                        packet.setPort(this.port);
                        this.dm.send(packet);
                        index += 2;
                    }
                } catch (IOException | InterruptedException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }



    public List<DatagramPacket> recebeMetadados(){
        List<Map.Entry<String, FileTime>> metaList = new ArrayList<>();

        int total, totalRecebidos = 0, received;
        DatagramPacket dp;
        Map.Entry<Integer, List<Map.Entry<String, FileTime>>> e;
        byte[] data;//
        byte[] missingPacketsData = new byte[1024];
        missingPacketsData[0] = 1; //tag : metadados
        DatagramPacket missingPackets;
        short missingIndex = 0;

        try {

            dp = this.dm.receive(1);                            //receber o primeiro pacote
            e = this.m.deserializeFileMeta(dp);                    //desserializar o primeiro pacote
            received = e.getKey();                                 //retirar o numero de sequencia
            total = this.m.getTotalPackets();                      //retirar o numero total de pacotes
            for(Map.Entry<String, FileTime> entry : e.getValue()){
                metaList.add(entry);                               //adicionar o par constituido <Nome,Data da Ultima Alteração>
            }

            List<Boolean> numSeqReceived = new ArrayList<>(total); //lista para apontar os pacotes que chegam

            for(int i = 0; i < total; i++){
                numSeqReceived.add(i, false);               //inicializar a lista
            }

            numSeqReceived.add(received, true);             //coloca como recebido o indice do 1º pacote lido
            totalRecebidos++;                                     //incrementar o total de pacotes recebidos

            while(totalRecebidos < total ){
                this.s.setSoTimeout(100);
                dp = this.dm.receive(1);                       //receber um pacote
                data = dp.getData();
                short controlo = (short)(((data[3] & 0xFF) << 8) | (data[2] & 0xFF));
                if(controlo == -1){                               //no caso de receber o pacote de controlo envia os numeros de sequencia dos pacotes que nao foram recebidos
                    for(boolean b : numSeqReceived){
                        if(!b){
                            missingPacketsData[missingIndex] = (byte) (missingIndex & 0xff);
                            missingPacketsData[missingIndex] = (byte) ((missingIndex >> 8) & 0xff);
                        }
                        missingIndex++;
                    }
                    missingPackets = new DatagramPacket(missingPacketsData, missingPacketsData.length);
                    this.dm.send(missingPackets);
                }
                else{                                              //no caso de nao ser um pacote de controlo vai desserializar e retirar os metadados do pacote
                    e = this.m.deserializeFileMeta(dp);
                    received = e.getKey();
                    for(Map.Entry<String, FileTime> entry : e.getValue()){
                        metaList.add(entry);
                    }
                    numSeqReceived.add(received, true);     //anota o numero de sequencia do pacote como tendo sido recebido
                    totalRecebidos++;                             //aumenta o numero de pacotes recebidos
                }

            }

            short finalConfirmation = -1;
            byte[] confirmation = new byte[1024];
            confirmation[0] = 1;
            confirmation[1] = (byte) (finalConfirmation & 0xff);
            confirmation[2] = (byte) ((finalConfirmation >> 8) & 0xff);
            DatagramPacket confirmationPacket = new DatagramPacket(confirmation, confirmation.length);
            this.dm.send(confirmationPacket);

        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
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
        //byte[] finalSend = ve(rp);new byte[1+toSend.length];
        //finalSend[0] = 1;
        //System.arraycopy(toSend, 0, finalSend, 1, toSend.length);
        //DatagramPacket metaDados = new DatagramPacket(finalSend,finalSend.length);
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
