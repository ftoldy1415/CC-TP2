package com.company;

import java.io.*;
import java.net.DatagramPacket;
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


    /**
     * Metodo utilizado para colocar a informação final de um pacote, terminação 0 0 , número de sequência do pacote e total de pacotes.
     * @param packetBuffer
     * @param seq_number
     * @param total
     * @param packetSize
     * @return
     */
    private DatagramPacket closePacket(byte[] packetBuffer, short seq_number, short total, int packetSize){
        packetBuffer[packetSize] = 0;                                    // inserir bytes terminacao
        packetBuffer[packetSize+1] = 0;
        packetBuffer[packetSize+2] = (byte) (seq_number & 0xff);           // inserir número de sequencia no pacote
        packetBuffer[packetSize+3] = (byte) ((seq_number >> 8) & 0xff);
        packetBuffer[packetSize+4] = (byte) (total & 0xff);                // inserir número total de pacotes a serem enviados
        packetBuffer[packetSize+5] = (byte) ((total >> 8) & 0xff);

        return new DatagramPacket(packetBuffer.clone(), this.mss);
    }

    /**
     * Método que cria um DatagramPacket. Para tal, recebe como argumento o array de bytes de dados, colocando no fim, pela ordem mostrada,
     * o dataSize, o nº de sequência e o número de pacotes total, devolvendo o resultado no formato de DatagramPacket.
     * @param packetBuffer
     * @param seq_number
     * @param total
     * @param packetSize
     * @param dataSize
     * @return
     */
    private DatagramPacket closeFinalPacket(byte[] packetBuffer, short seq_number, short total, int packetSize, short dataSize){
        packetBuffer[packetSize] = (byte) (dataSize & 0xff);             // inserir tamanho dos dados em vez do byte terminacao
        packetBuffer[packetSize+1] = (byte) ((dataSize >> 8) & 0xff);
        packetBuffer[packetSize+2] = (byte) (seq_number & 0xff);           // inserir número de sequencia no pacote
        packetBuffer[packetSize+3] = (byte) ((seq_number >> 8) & 0xff);
        packetBuffer[packetSize+4] = (byte) (total & 0xff);                // inserir número total de pacotes a serem enviados
        packetBuffer[packetSize+5] = (byte) ((total >> 8) & 0xff);

        return new DatagramPacket(packetBuffer.clone(), this.mss);
    }

    /**
     * Método que serializa um ficheiro, transformando o conteúdo dos mesmos numa List de DatagramPackets.
     * Para tal, começa por calcular o nº de pacotes inteiros que vai produzir e o nº de bytes de dados ocupados no último pacote a ser criado.
     * De seguida é corrido um ciclo em que enquanto offset != fullpackets * packetSize, ou seja, enquanto não chegarmos à leitura do último
     * pacote, vamos lendo do FileInputStream. Quando esse offset atinge o índice do último pacote, iremos ler lastPacketSize bytes, que à partida
     * terá um tamanho menor do que os restantes pacotes
     * @param file Ficheiro a ler
     * @return list Lista de DatagramPackets que vai constutuir todos os dados do ficheiro em questão
     * @throws IOException
     */
    public List<DatagramPacket> serializeFile(File file) throws IOException {
        FileInputStream fl = new FileInputStream(file);
        int size = (int) file.length();
        byte[] file_name = file.getName().getBytes();
        System.out.println(file.getName());
        short name_size = (short) file_name.length;
        int packetSize = this.mss - 9 - name_size;       // mss - 1 - 2 - tamNome - 2 - 2 - 2

        int lastPacketSize = size % packetSize;

        int fullPackets = size/packetSize;

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

    /**
     * Metodo que transforma o Map que associa cada nome de ficheiro à lista de DatagramPackets num Map que associa o nome do ficheiro à lista arrays de bytes desempacotados
     * Para tal, o método executa um ciclo for que aplica a cada List contida no Map.Entry, o método unpackData, inserindo os dados num finalMap
     * @param listByName Map dos datagramPackets organizados por nome do ficheiro a que correspondem
     * @return
     */
    public Map<String, List<byte[]>> unpackAllData(Map<String, List<DatagramPacket>> listByName){
        Map<String, List<byte[]>> finalMap = new HashMap<>();
        for(Map.Entry<String, List<DatagramPacket>> e : listByName.entrySet()){
            finalMap.put(e.getKey(), unpackData(e.getValue()));
        }
        return finalMap;
    }

    /**
     * Método que organiza os DatagramPackets por nome de ficheiro a que estes correspondem.
     * É executado um ciclo for que percorre uma List de DatagramPackets, verificando o nome do ficheiro do pacote, armazenando o DatagramPacket num mapa,
     * associando dito DatagramPacket ao nome de ficheiro.
     * @param l List de DatagramPackets
     * @return Map resultante que associa um ficheiro à List de DatagramPackets que correspondem ao mesmo.
     */
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


    /**
     * Método que deserializa uma lista de pacotes de dados de ficheiros, transformando numa Lista de arrays de bytes.
     * É utilizado um mapa auxiliar onde a key é o nº de sequencia e o valor é um array de bytes
     * É executado um ciclo for que percorre todos os DatagramPacket da List. A primeira condição é para ter em conta o pacote que terá apenas o nome do ficheiro
     * armazenando-o na posição 0 do mapa. De seguida, é feita a deserialização de cada DatagramPacket restante, armazenando a informação no mapa auxiliar.
     * No fim do ciclo é feito um 2º ciclo que percorre o mapa recém criado, armazenando na lista res, pela ordem de nº de sequencia, cada array de bytes de dados retirados dos DatagramPackets
     * @param l List de DatagramPackets recebidos
     * @return List<byte[]> dos dados retirados dos DatagramPackets de forma ordenada por nº de sequência do pacote
     */
    public List<byte[]> unpackData(List<DatagramPacket> l){
        System.out.println("SIZE DA LISTA DE PACKETS" + l.size());
        int cap = l.size();
        List<byte[]> res = new ArrayList<>();
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
                aux.put((int) seqNum,fileData);
            }
            else{
                short dataSize = (short) (((data[index_term+1] & 0xFF) << 8) | (data[index_term] & 0xFF));
                byte[] fileData = new byte[dataSize];
                System.arraycopy(data, 3+name_size, fileData, 0, dataSize);

                short seqNum = (short) (((data[this.mss-3] & 0xFF) << 8) | (data[this.mss-4] & 0xFF));
                aux.put((int) seqNum,fileData);
            }

        }

        for(int i = 0; i < aux.size(); i++){
            if(aux.containsKey(i)) res.add(aux.get(i));
        }


        System.out.println("Tamanho res: " + res.size());
        return res;
    }


    /**
     * Método que invoca o método deserializeAndCreateFile para cada Map.Entry do Map passado como argumento.
     * @param m Map que associa cada nome de ficheiro à lista de arrays de bytes de dados correspondentes.
     * @param pasta diretoria
     */
    public void createFiles(Map<String, List<byte[]>> m, String pasta){
        for (Map.Entry<String, List<byte[]>> e : m.entrySet()){
            deserializeAndCreateFile(e.getValue(), e.getKey(), pasta);
        }
    }


    /**
     * Método que transforma uma lista de byte arrays que contêm os dados deserializados num ficheiro.
     * O método começa por criar o ficheiro com o nome que consta como primeiro elemento da lista passada como argumento.
     * De seguida é feito um ciclo que insere os vários arrays de bytes de forma ordenada no ficheiro.
     * @param l lista de arrays de bytes com os dados para o ficheiro a ser criado
     * @param name
     * @param pasta file path para o ficheiro a ser criado
     */
    public void deserializeAndCreateFile(List<byte[]> l, String name, String pasta){
        String filename = new String(l.get(0));
        String filepath = pasta + filename;
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

    /**
     * Método que calcula o tamanho total de varios ficheiros
     * @param fileList Lista dos nomes dos ficheiros cujo tamanho se pretende calcular
     * @return resultado do calculo sob o formato de long
     */
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
