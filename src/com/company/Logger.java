package com.company;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.*;

public class Logger {
    private File file;
    private List<Log> logs;

    public void Logger (){
        File file = new File("logs.txt");
        List<Log> logs = new ArrayList<Log>();
    }

    public void writeLog(Log newLog){
        logs.add(newLog.clone());
    }

    public void publishLogs() throws IOException {
        BufferedWriter writer;
        writer = new BufferedWriter(new FileWriter(this.file));
        for(Log l : this.logs){
            writer.append(l.toString() + "\n");
        }
        writer.close();
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
}