package com.company;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

public class HTTPClient {

    public static void main(String args[]){

        try{
            Socket socket = new Socket("localhost", 8080);

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream());

            BufferedReader systemIn = new BufferedReader(new InputStreamReader(System.in));

            String userInput;
            while ((userInput = systemIn.readLine()) != null) {
                System.out.println(userInput);
                out.println(userInput);
                out.flush();
                String output;
                while((output = in.readLine()) != null){
                    System.out.println(output);
                }

            }


            socket.shutdownOutput();
            socket.shutdownInput();
            socket.close();


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}