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

    public Logger (){
        this.file = new File("logs.html");
        this.logs = new ArrayList<>();
    }

    public void writeLog(Log newLog){
        System.out.println(newLog);
        this.logs.add(newLog.clone());

    }

    public void publishLogs() throws IOException {
        BufferedWriter writer;
        writer = new BufferedWriter(new FileWriter(this.file));
        writer.append("<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>Example</title>\n" +
                "</head>\n" +
                "<body> \n");
        for(Log l : this.logs){
            writer.append("<p>" + l.toString() + "</p>" +"\n");
        }
        writer.append("</body>\n" +
                "</html>\n");
        writer.close();
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
}