package com.company;

import java.time.LocalDateTime;

public class Log {
    private String message;
    private LocalDateTime time;

    public Log(String m , LocalDateTime t){
        this.message = m;
        this.time = t;
    }

    public Log(Log log){
        this.message = log.getMessage();
        this.time = log.getTime();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public String toString(){
        return ("TIME : {" + this.time.toString() + "}" + "   AÇÃO : " + message);
    }

    public Log clone(){
        return new Log(this);
    }
}