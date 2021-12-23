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
    private Ficheiro f;
    private String folderName;
    private Logger logger;

    public Cliente(InetAddress ip, int port, Demultiplexer dm, DatagramSocket s, String folderName , Ficheiro ficheiro) throws SocketException {
        this.ipEnviar   = ip;
        this.port       = port;
        this.dm         = dm;
        this.s          = s;
        this.m          = new Metadados();
        this.folderName = folderName;
        this.f          = ficheiro;
        this.logger     = new Logger();
    }
    

    public void comunInicial(List<DatagramPacket> l, String pasta){
        byte[] buffer = new byte[1024];
        buffer[0] = 0;
        DatagramPacket pi = new DatagramPacket(buffer, buffer.length);
        pi.setAddress(this.ipEnviar);
        pi.setPort(this.port);
        try {
            //this.s.send(pi);
            this.dm.send(pi);
            System.out.println("Mandei a pedra");
            logger.writeLog(new Log(InetAddress.getLocalHost().getHostAddress() +" faz tentativa de conexão", LocalDateTime.now()));
            //this.s.setSoTimeout(100);
            this.dm.start();
            byte[] dataReceived = new byte[1024];
            DatagramPacket res = new DatagramPacket(dataReceived, dataReceived.length);
            //this.s.receive(res);
            this.dm.timeoutRequest(0,100);
            res = this.dm.receive(0);
            this.dm.timeoutRequest(0,0);
            System.out.println("Recebi a pedra, tamos em comunicação");
            logger.writeLog(new Log ("Conexão estabelecida com : " + this.ipEnviar.toString(), LocalDateTime.now()));
            //enviaMetadados
            //this.dm.start();
            //Thread envio = new Thread(() -> enviaMetadados(l));
            enviaMetadados(l);
            //receive compare
            List<String> fileToSend = receiveResultCompare();
            //send files
            System.out.println("\nTamanho do resultado da comparacao:: " + fileToSend.size());
            logger.writeLog(new Log("Vao ser enviados os seguintes ficheiros : " + fileToSend.toString(), LocalDateTime.now()));
            logger.writeLog(new Log("Inicio do envio", LocalDateTime.now()));
            if(fileToSend.size() != 0){
                sendAllFiles(fileToSend, pasta);
            }
            //confirmation packet (no more files)
            sendConfirmationPacket();
            logger.writeLog(new Log("Envio de pacote de confirmação", LocalDateTime.now()));
            System.out.println(fileToSend.toString());
            logger.writeLog(new Log("À espera dos ficheiros em falta...", LocalDateTime.now()));
            //receive files
            List<DatagramPacket> files = receiveFiles();
            System.out.println("files.size(): " + files.size());
            logger.writeLog(new Log("Foram recebidos " + files.size() + " pacotes " , LocalDateTime.now()));
            compileFiles(files,pasta);
            logger.writeLog(new Log("Ficheiros gerados" , LocalDateTime.now()));
            //confirmacao de 'vou enviar ficheiros'
            //send compared files
            //criação thread (tag 1, envio de metadados)
        } catch (IOException | ReceiveTimeOut | InterruptedException e) {
            System.out.println("Não recebi a pedra de volta");
            logger.writeLog(new Log("Tentativa de conexão falhada. Irá ficar à espera...", LocalDateTime.now()));
            try {
                byte[] pedra = new byte[1024];
                pedra[0] = 0;
                //this.s.setSoTimeout(0);
                System.out.println("Tou a espera de uma pedra");
                byte[] dataReceived = new byte[1024];
                DatagramPacket res = new DatagramPacket(dataReceived, dataReceived.length);
                //this.s.receive(res);
                res = this.dm.receive(0);
                System.out.println("Recebi uma pedra, vou mandar uma pedra de volta");
                logger.writeLog(new Log("Conexão estabelecida!", LocalDateTime.now()));
                DatagramPacket packetPedra = new DatagramPacket(pedra, pedra.length);
                packetPedra.setAddress(this.ipEnviar);
                packetPedra.setPort(this.port);
                //this.s.send(packetPedra);
                this.dm.send(packetPedra);
                logger.writeLog(new Log("À espera de metadados...", LocalDateTime.now()));
                //this.dm.start();
                List<Map.Entry<String, FileTime>> meta = recebeMetadados();
                logger.writeLog(new Log("Metadados recebidos", LocalDateTime.now()));
                Map.Entry<List<String>, List<String>> fileList = m.compare(pasta, meta);//comparar os metadados recebidos do outro nó com os ficheiros da pasta deste nó
                logger.writeLog(new Log("Resultado da comparação: \nTerá de pedir os seguintes ficheiros: " + fileList.getKey() , LocalDateTime.now()));
                System.out.println("\n\nresultado comparação:\n");
                System.out.println("\nFiles to Ask");
                fileList.getKey().forEach(System.out::println);
                System.out.println("\nFiles to Send");
                fileList.getValue().forEach(System.out::println);
                //send compare result
                List<DatagramPacket> lp = this.m.serializeAsk(fileList.getKey());
                sendFileRequest(lp);
                logger.writeLog(new Log("Pedido de ficheiros executado. À espera dos ficheiros...", LocalDateTime.now()));
                //receive Files
                List<DatagramPacket> files = receiveFiles();
                logger.writeLog(new Log("Foram recebidos " + files.size() + " ficheiros", LocalDateTime.now()));
                System.out.println("files.size(): " + files.size());
                logger.writeLog(new Log("A construir ficheiros...", LocalDateTime.now()));
                compileFiles(files,pasta);
                //send files
                logger.writeLog(new Log("A enviar ficheiros necessários. Vão ser enviados: " + fileList.getValue(), LocalDateTime.now()));
                sendAllFiles(fileList.getValue(), pasta);
                logger.writeLog(new Log("Envio de pacote de confirmação", LocalDateTime.now()));
                sendConfirmationPacket();

            } catch (IOException | ReceiveTimeOut | InterruptedException ioException) {
                ioException.printStackTrace();
            }
            try {
                logger.publishLogs();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
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
                this.dm.timeoutRequest(1,1000);                    //set do timeout da resposta
                receivePacket = this.dm.receive(1);//bloqueia até receber uma resposta
                this.dm.timeoutRequest(1,0);
                System.out.println("Recebi o pacote de confirmação");
                response = true;//
            }catch(IOException | InterruptedException |  ReceiveTimeOut e){
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
                } catch (IOException | InterruptedException | ReceiveTimeOut exception) {
                    exception.printStackTrace();
                }
            }
            System.out.println("O VALOR DE RESPONSE É : " + response);
        }
    }



    public List<Map.Entry<String, FileTime>> recebeMetadados(){
        List<Map.Entry<String, FileTime>> metaList = new ArrayList<>();

        int total, totalRecebidos = 0, received;
        DatagramPacket dp;
        Map.Entry<Integer, List<Map.Entry<String, FileTime>>> e;
        byte[] data;//
        byte[] missingPacketsData = new byte[1024];
        missingPacketsData[0] = 1; //tag : metadados
        DatagramPacket missingPackets;
        short missingIndex = 1, missing_seq = 0;

        try {

            dp = this.dm.receive(1);                           //receber o primeiro pacote
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
            System.out.println("NUMERO TOTAL DE PACOTES: " + total);
            while(totalRecebidos < total){
                this.s.setSoTimeout(10000);                       //nao deve estar a funcionar
                dp = this.dm.receive(1);                       //receber um pacote
                data = dp.getData();
                short controlo = (short)(((data[3] & 0xFF) << 8) | (data[2] & 0xFF));
                if(controlo == -1){                               //no caso de receber o pacote de controlo envia os numeros de sequencia dos pacotes que nao foram recebidos
                    for(boolean b : numSeqReceived){
                        if(!b){
                            missingPacketsData[missingIndex++] = (byte) (missing_seq & 0xff);
                            missingPacketsData[missingIndex++] = (byte) ((missing_seq >> 8) & 0xff);
                        }
                        missing_seq++;
                    }
                    missingPackets = new DatagramPacket(missingPacketsData, missingPacketsData.length);
                    this.dm.send(missingPackets);
                }
                else{                                              //no caso de nao ser um pacote de controlo vai desserializar e retirar os metadados do pacote
                    e = this.m.deserializeFileMeta(dp);
                    received = e.getKey();
                    metaList.addAll(e.getValue());
                    numSeqReceived.add(received, true);     //anota o numero de sequencia do pacote como tendo sido recebido
                    totalRecebidos++;                             //aumenta o numero de pacotes recebidos
                }

            }

            System.out.println("CHEGOU AO ENVIO FINAL");
            System.out.println("Tamanho Meta List" + metaList.size());

            short finalConfirmation = -1;
            byte[] confirmation = new byte[1024];
            confirmation[0] = 1;
            confirmation[1] = (byte) (finalConfirmation & 0xff);
            confirmation[2] = (byte) ((finalConfirmation >> 8) & 0xff);
            DatagramPacket confirmationPacket = new DatagramPacket(confirmation, confirmation.length);
            confirmationPacket.setAddress(this.ipEnviar);
            System.out.println("Enviei para o IP: " + this.ipEnviar);
            confirmationPacket.setPort(this.port);
            System.out.println("Enviei para a porta: " + this.port);
            this.dm.send(confirmationPacket);

            for(Map.Entry<String, FileTime> entry : metaList){
                System.out.println(entry.getKey());
            }

        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }catch (ReceiveTimeOut erro){
            System.out.println(erro.getMessage());
        }
        return metaList;
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


    public void sendConfirmationPacket(){
        byte[] dataPacket = new byte[1024];
        dataPacket[0] = 2;
        short n = -1;
        dataPacket[1] = (byte) (n & 0xff);
        dataPacket[2] = (byte) ((n >> 8) & 0xff);
        DatagramPacket packet = new DatagramPacket(dataPacket, dataPacket.length);
        packet.setAddress(this.ipEnviar);
        packet.setPort(this.port);

        this.dm.send(packet);
    }

    public List<DatagramPacket> receiveFiles(){
        List<DatagramPacket> packetList = new ArrayList<>();
        DatagramPacket packet;
        byte[] data;
        try {
            packet = this.dm.receive(2);
            data = packet.getData();
            System.out.println("Primeiro byte: " + data[1] + " | Segundo byte: " + data[2]);
            System.out.println("Parei aqui");
            while(data[1] != -1 && data[2] != -1){
                packetList.add(packet);
                try {
                    packet = this.dm.receive(2);
                    data = packet.getData();
                } catch (IOException | InterruptedException | ReceiveTimeOut e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException | ReceiveTimeOut | InterruptedException e) {
            e.printStackTrace();
        }

        return packetList;
    }

    public void compileFiles(List<DatagramPacket> list, String pasta){
        Map<String, List<DatagramPacket>> packetsByName = this.f.organizePacketsByName(list);
        Map<String, List<byte[]>> deserializedPackets = this.f.unpackAllData(packetsByName);
        this.f.createFiles(deserializedPackets, pasta);
    }


    public void sendCompareResult(List<String> filesToAsk){
        m.serializeAsk(filesToAsk);
    }

    public void sendAllFiles(List<String> fileList, String pasta){
        try{
            int n_threads = fileList.size();
            Thread[] threadsEnvio = new Thread[n_threads];
            int i = 0;
            for(String file : fileList){
                threadsEnvio[i] = new Thread(() -> sendFile(file, pasta));
                i++;
            }

            for(i = 0; i < n_threads; i++){
                threadsEnvio[i].start();
            }

            for(i = 0; i < n_threads; i++){
                threadsEnvio[i].join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public void sendFile(String s, String pasta){
        File fich = new File(pasta + "/" + s);
        System.out.println("Sending File: " + fich);

        try{
            List<DatagramPacket> packetList = this.f.serializeFile(fich);
            for(DatagramPacket p : packetList){
                p.setAddress(this.ipEnviar);
                p.setPort(this.port);
                this.dm.send(p);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public List<String> receiveResultCompare(){
        List<String> filesToSend = new ArrayList<>();
        List<Integer> seqReceived = new ArrayList<>();
        int totalReceived = -1;
        int total = 0;
        int index = 1;
        int name_size,seqNum;
        byte[] nameBytes;
        try {
            while(totalReceived < total) {
                this.dm.timeoutRequest(1,1000);
                DatagramPacket p = this.dm.receive(1);
                System.out.println("::::::  receive compare ::::::");
                byte[] pData = p.getData();
                short control = (short) (((pData[2] & 0xFF) << 8) | (pData[1] & 0xFF));
                if(control == -1){
                    int missingIndex = 0;
                    byte[] missing = new byte[1024];
                    for(int i = 0; i < seqReceived.size() ; i++){
                        if(!seqReceived.contains(i)){
                            missing[missingIndex++] = (byte) (i & 0xFF);
                            missing[missingIndex++] = (byte) ((i >> 8) & 0xFF);
                        }
                    }
                    missing[missingIndex++] = (byte) (-1 & 0xFF);
                    missing[missingIndex++] = (byte) ((-1 >> 8) & 0xFF);
                }else{
                    index = 1;
                    System.out.println(":::: else ::::");
                    System.out.println("Primeiro byte: " + pData[index] + " | Segundo byte: " + pData[index+1]);
                    while(index < pData.length){
                        if(pData[index] == 0 && pData[index+1] == 0) break;

                        name_size = (short) (((pData[index + 1] & 0xFF) << 8) | (pData[index] & 0xFF));
                        nameBytes = new byte[name_size];
                        index += 2;
                        System.arraycopy(pData,index,nameBytes,0,name_size);
                        String name = new String(nameBytes);
                        System.out.println("name LIDO ::" + name);
                        filesToSend.add(name);
                        index += name_size;

                    }
                    index += 2;
                    seqNum = (short) (((pData[index + 1] & 0xFF) << 8) | (pData[index] & 0xFF));
                    System.out.println("Seq number receive files: " + seqNum);
                    seqReceived.add(seqNum);
                    if(totalReceived == -1 && total == 0){
                        index += 2;
                        total = (((pData[index + 1] & 0xFF) << 8) | (pData[index] & 0xFF));
                        totalReceived = 1;
                        System.out.println("total:: " + total);
                    }else{
                        totalReceived++;
                    }
                }
            }

        } catch (InterruptedException | IOException | ReceiveTimeOut ignore) {
            System.out.println("" +
                    "");
        }
        short finalConfirmation = -1;
        byte[] confirmation = new byte[1024];
        confirmation[0] = 1;
        confirmation[1] = (byte) (finalConfirmation & 0xff);
        confirmation[2] = (byte) ((finalConfirmation >> 8) & 0xff);
        DatagramPacket confirmationPacket = new DatagramPacket(confirmation, confirmation.length);
        confirmationPacket.setAddress(this.ipEnviar);
        confirmationPacket.setPort(this.port);
        this.dm.send(confirmationPacket);

        return filesToSend;
    }

    public void sendFileRequest(List<DatagramPacket> lp){
        for(DatagramPacket p : lp){
            p.setAddress(this.ipEnviar);
            p.setPort(this.port);
            this.dm.send(p);
        }
        boolean response = false;
        while(!response) {
            try {
                //this.dm.timeoutRequest(1, 2000);
                DatagramPacket Confirmationresponse = this.dm.receive(1);
                response = true;
            } catch (ReceiveTimeOut | IOException | InterruptedException e) {
                System.out.println("time out sendFileRequest");
                byte[] controlBuffer = new byte[1024];              //buffer para pacote de controlo
                short control = -1;                                 //flag de controlo
                controlBuffer[0] = 1;                               //tag do pacote
                controlBuffer[1] = (byte) (control & 0xff);         //inicializar o primeiro byte da flag de controlo
                controlBuffer[2] = (byte) ((control >> 8) & 0xff);  //inicializar o segundo byte da flag de controlo
                DatagramPacket controlPacket = new DatagramPacket(controlBuffer, controlBuffer.length); //pacote a enviar
                controlPacket.setAddress(this.ipEnviar);            //set do endereço ip para onde vai ser enviado o pacote
                controlPacket.setPort(this.port);                   //set da porta para onde sera reencaminhado o pacote no destino
                this.dm.send(controlPacket);
                try {
                    int seqNum;
                    int index = 1;
                    DatagramPacket missingPackets = this.dm.receive(1);
                    byte[] missingPacketsData = missingPackets.getData();   //extrair os dados do pacote
                    while ((seqNum = (short) (((missingPacketsData[index + 1] & 0xFF) << 8) | (missingPacketsData[index] & 0xFF))) != -1) {
                        //reenviar os pacotes que nao chegaram ao destino
                        DatagramPacket packet = lp.get(seqNum);
                        packet.setAddress(this.ipEnviar);
                        packet.setPort(this.port);
                        this.dm.send(packet);
                        index += 2;
                    }

                } catch (InterruptedException | IOException | ReceiveTimeOut ignored) {
                }
            }
        }

    }


    public void run() {
    }

}
