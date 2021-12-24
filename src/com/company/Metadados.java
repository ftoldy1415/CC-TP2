package com.company;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Metadados {
    private int mss;
    private int metaSize;
    private int totalPackets;
    //0 -> comunicação inicial
    //1 -> pacotes de metadados
    //2 -> pacotes de ficheiros


    public Metadados(){
        this.mss = 1024;
        this.metaSize = this.mss-6;
    }


    /**
     * Método de serialização dos pacotes com os ficheiros a pedir.
     * Começa por fazer uma verificação do tamanho total dos nomes de todos os ficheiros que se pretende requisitar.
     * Calcula-se o nº total de pacotes (dividindo o tamanho total dos nomes pelo espaço disponível em cada pacote para armazenamento dos mesmos),
     * É inicializado um array de bytes com tamanho 1024 e é iniciado um ciclo que percorre todos as strings na lista l (lista de nomes dos ficheiros a pedir).
     * Nesse ciclo, é feito sucessivamente a inserção do tamanho do nome (no formato short) seguido do nome do ficheiro, incrementando o indice adequadamente.
     * Quando dito indice atingir um valor não inferior a mss-6, é chamado o método fechaPacote e adicionado à lista o pacote recém criado.
     * É ainda inicializado um novo array para as iterações seguintes.
     * @param l
     * @return
     */
    public List<DatagramPacket> serializeAsk(List<String> l){
        List<DatagramPacket> finalList = new ArrayList<>();
        int sizetotal = 0;
        for (String s : l){
            sizetotal += s.getBytes().length ; //tamanho do nome
            sizetotal += 2;                    //short indicativo do tamanho do nome
        }
        int numPackets = (sizetotal/1019) + 1;

        int index = 1;
        int seq_num = 0;
        byte[] data = new byte[1024];
        data[0] = 1; //tag 1
        for(String s : l){
            byte[] name = s.getBytes();
            short size_name = (short) name.length;
            if(index < this.mss-6){
                data[index++] = (byte) (size_name & 0xff);
                data[index++] = (byte) ((size_name >> 8) & 0xff);
                System.arraycopy(name, 0, data, index, size_name);
                index += size_name;
            }
            else{
                fechaPacote(data, index, seq_num);
                index += 4;
                finalList.add(criaPacote(new AbstractMap.SimpleEntry<>(index, data), seq_num+1));
                seq_num++;
                index = 1;
                data = new byte[1024];
                data[0] = 1;
                data[index++] = (byte) (size_name & 0xff);
                data[index++] = (byte) ((size_name >> 8) & 0xff);
                System.arraycopy(name, 0, data, index, size_name);
                index += size_name;
            }
        }
        if (seq_num < numPackets){
            fechaPacote(data, index, seq_num);
            index += 4;
            finalList.add(criaPacote(new AbstractMap.SimpleEntry<>(index, data), seq_num+1));
        }
        return finalList;
    }

    /**
     * Método de deserialização dos pacotes de pedido de ficheiros.
     * O método recebe uma lista de DatagramPackets que irá deserializar num ciclo for.
     * Cada iteração desse ciclo irá iniciar um ciclo while com o propósito de ler cada nome de ficheiro para adicionar a uma List<String>
     * Cada ciclo while será terminado quando o indice de leitura se encontrar nos bytes de terminação do pacote
     * @param filesToAskPacket
     * @return
     */
    public List<String> deserializeAsk(List<DatagramPacket> filesToAskPacket){
        List<String> filesList = new ArrayList<>();
        byte[] data, nameBytes;
        String name;
        int index ;
        short name_size;
        for(DatagramPacket p : filesToAskPacket){
            data = p.getData();
            index = 1;
            while(data[index] != 0 && data[index+1] != 0 ) {
                name_size = (short) (((data[index + 1] & 0xFF) << 8) | (data[index] & 0xFF));
                nameBytes = new byte[name_size];
                index += 2;
                System.arraycopy(data, index, nameBytes, 0, name_size);
                name = new String(nameBytes);
                filesList.add(name);
            }

        }
        return filesList;
    }


    /**
     * Método que serializa os metadados de 1 ficheiro. Os primeiros 2 bytes do array representam o tamanho do nome do ficheiro
     * depois é inserido o nome do ficheiro convertido em bytes e por fim o lastmodified time convertido para segundos no formato long
     * @param attr file atributes do ficheiro em questão
     * @param name nome do ficheiro
     * @return
     */
    public byte[] serializeMetadadosFicheiro(BasicFileAttributes attr,String name){
        long lastmodified = attr.lastModifiedTime().to(TimeUnit.SECONDS);
        byte [] nome = name.getBytes();
        short name_size = (short) nome.length;
        byte[] b = new byte[name_size+10];
        System.arraycopy(nome,0,b,2,nome.length);
        b[0] = (byte) (name_size & 0xff);
        b[1] = (byte) ((name_size >> 8) & 0xff);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(lastmodified);
        byte[] longBuffer = buffer.array();
        System.arraycopy(longBuffer,0,b,nome.length+2,8);
        return b;
    }

    /**
     * Método que faz a deserialização dos metadados de 1 ficheiro. Começa por tirar o tamanho do nome, alocando um array com o tamanho certo.
     * Converte o array de bytes do nome para uma string. Converte o long para lastmodified time.
     * @param b array de bytes
     * @param index posição em que é iniciada a leitura
     * @return Map entry com o tamanho total dos metadados como key e um map entry com o nome e filetime como valor.
     */
    public Map.Entry<Integer, Map.Entry<String, FileTime>> deserializeMetadadosFicheiro(byte[] b, int index){

        short name_size = (short)(((b[index+1] & 0xFF) << 8) | (b[index] & 0xFF));
        byte[] nomeFile = new byte[name_size];
        System.arraycopy(b,index+2,nomeFile,0,name_size);
        String name = new String(nomeFile);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        byte[] bufferBytes = new byte[8];
        System.arraycopy(b,index + 2 + name_size ,bufferBytes,0,8);
        buffer.put(bufferBytes);
        buffer.flip(); //need flip
        long lastModifiedLong = buffer.getLong();
        FileTime lastModifiedTime = FileTime.from(lastModifiedLong,TimeUnit.SECONDS);
        index += 2 + name_size + 8;

        return new AbstractMap.SimpleEntry<>(index, new AbstractMap.SimpleEntry<>(name, lastModifiedTime));

    }

    /**
     * serializa o pacote que contem apenas o nome da pasta a que os ficheiros a sincronizar pertencem (para posterior comparação do lado do receptor)
     * O array pastaData tem como primeiro byte um byte de "código" que sinaliza que este datagrampacket irá conter metadados.
     * Para assinalar que o datagrampacket terá apenas uma pasta, o long que sucederia o nome do ficheiro é colocado a 0.
     * @param s
     * @param pasta
     * @return
     */
    private DatagramPacket serializePastaData (String s, String pasta, short totalPackets){

        byte [] pastaB = pasta.getBytes();             //byte array com o nome da pasta
        byte [] b = new byte[2];                       //cabeçalho para guardar o tamanho do nome da pasta
        short size_name_pasta = (short) pastaB.length;
        b[0] = (byte) (size_name_pasta & 0xff);
        b[1] = (byte) ((size_name_pasta >> 8) & 0xff);

        byte[] pastaData = new byte[mss];
        pastaData[0] = 1;

        System.arraycopy(b, 0, pastaData, 1, b.length);
        System.arraycopy(pastaB, 0, pastaData, 3, pastaB.length);


        long id = 0;
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(id);
        byte[] longBuffer = buffer.array();
        System.arraycopy(longBuffer, 0, pastaData, 1+pastaB.length + b.length, longBuffer.length);

        byte [] total = new byte[2];
        total[0] = (byte) (totalPackets & 0xff);
        total[1] = (byte) ((totalPackets >> 8) & 0xff);

        System.arraycopy(total, 0, pastaData, 1+pastaB.length+b.length+8, total.length);

        return new DatagramPacket(pastaData,mss);
    }

    /**
     * Método que fecha os arrays de bytes com terminação 00 mais o nº de sequência
     * @param packetBuffer buffer a encerrar
     * @param size_final tamanho final provisório
     * @param seq_number numero de sequencia
     * @return
     */
    private byte[] fechaPacote(byte[] packetBuffer, int size_final, int seq_number){
        packetBuffer[size_final++] = 0;
        packetBuffer[size_final++] = 0;
        packetBuffer[size_final++] = (byte) (seq_number & 0xff);
        packetBuffer[size_final] = (byte) ((seq_number >> 8) & 0xff);
        return packetBuffer.clone();
    }

    /**
     * Cria o DatagramPacket que tem como conteudo o byte array seguido do numero total de pacotes
     * @param e
     * @param total nº total de pacotes
     * @return  DatagramPacket finalizado
     */
    private DatagramPacket criaPacote(Map.Entry<Integer, byte[]> e, int total){
        byte [] toAdd = e.getValue();
        int indexA = e.getKey();
        toAdd[indexA++] = (byte) (total & 0xff);
        toAdd[indexA] = (byte) ((total >> 8) & 0xff);
        return new DatagramPacket(toAdd, toAdd.length);
    }


    public List<DatagramPacket> serializeDataToPacket(String path) throws IOException {
        ArrayList<DatagramPacket> resultSerialized = new ArrayList<>();
        ArrayList<Map.Entry<Integer,byte[]>> provisional = new ArrayList<>();

        File f = new File(path);
        int size_final;
        short seq_number = 0;
        int resultSerializedIndex = 0;



        byte[] packetBuffer = new byte[mss], packetBufferData;
        packetBuffer[0] = 1;
        size_final = 1;
        for(String x : Objects.requireNonNull(f.list())){
            BasicFileAttributes attr = Files.readAttributes(Path.of(path +x), BasicFileAttributes.class);
            byte[] serializado = serializeMetadadosFicheiro(attr,x);

            if (serializado.length < metaSize-size_final){
                System.arraycopy(serializado, 0, packetBuffer, size_final, serializado.length);

                size_final += serializado.length;
            }
            else {                                  //flag final de pacote
                packetBufferData = fechaPacote(packetBuffer, size_final, seq_number++);
                provisional.add(new AbstractMap.SimpleEntry<>(size_final+4, packetBufferData));
                size_final = 1; // quando pacote fecha, temos que voltar ao inicio do proximo pacote.

            }
        }

        packetBufferData = fechaPacote(packetBuffer, size_final, seq_number);
        provisional.add(new AbstractMap.SimpleEntry<>(size_final+4, packetBufferData));

        for (Map.Entry<Integer, byte[]> e : provisional){              //cria os datagramPackets com o tamanho o nº de sequencia nos 2 bytes depois da informaçao a passar
            DatagramPacket packetToAdd = criaPacote(e, seq_number+1);    //seq number vai ser o neste momento o número total de pacotes a enviar. Este nº vai ser colocado em todos os pacotes
            resultSerialized.add(packetToAdd);
        }
        return resultSerialized;
    }


    /**
     * Deserializa um DatagramPacket relativo a metadados
     * @param dados
     * @return um map entry que contem o nº de sequencia e a lista de metadados contidos no datagramPacket deserializado
     */

    public Map.Entry<Integer, List<Map.Entry<String, FileTime>>> deserializeFileMeta(DatagramPacket dados) {

        byte[] dadosBytes = dados.getData();

        short seq_number, total;
        List<Map.Entry<String, FileTime>> listaMeta = null;
        int index = 1;

        FileTime file_time, compare; //compare é criado para fazer a verificação do long que representa FileTime. Usado para a verificação do primeiro elemento (pasta ou ficheiro)
        long compare_long = 0;
        compare = FileTime.from(compare_long, TimeUnit.SECONDS);
        int i = 0;
        listaMeta = new ArrayList<>();
        while (index < dadosBytes.length) {
            i++;
            if(dadosBytes[index] == 0 && dadosBytes[index+1] == 0) {
                index += 2;
                seq_number = (short) (((dadosBytes[index+1] & 0xFF) << 8) | (dadosBytes[index] & 0xFF));
                index += 2;
                this.totalPackets = (short) (((dadosBytes[index+1] & 0xFF) << 8) | (dadosBytes[index] & 0xFF));
                return new AbstractMap.SimpleEntry<>((int) seq_number, listaMeta);
            }
            else{
                Map.Entry<Integer, Map.Entry<String, FileTime>> ret = deserializeMetadadosFicheiro(dadosBytes, index);
                file_time = ret.getValue().getValue();
                if(file_time.compareTo(compare) == 0){
                    listaMeta.add(new AbstractMap.SimpleEntry<>(ret.getValue().getKey(), ret.getValue().getValue()));
                    this.totalPackets = (short) (((dadosBytes[ret.getKey()+1] & 0xFF) << 8) | (dadosBytes[ret.getKey()] & 0xFF));
                    return new AbstractMap.SimpleEntry<>(0, listaMeta);
                }
                else{
                    listaMeta.add(new AbstractMap.SimpleEntry<>(ret.getValue().getKey(), ret.getValue().getValue()));
                }
                index = ret.getKey();
            }
        }
        return null;
    }

    /**
     * Método que desserializa uma lista de DatagramPackets
     * @param lp Lista dos pacotes para desserializar
     * @return listaFinal -> uma lista com os pares (Nome de ficheiro, Metadados respetivos)
     */

    public List<Map.Entry<String, FileTime>> deserializePackets(List<DatagramPacket> lp){
        List<Map.Entry<String, FileTime>> listaFinal = new ArrayList<>();
        Map.Entry<Integer, List<Map.Entry<String, FileTime>>> entry;
        int i = 0;
        for (DatagramPacket dp : lp){
            entry = deserializeFileMeta(lp.get(i++));
            listaFinal.addAll(entry.getValue());
        }
        return listaFinal;
    }

    /**
     * Método de comparação de metadados
     * @param pasta String com o nome da pasta da comparação
     * @param metaFile Lista com os metadados todos da pasta
     * @return Um par do tipo (lista de ficheiros a pedir, lista de ficheiros a enviar)
     */

    public Map.Entry<List<String>, List<String>> compare(String pasta , List<Map.Entry<String, FileTime>> metaFile) throws IOException {
        String filepath = pasta;
        ArrayList<String> filesToAsk  = new ArrayList<>();
        ArrayList<String> filesToSend = new ArrayList<>();
        for (Map.Entry<String,FileTime> e : metaFile){
            try {
                File f = new File(filepath+e.getKey());
                if (e.getValue().to(TimeUnit.SECONDS) == 0);
                else{
                    BasicFileAttributes attr = Files.readAttributes(Path.of(filepath + e.getKey()), BasicFileAttributes.class);
                    if (attr.lastModifiedTime().compareTo(e.getValue()) < 0){
                        filesToAsk.add(e.getKey());
                    }
                    else if(attr.lastModifiedTime().compareTo(e.getValue()) > 0){

                        filesToSend.add(e.getKey());
                    }
                }
            } catch(IOException exception){
                filesToAsk.add(e.getKey());
            }
        }
        String directory = pasta;
        File f = new File(directory);
        List<String> nameMetaFile = metaFile.stream().map(Map.Entry::getKey).collect(Collectors.toList());
        for(String x : Objects.requireNonNull(f.list())){
            if(!nameMetaFile.contains(x)){
                filesToSend.add(x);
            }
        }


        return new AbstractMap.SimpleEntry<>(filesToAsk, filesToSend);
    }

    public int getTotalPackets(){
        return this.totalPackets;
    }



}
