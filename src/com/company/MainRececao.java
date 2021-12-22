package com.company;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainRececao {

    public static void main(String[] args) throws IOException {
        try {
            DatagramSocket s = new DatagramSocket(12346);
            Demultiplexer d = new Demultiplexer(s,12346, InetAddress.getByName(args[1]));
            Ficheiro f      = new Ficheiro();
            InetAddress ipEnvio = InetAddress.getByName(args[1]);
            System.out.println(ipEnvio.toString());
            System.out.println(args[0]);
            Cliente c = new Cliente(ipEnvio,12345,d,s,args[0],f);
            Metadados m = new Metadados();
            List<DatagramPacket> original = m.serializeDataToPacket(args[0]);
            c.comunInicial(original ,args[0]);


        } catch (IOException e) {
            e.printStackTrace();
        }


        /*
        InetAddress ipEnviar = InetAddress.getByName("localhost");
        int port = 57200;

        DatagramSocket s = new DatagramSocket(57201);
        Demultiplexer dm = new Demultiplexer(s);

        //dm.start();

        Cliente c = new Cliente(ipEnviar, port, dm, s, "");

        //try {
            //Metadados m = new Metadados();
        c.comunInicial(new ArrayList<>(), "Guiao8");


            /*

            List<List<Map.Entry<String, FileTime>>> finalD = m.deserializePackets(original);
            int seq = 0;
            for (List<Map.Entry<String, FileTime>> l : finalD) {
                if (l == null) System.out.println("Pasta");
                else {
                    System.out.println("DESERIALIZED: ");

                    for (int i = 0; i < l.size(); i++) {
                        System.out.println("NUMERO DE SEQUENCIA : " + seq + " FILE NAME : " + l.get(i).getKey());
                    }

                }
                seq++;

            }

             */

      //  } catch (IOException e) {
      //      System.out.println("ERRO: " + e.getMessage());
      //  }

    }

}
