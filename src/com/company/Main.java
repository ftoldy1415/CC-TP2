package com.company;

import javax.xml.crypto.Data;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import static java.nio.file.attribute.FileTime.from;

public class Main {


    public static void main(String[] args) throws IOException {
        InetAddress ipEnviar = InetAddress.getByName("127.2.1.23");
        int port = 57200;

        Cliente c = new Cliente(ipEnviar, port);


        Metadados m = new Metadados();
        List<DatagramPacket> original = m.serializeFileMeta("/Users/brunofilipemirandapereira/Downloads/", "Downloads");
        System.out.println("O TAMANHO DESTA MERDA É :" + original.size());


        c.enviaMetadados(original);

    }
}

        /*
        try{
            Metadados m = new Metadados();
            List<DatagramPacket> original = m.serializeFileMeta("/Users/brunofilipemirandapereira/Downloads/","Downloads");
            System.out.println("O TAMANHO DESTA MERDA É :" + original.size());
            List<List<Map.Entry<String, FileTime>>> finalD = m.deserializePackets(original);
            int seq = 0;
            for( List<Map.Entry<String,FileTime>> l : finalD){
                if(l == null) System.out.println("Pasta");
                else{
                    System.out.println("DESERIALIZED: ");

                    for(int i = 0; i < l.size(); i++){;
                        System.out.println("NUMERO DE SEQUENCIA : " + seq + " FILE NAME : " + l.get(i).getKey());
                    }

                }
                seq++;

            }

        }catch (IOException e){
            System.out.println("ERRO: " + e.getMessage());
        }
        */



     /*

        Cliente c = new Cliente(InetAddress.getByName("localhost"),12345);


        //Path file = Path.of("/home/ftoldy/Área de Trabalho/prolog/ficha1.pl");
        File f = new File("/home/ftoldy/Área de Trabalho/prolog/ficha1.pl");

        List<byte[]> b = c.fileToByte(f);
        c.writeByte(b);





        /*
        BasicFileAttributes attr = null;

        try {
            attr = Files.readAttributes(file, BasicFileAttributes.class);
            System.out.println(attr.lastModifiedTime().toString());
            byte[] metaDataS = serializeMeta(attr,"ficha1.pl");
            deserializeMeta(metaDataS);

        } catch (IOException e) {
            e.printStackTrace();
        }

         */

        /*
        try{
            String currentPath = new java.io.File(".").getCanonicalPath();
            System.out.println("Current dir:" + currentPath);

        }catch (IOException e){
            System.out.println(e.getMessage());
        }

         */




