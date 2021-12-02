
package com.company;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;

public class Servidor {


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



    public static void main(String[] args) {

        Path file = Path.of("/home/ftoldy/Área de Trabalho/prolog/ficha1.pl");

        BasicFileAttributes attr = null;
        try {
            attr = Files.readAttributes(file, BasicFileAttributes.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            ServerSocket ss = new ServerSocket(12345);

            while (true){
                Socket socket = ss.accept();

                DataInputStream in = new DataInputStream(socket.getInputStream());
                PrintWriter out = new PrintWriter(socket.getOutputStream());



                boolean c = compare(attr,in);

                System.out.println(c);

                out.println(c);

                socket.shutdownOutput();
                socket.shutdownInput();
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}