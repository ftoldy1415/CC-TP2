package com.company;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;


public class Cliente implements Runnable{
    private InetAddress ipEnviar;
    private int port;

    public void Cliente(InetAddress ip, int port){
        this.ipEnviar = ip;
        this.port     = port;
    }


    public static void serialize(DataOutputStream out, BasicFileAttributes attr) throws IOException {
        out.writeUTF(String.valueOf(attr.creationTime()));
        out.writeUTF(attr.lastModifiedTime().toString());
        out.writeInt(Integer.parseInt(String.valueOf(attr.size())));
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
