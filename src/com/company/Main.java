package com.company;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.nio.file.attribute.FileTime.from;

public class Main {



    public static void main(String[] args) {


        //Path file = Path.of("/home/ftoldy/Área de Trabalho/prolog/ficha1.pl");
        File f = new File("/home/ftoldy/Área de Trabalho/prolog/");
        /*
        BasicFileAttributes attr = null;

        try {
            attr = Files.readAttributes(file, BasicFileAttributes.class);
            System.out.println(attr.lastModifiedTime().toString());
            byte[] metaDataS = serializeMeta(attr,"ficha1.pl");
            deserializeMeta(metaDataS);

        } catch (IOException e) {
            e.printStackTrace();
        }

         */
        for (String s : Objects.requireNonNull(f.list())){
            System.out.println(s);
        }
    }
}