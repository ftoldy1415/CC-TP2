package com.company;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Demultiplexer implements AutoCloseable{
    private Lock l ;
    private DatagramSocket s;
    private DatagramSocket control;
    private Map<Integer, Condition> conds;
    private Map<Integer, Deque<DatagramPacket>> data;
    private Map<Integer,Boolean> timeoutRequests;
    private Map<Integer,Boolean> exceptions;
    private int timeout;
    private int port;
    private InetAddress ipEnviar;

    public Demultiplexer(DatagramSocket s, int port, InetAddress ipEnviar){
        this.l     = new ReentrantLock();
        this.s     = s;
        this.conds = new HashMap<>();
        this.data  = new HashMap<>();
        this.timeoutRequests = new HashMap<>();
        this.exceptions = new HashMap<>();
        this.port = port;
        this.ipEnviar = ipEnviar;
    }

    public void start(){
        Thread[] threads = {

                new Thread(() -> {
                   byte[] packetData;

                   while(true){
                       packetData = new byte[1024];
                       DatagramPacket packet = new DatagramPacket(packetData, packetData.length);

                       try{
                           s.setSoTimeout(checkTimeout());
                           s.receive(packet);

                           int tag;
                           tag = packetData[0];
                           if(this.ipEnviar.equals(packet.getAddress())){
                               if(tag != -1 ) {
                                   this.l.lock();
                                   if (!this.conds.containsKey(tag)) {
                                       this.conds.put(tag, this.l.newCondition());
                                       this.data.put(tag, new ArrayDeque<>());
                                       this.exceptions.put(tag, false);
                                       this.timeoutRequests.put(tag, false);
                                   }
                                   Condition c = this.conds.get(tag);
                                   Deque<DatagramPacket> deque = this.data.get(tag);
                                   deque.add(packet);
                                   this.data.put(tag, deque);
                                   c.signal();

                                   this.l.unlock();
                               }
                           }

                       }catch(SocketTimeoutException e){
                           this.l.lock();
                           Iterator<Map.Entry<Integer,Boolean>> it = this.timeoutRequests.entrySet().iterator();
                           int tag = -1;
                           while(it.hasNext() && tag == -1){
                               Map.Entry<Integer,Boolean> entry = it.next();
                               if(entry.getValue()) tag = entry.getKey();
                           }
                           this.timeoutRequests.put(tag,false);
                           this.exceptions.put(tag,true);
                           Condition c = this.conds.get(tag);
                           c.signal();
                           this.l.unlock();
                           try {
                               this.s.setSoTimeout(0);
                               this.timeout = 0;
                           } catch (SocketException socketException) {
                               socketException.printStackTrace();
                           }


                           System.out.println("exceção tag " + tag + " : " +e.getMessage());
                       }catch(IOException e){

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

    public DatagramPacket receive(int tag) throws IOException, InterruptedException, ReceiveTimeOut{
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
            if(this.exceptions.containsKey(tag) && this.exceptions.get(tag)) break;
        }
        //c.signal();
        if(this.exceptions.containsKey(tag) && this.exceptions.get(tag)){
            this.exceptions.put(tag,false);
            throw new ReceiveTimeOut("Receive timeout");
        }
        packet = this.data.get(tag).poll();

        this.l.unlock();
        return packet;
    }

    public void timeoutRequest(int tag, int timeOutTime){
        try {
            this.l.lock();
            this.s.setSoTimeout(timeOutTime);
            this.timeout = timeOutTime;
            if(timeOutTime != 0) this.timeoutRequests.put(tag,true);
            else this.timeoutRequests.put(tag,false);
            byte[] ctrl = new byte[1024];
            ctrl[0] = -1;
            /*
                Para desbloquear o a thread que está  ler do socket do receive envia-se um pacote de controlo utilizando o socket
                Este pacote nao vai apra a rede , vai apenas circular na maquina
                Desbloqueando a thread do receive é possivel fazer set do timeout
            */
            try {
                DatagramPacket ctrlP = new DatagramPacket(ctrl,ctrl.length, InetAddress.getByName("localhost"),this.port);
                this.s.send(ctrlP);
            } catch (IOException e) {
            e.printStackTrace();
        }
        this.l.unlock();
        }catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public int checkTimeout(){
        this.l.lock();
       Iterator<Map.Entry<Integer,Boolean>> it = this.timeoutRequests.entrySet().iterator();
       int timeout = 0;
       while(it.hasNext() && timeout == 0){
           Map.Entry<Integer,Boolean> e = it.next();
           if(e.getValue()) timeout = this.timeout;
       }
       this.l.unlock();
       return timeout;
    }

    public void close(){
        this.s.close();
    }

}
