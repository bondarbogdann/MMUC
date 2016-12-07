package com.example.miditest;

/**
 * Created by bbondar on 07-Dec-16.
 */

public class Channel {

    private byte number;
    private int note;
    private boolean isPlaying;

    public Channel(byte number){
        this.number = number;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
    }

    public int getNote() {
        return note;
    }

    public void setNote(int note) {
        this.note = note;
    }

    public byte getNumber() {
        return number;
    }

    public void setNumber(byte number) {
        this.number = number;
    }
}
