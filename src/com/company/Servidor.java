package com.company;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;

public class Servidor implements Runnable{


    public static boolean compare (BasicFileAttributes attr, DataInputStream in) throws IOException {

        String creationTime = in.readUTF();
        String lastModifiedTime = in.readUTF();
        int size = in.readInt();

        String thisCreationTime = attr.creationTime().toString();
        String thisLastModifiedTime = attr.lastModifiedTime().toString();
        int thisSize = Integer.parseInt(String.valueOf(attr.size()));

        boolean equals = false;
        if (creationTime.equals(thisCreationTime) && lastModifiedTime.equals(thisLastModifiedTime) && size == thisSize)
            equals = true;

        return equals;
    }

    public void confirmConnection(InetAddress ip, int port ){
        try {

            DatagramSocket confirmSocket = new DatagramSocket(port, ip);
            byte[] confirmData = new byte[1024];
            confirmData[0] = 0;
            DatagramPacket response = new DatagramPacket(confirmData,confirmData.length);
            confirmSocket.send(response);
            confirmSocket.close();

        }  catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void run(){
        try {
            byte[] receiveData = new byte[1024];
            DatagramSocket dataSocket = new DatagramSocket(8888);
            while(true){
                DatagramPacket dataPacket = new DatagramPacket(receiveData,receiveData.length);
                dataSocket.receive(dataPacket);

                receiveData = dataPacket.getData();

                int firstByte = receiveData[0];

                switch (firstByte){
                    case 0: // inicio de conexão
                        confirmConnection(dataPacket.getAddress(),dataPacket.getPort());    //envia confirmação de conexão para quem enviou o datapacket de inicio de confirmação
                        break;

                    case 1: //receber metadados

                        break;

                    case 2: //receção de dados
                        break;
                }


            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}