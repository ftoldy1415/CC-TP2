package com.company;
import java.io.File;
import java.util.ArrayList;
import java.util.*;

public class Logger {
    private File file;
    private List<String> logs;

    public void Logger (){
        File file = new File("logs.txt");
        List<String> logs = new ArrayList<String>();
    }

    public void writeLog(String newLog){
        logs.add(newLog);
    }

    public void publishLogs() throws IOException {
        BufferedWriter writer = new BufferedWriter(this.file);
        for(String s : this.logs){
            writer.append(s);
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