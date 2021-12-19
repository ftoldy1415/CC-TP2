package com.company;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Map;

public class Demultiplexer implements AutoCloseable{
    private Lock l ;
    private DatagramSocket s;
    private Map<Integer, Condition> conds;
    private Map<Integer, Deque<DatagramPacket>> data;

    public Demultiplexer(DatagramSocket s){
        this.l     = new ReentrantLock();
        this.s     = s;
        this.conds = new HashMap<>();
        this.data  = new HashMap<>();
    }

    public void start(){
        Thread[] threads = {

                new Thread(() -> {
                   byte[] packetData;

                   while(true){
                       packetData = new byte[1024];
                       DatagramPacket packet = new DatagramPacket(packetData, packetData.length);

                       try{
                           s.receive(packet);
                           this.l.lock();
                           int tag;
                           tag = packetData[0];

                           if(!this.conds.containsKey(tag)){
                               this.conds.put(tag, this.l.newCondition());
                               this.data.put(tag, new ArrayDeque<>());
                           }
                           Condition c = this.conds.get(tag);
                           Deque<DatagramPacket> deque = this.data.get(tag);
                           deque.add(packet);
                           this.data.put(tag, deque);
                           c.signal();

                           this.l.unlock();
                       }catch(IOException e){
                           System.out.println(e.getMessage());
                       }

                   }

                })
        };
        threads[0].start();
    }

    public void send(DatagramPacket dp){
        try {
            s.send(dp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public DatagramPacket receive(int tag) throws IOException, InterruptedException{
        // verificar se existe algum tipo de dados associado à tag i
        //Caso existam dados , entao estes serao lidos e a função retorna , caso contrário , a thread será adormecida até que hajam dados para ler .
        this.l.lock();
        //ArrayDeque<DatagramPacket> queue = new ArrayDeque<>();
        DatagramPacket packet = null;
        Condition c = null;

        if(!this.conds.containsKey(tag)){
            c = this.l.newCondition();
            this.conds.put(tag,c);
            this.data.put(tag, new ArrayDeque<>());
        }

        while((data.get(tag).isEmpty())){
            c = this.conds.get(tag);
            c.await();
        }
        //c.signal();

        packet = this.data.get(tag).poll();

        this.l.unlock();
        return packet;
    }

    public void close(){
        this.s.close();
    }

}
