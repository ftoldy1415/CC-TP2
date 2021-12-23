package com.company;

import java.io.IOException;
import java.net.*;
import java.util.List;

public class MainFinal {

    public static void main(String[] args){

        try {
            DatagramSocket s = new DatagramSocket(12345);
            Demultiplexer d = new Demultiplexer(s,12345, InetAddress.getByName(args[1]));
            Ficheiro f = new Ficheiro();

            InetAddress ipEnvio = InetAddress.getByName(args[1]);
            System.out.println(ipEnvio.toString());
            System.out.println(args[0]);
            Cliente c = new Cliente(ipEnvio,12345,d,s,args[0],f);
            Metadados m = new Metadados();
            List<DatagramPacket> original = m.serializeDataToPacket(args[0]);
            c.comunInicial(original, args[0]);


        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
