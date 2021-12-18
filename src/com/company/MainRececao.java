package com.company;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;

public class MainRececao {

    public static void main(String[] args) throws IOException {
        InetAddress ipEnviar = InetAddress.getByName("127.2.1.23");
        int port = 57200;

        Cliente c = new Cliente(ipEnviar, port);

        try {
            Metadados m = new Metadados();
            List<DatagramPacket> original = c.recebeMetadados();

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

        } catch (IOException e) {
            System.out.println("ERRO: " + e.getMessage());
        }
    }

}
