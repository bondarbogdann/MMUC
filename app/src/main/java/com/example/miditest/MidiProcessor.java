package com.example.miditest;

import android.util.Log;

import org.billthefarmer.mididriver.MidiDriver;

import java.util.HashMap;

class MidiProcessor implements MidiDriver.OnMidiStartListener{
    private MidiDriver midiDriver;
    private byte[] event;
    private Channel pressure0Channel;
    private Channel pressure1Channel;

    private final int PRESSURETHRESHOLD = 50;

    MidiProcessor(){
        // Instantiate the driver.
        midiDriver = new MidiDriver();
        // Set the listener.
        midiDriver.setOnMidiStartListener(this);

        pressure0Channel = new Channel((byte) 0x00);
        pressure1Channel = new Channel((byte) 0x01);
    }

    void startDriver(){
        midiDriver.start();
    }

    void stopDriver(){
        midiDriver.stop();
    }

    @Override
    public void onMidiStart() {
        Log.d(this.getClass().getName(), "onMidiStart()");
    }

    private void playNote(int noteNumber, byte channel) {

        // Construct a note ON message for the note at maximum velocity on channel 1:
        event = new byte[3];
        event[0] = (byte) (0x90 | channel);  // 0x90 = note On, 0x00 = channel 1
        event[1] = (byte) noteNumber;
        event[2] = (byte) 0x7F;  // 0x7F = the maximum velocity (127)

        // Send the MIDI event to the synthesizer.
        midiDriver.write(event);

    }

    private void stopNote(int noteNumber, boolean sustainUpEvent, byte channel) {

        // Stop the note unless the sustain button is currently pressed. Or stop the note if the
        // sustain button was depressed and the note's button is not pressed.
        if (sustainUpEvent) {
            // Construct a note OFF message for the note at minimum velocity on channel 1:
            event = new byte[3];
            event[0] = (byte) (0x80 | channel);  // 0x80 = note Off, 0x00 = channel 1
            event[1] = (byte) noteNumber;
            event[2] = (byte) 0x00;  // 0x00 = the minimum velocity (0)

            // Send the MIDI event to the synthesizer.
            midiDriver.write(event);
        }
    }

    void selectInstrument(int instrument, byte channel) {

        // Construct a program change to select the instrument on channel 1:
        event = new byte[2];
        event[0] = (byte)(0xC0 | channel); // 0xC0 = program change, 0x00 = channel 1
        event[1] = (byte)instrument;

        // Send the MIDI event to the synthesizer.
        midiDriver.write(event);
    }

    void play(int pressure0, int pressure1, int yaw, int pitch, int roll) {
        if (pressure0 > PRESSURETHRESHOLD && !pressure0Channel.isPlaying()) {
            pressure0Channel.setNote(pitch);
            playNote(pressure0Channel.getNote(), pressure0Channel.getNumber());
            pressure0Channel.setPlaying(true);
        }
        if (pressure0 < PRESSURETHRESHOLD && pressure0Channel.isPlaying()) {
            stopNote(pressure0Channel.getNote(), true, pressure0Channel.getNumber());
            pressure0Channel.setPlaying(false);
        }
        if (pressure1 > PRESSURETHRESHOLD && !pressure1Channel.isPlaying()) {
            pressure1Channel.setNote(pitch + 10);
            playNote(pressure1Channel.getNote(), pressure1Channel.getNumber());
            pressure1Channel.setPlaying(true);
        } else if (pressure1 > PRESSURETHRESHOLD && pressure1Channel.isPlaying() && pressure1Channel.getNote() != pitch + 10) {
            stopNote(pressure1Channel.getNote(), true, pressure1Channel.getNumber());
            pressure1Channel.setNote(pitch + 10);
            playNote(pressure1Channel.getNote(), pressure1Channel.getNumber());
        } else if (pressure1 < PRESSURETHRESHOLD && pressure1Channel.isPlaying()) {
            stopNote(pressure1Channel.getNote(), true, pressure1Channel.getNumber());
            pressure1Channel.setPlaying(false);
        }
        //TODO: accelerometer
    }
}
