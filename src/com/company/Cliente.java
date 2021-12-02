package com.company;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

//olaaa

public class Cliente {

    public static void serialize(DataOutputStream out, BasicFileAttributes attr) throws IOException {
        out.writeUTF(String.valueOf(attr.creationTime()));
        out.writeUTF(attr.lastModifiedTime().toString());
        out.writeInt(Integer.parseInt(String.valueOf(attr.size())));
    }



    public static void main(String[] args) {
        Path file = Path.of("/home/ftoldy/Área de Trabalho/prolog/ficha1.pl");
        BasicFileAttributes attr = null;
        try {
            attr = Files.readAttributes(file, BasicFileAttributes.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            Socket socket = new Socket("localhost", 12345);

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            BufferedReader systemIn = new BufferedReader(new InputStreamReader(System.in));

            assert attr != null;
            serialize(out,attr);

            out.flush();

            System.out.println("siuuuuuuuuuuuuuuu");

            socket.shutdownOutput();
            String response;
            while ((response = in.readLine()) != null){
                System.out.println("Resposta: " + response);
            }


            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
