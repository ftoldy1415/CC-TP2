package com.company;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class Main {

    public static void main(String[] args) {

        byte[] ola = new byte[1];
        ola[0] = 29;

        int x = ola[0];

        System.out.println(x);

    }
}
