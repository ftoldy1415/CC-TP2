package com.company;

import java.io.*;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Ficheiro {

    private int mss;
    private int packetDataSize;

    public Ficheiro(){
        this.mss = 1024;
        this.packetDataSize = this.mss-6;
    }



    public List<DatagramPacket> serializeMultipleFiles(List<String> fileList, String filepath){
        List<DatagramPacket> finalList = new ArrayList<>();
        for(String s : fileList){
            File f = new File(filepath+s);
            try {
                finalList.addAll(serializeFile(f));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return finalList;
    }


    private DatagramPacket closePacket(byte[] packetBuffer, short seq_number, short total, int packetSize){
        packetBuffer[packetSize] = 0;                                    // inserir bytes terminacao
        packetBuffer[packetSize+1] = 0;
        packetBuffer[packetSize+2] = (byte) (seq_number & 0xff);           // inserir número de sequencia no pacote
        packetBuffer[packetSize+3] = (byte) ((seq_number >> 8) & 0xff);
        packetBuffer[packetSize+4] = (byte) (total & 0xff);                // inserir número total de pacotes a serem enviados
        packetBuffer[packetSize+5] = (byte) ((total >> 8) & 0xff);

        return new DatagramPacket(packetBuffer.clone(), this.mss);
    }


    private DatagramPacket closeFinalPacket(byte[] packetBuffer, short seq_number, short total, int packetSize, short dataSize){
        packetBuffer[packetSize] = (byte) (dataSize & 0xff);             // inserir tamanho dos dados em vez do byte terminacao
        packetBuffer[packetSize+1] = (byte) ((dataSize >> 8) & 0xff);
        packetBuffer[packetSize+2] = (byte) (seq_number & 0xff);           // inserir número de sequencia no pacote
        packetBuffer[packetSize+3] = (byte) ((seq_number >> 8) & 0xff);
        packetBuffer[packetSize+4] = (byte) (total & 0xff);                // inserir número total de pacotes a serem enviados
        packetBuffer[packetSize+5] = (byte) ((total >> 8) & 0xff);

        return new DatagramPacket(packetBuffer.clone(), this.mss);
    }


    public List<DatagramPacket> serializeFile(File file) throws IOException {
        FileInputStream fl = new FileInputStream(file);
        int size = (int) file.length();
        byte[] file_name = file.getName().getBytes();
        System.out.println(file.getName());
        short name_size = (short) file_name.length;
        int packetSize = this.mss - 9 - name_size;       // mss - 1 - 2 - tamNome - 2 - 2 - 2

        int lastPacketSize = size % packetSize;
        System.out.println(lastPacketSize);

        int fullPackets = size/packetSize;
        System.out.println(fullPackets);

        List<DatagramPacket> lista = new ArrayList<>();
        byte[] arr = new byte[this.mss];
        int offset = 0;
        short seq_number = 1, total = (short) (fullPackets+1);
        DatagramPacket toSend;
        while (offset != fullPackets * packetSize && fl.readNBytes(arr, 3+file_name.length, packetSize) > 0){
            arr[0] = 2;
            arr[1] = (byte) (name_size & 0xff);
            arr[2] = (byte) ((name_size >> 8) & 0xff);
            System.arraycopy(file_name,  0, arr, 3,file_name.length);
            toSend = closePacket(arr, seq_number, total, 1 + 2 + file_name.length + packetSize);
            lista.add(toSend);
            System.out.println("Número de sequencia dentro da serialize" + seq_number);
            seq_number++;
            offset += packetSize;
        }
        //fechar o ultimo pacote
        byte[] lastArr = new byte[this.mss];
        fl.readNBytes(lastArr, 3+file_name.length, lastPacketSize);
        lastArr[0] = 2;
        lastArr[1] = (byte) (name_size & 0xff);
        lastArr[2] = (byte) ((name_size >> 8) & 0xff);
        System.arraycopy(file_name,  0, lastArr, 3,file_name.length);
        toSend = closeFinalPacket(lastArr, seq_number, total, 1 + 2 + file_name.length + packetSize, (short) lastPacketSize);
        lista.add(toSend);

        fl.close();

        return lista;
    }


    public Map<String, List<byte[]>> unpackAllData(Map<String, List<DatagramPacket>> listByName){
        Map<String, List<byte[]>> finalMap = new HashMap<>();
        for(Map.Entry<String, List<DatagramPacket>> e : listByName.entrySet()){
            finalMap.put(e.getKey(), unpackData(e.getValue()));
        }
        return finalMap;
    }


    public Map<String,List<DatagramPacket>> organizePacketsByName(List<DatagramPacket> l){
        Map<String,List<DatagramPacket>> res = new HashMap<>();
        short name_size;
        for (DatagramPacket p : l){
            byte[] data = p.getData();
            name_size = (short) (((data[2] & 0xFF) << 8) | (data[1] & 0xFF));
            byte[] filename = new byte[name_size];
            System.arraycopy(data, 3, filename, 0, name_size);

            String nameFinal = new String(filename);
            List<DatagramPacket> list;
            if(!res.containsKey(nameFinal)){
                list = new ArrayList<>();
            }
            else{
                list = res.get(nameFinal);
            }
            list.add(p);
            res.put(nameFinal, list);
        }
        return res;
    }


    public List<byte[]> unpackData(List<DatagramPacket> l){
        System.out.println("SIZE DA LISTA DE PACKETS" + l.size());
        int cap = l.size();
        List<byte[]> res = new ArrayList<>(3);
        Map<Integer, byte[]> aux = new HashMap<>();
        byte[] name = null;

        for(DatagramPacket pacote : l){
            byte[] dataP = pacote.getData();
            byte[] data = dataP.clone();
            short name_size = (short) (((data[2] & 0xFF) << 8) | (data[1] & 0xFF));

            int index_term = this.mss - 6;
            if(name == null){
                name = new byte[name_size];
                System.arraycopy(data, 3,name, 0, name_size);
                aux.put(0,name);
            }
            if(data[index_term+1] == 0 && data[index_term] == 0){
                byte[] fileData = new byte[this.mss - 9 - name_size];
                System.arraycopy(data, 3+name_size, fileData, 0, this.mss-9-name_size);
                short seqNum = (short) (((data[this.mss-3] & 0xFF) << 8) | (data[this.mss-4] & 0xFF));
                System.out.println("Número de sequencia " + seqNum + " | Tamanho do pacote: " + (this.mss - 9 - name_size));
                aux.put((int) seqNum,fileData);
            }
            else{
                short dataSize = (short) (((data[index_term+1] & 0xFF) << 8) | (data[index_term] & 0xFF));
                byte[] fileData = new byte[dataSize];
                System.arraycopy(data, 3+name_size, fileData, 0, dataSize);

                short seqNum = (short) (((data[this.mss-3] & 0xFF) << 8) | (data[this.mss-4] & 0xFF));
                System.out.println("Número de sequencia " + seqNum + " | Tamanho do pacote: " + dataSize);
                aux.put((int) seqNum,fileData);
            }

        }

        for(int i = 0; i < aux.size(); i++){
            if(aux.containsKey(i)) res.add(aux.get(i));
        }


        System.out.println("Tamanho res: " + res.size());
        return res;
    }


    public void createFiles(Map<String, List<byte[]>> m, String pasta){
        for (Map.Entry<String, List<byte[]>> e : m.entrySet()){
            deserializeAndCreateFile(e.getValue(), e.getKey(), pasta);
        }
    }


    public void deserializeAndCreateFile(List<byte[]> l, String name, String pasta){
        String filename = new String(l.get(0));
        System.out.println(filename);
        String filepath = pasta + filename;
        System.out.println(filepath);
        File f = new File(filepath);
        try {
            f.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            OutputStream os = new FileOutputStream(f);
            for (int i = 1; i < l.size() ; i++){
                byte[] arr = l.get(i);
                os.write(arr, 0, arr.length);
            }
            os.close();
        }
        catch (Exception e) {
            System.out.println("Exception: " + e);
        }
    }

    public long totalSize(List<String> fileList){
        long total = 0;
        for(String filename : fileList){
            try{
                Path path = Paths.get(filename);
                total += Files.size(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return total;
    }

}
