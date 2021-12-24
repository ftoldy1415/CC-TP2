package com.company;
import java.io.*;
import java.net.*;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.*;


public class Cliente{
    private InetAddress ipEnviar;
    private int port;
    private Demultiplexer dm;
    private DatagramSocket s;
    private Metadados m;
    private Ficheiro f;
    private String folderName;
    private Logger logger;
    private static int TOTAL_PACKETS = 0;

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

    /**
     *
     * @param l
     * @param pasta
     */
    public void comunInicial(List<DatagramPacket> l, String pasta){

        JavaHTTPServer.runServer();

        byte[] buffer = new byte[1024];
        buffer[0] = 0;
        DatagramPacket pi = new DatagramPacket(buffer, buffer.length);
        pi.setAddress(this.ipEnviar);
        pi.setPort(this.port);
        long start = 0, end = 0;
        int totalPackets = 0;
        try {
            this.dm.send(pi);
            this.logger.writeLog(new Log("Tentativa de conexão com: " + this.ipEnviar, LocalDateTime.now()));
            this.dm.timeoutRequest(0,100);
            this.dm.start();
            byte[] dataReceived = new byte[1024];
            DatagramPacket res = new DatagramPacket(dataReceived, dataReceived.length);
            res = this.dm.receive(0);
            this.dm.timeoutRequest(0,0);
            this.logger.writeLog(new Log("Conexão estabelecida com: " + this.ipEnviar , LocalDateTime.now()));

            //inicio da conexao
            start = System.currentTimeMillis();

            //envio de metadados
            enviaMetadados(l);
            this.logger.writeLog(new Log("Envio metadados", LocalDateTime.now()));

            //envio do resultado da comparação
            List<String> fileToSend = receiveResultCompare();

            //envio de ficheiros

            this.logger.writeLog(new Log("É necessário enviar os seguintes ficheiros: " + fileToSend.toString(), LocalDateTime.now()));
            this.logger.writeLog(new Log("Início do envio. À espera de confirmação da receção... ", LocalDateTime.now()));
            if(fileToSend.size() != 0){
                sendAllFiles(fileToSend, pasta);
            }
            sendConfirmationPacket();
            TOTAL_PACKETS++;
            this.logger.writeLog(new Log("Confirmação enviada!", LocalDateTime.now()));

            this.logger.writeLog(new Log("À espera de ficheiros...", LocalDateTime.now()));

            //rececao de ficheiros
            List<DatagramPacket> files = receiveFiles();
            this.logger.writeLog(new Log("Foram recebidos " + files.size() + " pacotes!", LocalDateTime.now()));
            this.logger.writeLog(new Log("A construir ficheiros...", LocalDateTime.now()));

            //contruir os ficheiros
            compileFiles(files,pasta);
            this.logger.writeLog(new Log("Ficheiros construídos!", LocalDateTime.now()));

            end = System.currentTimeMillis();
        } catch (IOException | ReceiveTimeOut | InterruptedException e) {
            this.logger.writeLog(new Log("Conexão falhou! Ficará à espera que alguém se tente conectar...", LocalDateTime.now()));
            try {
                byte[] pedra = new byte[1024];
                pedra[0] = 0;
                byte[] dataReceived = new byte[1024];
                DatagramPacket res = new DatagramPacket(dataReceived, dataReceived.length);
                res = this.dm.receive(0);

                start = System.currentTimeMillis();

                this.logger.writeLog(new Log("Conexão estabelecida com: " + this.ipEnviar, LocalDateTime.now()));
                DatagramPacket packetPedra = new DatagramPacket(pedra, pedra.length);
                packetPedra.setAddress(this.ipEnviar);
                packetPedra.setPort(this.port);
                this.dm.send(packetPedra);
                TOTAL_PACKETS++;
                this.logger.writeLog(new Log("À espera de metadados...", LocalDateTime.now()));
                List<Map.Entry<String, FileTime>> meta = recebeMetadados();
                this.logger.writeLog(new Log("Metadados recebidos! A comparar metadados...", LocalDateTime.now()));
                Map.Entry<List<String>, List<String>> fileList = m.compare(pasta, meta);                            //comparar os metadados recebidos do outro nó com os ficheiros da pasta deste nó
                fileList.getKey().forEach(System.out::println);
                fileList.getValue().forEach(System.out::println);

                //send compare result
                List<DatagramPacket> lp = this.m.serializeAsk(fileList.getKey());
                this.logger.writeLog(new Log("Será necessário receber os seguintes ficheiros: " + fileList.getKey(), LocalDateTime.now()));
                sendFileRequest(lp);

                //receive Files
                List<DatagramPacket> files = receiveFiles();
                this.logger.writeLog(new Log("Foram recebidos " + files.size() + " pacotes!", LocalDateTime.now()));
                this.logger.writeLog(new Log("A construir ficheiros...", LocalDateTime.now()));
                compileFiles(files,pasta);
                this.logger.writeLog(new Log("Ficheiros construídos!", LocalDateTime.now()));

                //send files
                this.logger.writeLog(new Log("Será necessário enviar os seguintes ficheiros: " + fileList.getValue(), LocalDateTime.now()));

                sendAllFiles(fileList.getValue(), pasta);
                this.logger.writeLog(new Log("Ficheiros enviados!", LocalDateTime.now()));
                sendConfirmationPacket();

                this.logger.writeLog(new Log("Pacote de confirmação enviado!", LocalDateTime.now()));
                end = System.currentTimeMillis();

            } catch (IOException | ReceiveTimeOut | InterruptedException ioException) {
                ioException.printStackTrace();
            }
        }
        //final da conexao
        this.logger.writeLog(new Log("Conexão concluída!", LocalDateTime.now()));
        this.logger.writeLog(new Log("Tempo total da conexão (ms): " + (end - start), LocalDateTime.now()));
        this.logger.writeLog(new Log("Débito final (bits/seg): " + ((TOTAL_PACKETS * 1024L * 8) /(end-start)), LocalDateTime.now()));
        try {
            this.logger.publishLogs();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Metodo responsavel por enviar os metadados dos ficheiros , esses metadados já foram serializados e enpacotados em DatagramPackets
     * Depois de enviar é verificado se esse envio foi feito corretamente e se nao houve perda dos pacotes , caso tenha havido perda eles serao reenviados
     * @param l
     */
    public void enviaMetadados(List<DatagramPacket> l){

        for(DatagramPacket p : l){
            p.setAddress(this.ipEnviar);                           //set do ip do peer
            p.setPort(this.port);                                  //set da porta do socket
            this.dm.send(p);
            TOTAL_PACKETS++;
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
                TOTAL_PACKETS++;
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
                TOTAL_PACKETS++;
                try {
                    receivePacket = this.dm.receive(1);          //receber pacote
                    TOTAL_PACKETS++;
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


    /**
     * Metodo que recebe os metadados enviados por outro peer , verifica se foram recebidos todos os pacotes
     * todos os pacotes tem no seu fim o numero de sequencia desse pacote e o total de pacotes que devem ser recebidos
     * assim pode desserializar-se um pacote e obter o numero total de pacotes que deveriam chegar
     * isso é usado para verificar se todos os pacotes foram recebidos , caso isso nao aconteça é enviado um pacote com os numeros de sequencia em falta,
     * e espera-se pelo reenvio dos mesmos.
     * @return
     */
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
            TOTAL_PACKETS++;
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
                missingIndex = 1;
                dp = this.dm.receive(1);                       //receber um pacote
                TOTAL_PACKETS++;
                data = dp.getData();
                short controlo = (short)(((data[2] & 0xFF) << 8) | (data[1] & 0xFF));
                if(controlo == -1){                               //no caso de receber o pacote de controlo envia os numeros de sequencia dos pacotes que nao foram recebidos
                    for(boolean b : numSeqReceived){
                        if(!b){
                            missingPacketsData[missingIndex++] = (byte) (missing_seq & 0xff);
                            missingPacketsData[missingIndex++] = (byte) ((missing_seq >> 8) & 0xff);
                        }
                        missing_seq++;
                    }
                    missingPacketsData[missingIndex++] = -1;
                    missingPacketsData[missingIndex++] = -1;
                    missingPackets = new DatagramPacket(missingPacketsData, missingPacketsData.length);
                    this.dm.send(missingPackets);
                    TOTAL_PACKETS++;
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
            TOTAL_PACKETS++;

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

    /**
     * Envia um pacote de confirmação com um formato específico
     */
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

    /**
     * Método que recebe os pacotes relativos aos ficheiros transferidos
     * @return packetList -> Lista com os pacotes de todos os ficheiros
     */
    public List<DatagramPacket> receiveFiles(){
        List<DatagramPacket> packetList = new ArrayList<>();
        DatagramPacket packet;
        byte[] data;
        try {
            packet = this.dm.receive(2);
            TOTAL_PACKETS++;
            data = packet.getData();
            System.out.println("Primeiro byte: " + data[1] + " | Segundo byte: " + data[2]);
            System.out.println("Parei aqui");
            while(data[1] != -1 && data[2] != -1){
                packetList.add(packet);
                try {
                    packet = this.dm.receive(2);
                    TOTAL_PACKETS++;
                    data = packet.getData();
                } catch (IOException | InterruptedException | ReceiveTimeOut e) {
                    System.out.println("::::::: TIMEOUT ::::::: ERROOO ::::::::: ");
                    e.printStackTrace();
                }
            }
        } catch (IOException | ReceiveTimeOut | InterruptedException e) {
            e.printStackTrace();
        }

        return packetList;
    }

    /**
     * Método que organiza os pacotes recebidos por nome de ficheiro, com o auxílio da organizePacketsByName da classe ficheiro,
     * desserializa esses mesmos pacotes e organiza-os por numero de sequencia, no método unpackAllData.
     * Por fim, cria os ficheiros com base nos nomes e nos dados de todos os pacotes recebidos
     * @param list -> Lista de todos os pacotes recebidos na receiveFiles()
     * @param pasta -> pasta onde estes ficheiros se encontram
     */
    public void compileFiles(List<DatagramPacket> list, String pasta){
        Map<String, List<DatagramPacket>> packetsByName = this.f.organizePacketsByName(list);
        Map<String, List<byte[]>> deserializedPackets = this.f.unpackAllData(packetsByName);
        this.f.createFiles(deserializedPackets, pasta);
    }

    public void sendCompareResult(List<String> filesToAsk){
        m.serializeAsk(filesToAsk);
    }

    /**
     *  Método que cria uma thread de envio de ficheiros para cada ficheiro
     * @param fileList -> Lista dos ficheiros a enviar
     * @param pasta -> caminho para a pasta onde estes se encontram
     */
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

    /**
     *  Método que serializa em pacotes o ficheiro especificado e envia-os para o ip e porta adequados
     * @param s nome do ficheiro
     * @param pasta pasta onde ele se encontra
     */
    public void sendFile(String s, String pasta){
        File fich = new File(pasta + "/" + s);
        System.out.println("Sending File: " + fich);

        try{
            List<DatagramPacket> packetList = this.f.serializeFile(fich);
            for(DatagramPacket p : packetList){
                p.setAddress(this.ipEnviar);
                p.setPort(this.port);
                this.dm.send(p);
                TOTAL_PACKETS++;
                System.out.println("enviadoo :: :: :: " + TOTAL_PACKETS);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     *  Método que recebe o resultado da comparação dos ficheiros. Implementa deteção e correção de perdas
     * @return Lista de ficheiros para enviar para o peer
     */
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
                TOTAL_PACKETS++;
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
        TOTAL_PACKETS++;

        return filesToSend;
    }

    /**
     * Método de envio dos pacotes de requisito de ficheiro. O método começa por realizar uma primeira tentativa de envio de todos os pacotes
     * Caso
     * @param lp lista de DatagramPackets a enviar
     */
    public void sendFileRequest(List<DatagramPacket> lp){
        for(DatagramPacket p : lp){
            p.setAddress(this.ipEnviar);
            p.setPort(this.port);
            this.dm.send(p);
            TOTAL_PACKETS++;
        }
        boolean response = false;
        while(!response) {
            try {
                this.dm.timeoutRequest(1,100);
                DatagramPacket Confirmationresponse = this.dm.receive(1);
                this.dm.timeoutRequest(1,0);
                TOTAL_PACKETS++;
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
                TOTAL_PACKETS++;
                try {
                    int seqNum;
                    int index = 1;
                    DatagramPacket missingPackets = this.dm.receive(1);
                    TOTAL_PACKETS++;
                    byte[] missingPacketsData = missingPackets.getData();   //extrair os dados do pacote
                    while ((seqNum = (short) (((missingPacketsData[index + 1] & 0xFF) << 8) | (missingPacketsData[index] & 0xFF))) != -1) {
                        DatagramPacket packet = lp.get(seqNum);
                        packet.setAddress(this.ipEnviar);
                        packet.setPort(this.port);
                        this.dm.send(packet);
                        TOTAL_PACKETS++;
                        index += 2;
                    }

                } catch (InterruptedException | IOException | ReceiveTimeOut ignored) {
                }
            }
        }

    }


}
