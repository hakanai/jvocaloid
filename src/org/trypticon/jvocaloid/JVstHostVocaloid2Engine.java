package org.trypticon.jvocaloid;

import com.synthbot.audioio.vst.JVstAudioThread;
import com.synthbot.audioplugin.vst.JVstLoadException;
import com.synthbot.audioplugin.vst.vst2.JVstHost2;
import com.synthbot.audioplugin.vst.vst2.JVstHostListener;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Engine for accessing VOCALOID 2 via JVstHost.
 */
public class JVstHostVocaloid2Engine implements VocaloidEngine {

    // Audio output parameters.
    private static final float SAMPLES_PER_SECOND = 44100;
    private static final int BLOCK_SIZE = 4096;

    // Channel to emit on. As far as I know, VOCALOID only supports channel 1, which is 0 here.
    private static final int CHANNEL = 0;

    // TODO: These should be provided at the caller, but at the moment we're just testing.
    private int microsecondsPerQuarterNote = 500000;
    private long nanosecondsPerDeltaTime = microsecondsPerQuarterNote * 1000000L / 480;

    /**
     * VST host access.
     */
    private final JVstHost2 vst;

    /**
     * Reusable message object to cut down on object allocation.
     */
    private final ShortMessage message = new ShortMessage();

    /**
     * Thread runner which takes care of calling process methods and emitting audio to the sound device.
     */
    private final JVstAudioThread audioThreadRunner;

    /**
     * The audio thread.
     */
    private volatile Thread audioThread;

    /**
     * Constructs the engine.
     *
     * @throws RuntimeException if an error occurs loading the engine.
     */
    public JVstHostVocaloid2Engine() {
        //TODO: Look up the VST using the Windows registry via JNA.
        //TODO: Support for non-Windows should be possible too.
        File vstDll = new File("C:\\Program Files (x86)\\Steinberg\\VSTplugins\\VOCALOID2\\Vocaloid2.dll");
        try {
            vst = JVstHost2.newInstance(vstDll, SAMPLES_PER_SECOND, BLOCK_SIZE);
        } catch (JVstLoadException | FileNotFoundException e) {
            throw new RuntimeException("Could not initialise VST host", e);
        }

        vst.addJVstHostListener(new MyJVstHostListener());

        audioThreadRunner = new JVstAudioThread(vst);
    }

    @Override
    public void start() {
        if (audioThread != null) {
            throw new IllegalStateException("Already started");
        }

        audioThread = new Thread(audioThreadRunner);
        audioThread.setName("Audio Thread");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    @Override
    public void stop() {
        if (audioThread != null) {
            audioThread.interrupt();

            try {
                audioThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            audioThread = null;
        }
    }

    @Override
    public void delay(int timeOffset) {
        try {
            long nanos = timeOffset * nanosecondsPerDeltaTime;
            long millis = nanos / 1000000;
            int remainingNanos = (int) (nanos % 1000000);
            Thread.sleep(millis, remainingNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }

    @Override
    public void queueControlChangeNoteMessage(int delayMillis, int languageType, int voiceType) {
        queueNrpn(0x6000, 0, 0); // version number, device number
        queueNrpn(0x6001, delayMillis/128, delayMillis%128);
        queueNrpn(0x6002, languageType);
        queueNrpn(0x5302, voiceType);
    }

    @Override
    public void queueNoteMessage(int delayMillis, int noteNumber, int velocity, int noteDuration, int noteLocation, int[][] phonetics) {
        queueNrpn(0x5000, 0, 0); // version number, device number
        queueNrpn(0x5001, delayMillis/128, delayMillis%128);
        queueNrpn(0x5002, noteNumber);
        queueNrpn(0x5003, velocity);
        queueNrpn(0x5004, noteDuration/128, noteDuration%128);
        queueNrpn(0x5005, noteLocation);

        //TODO: Vibrato
        //queueNrpn(0x500C, 0x00); // Index of vibrato database
        //queueNrpn(0x500D, 0x01, 0x55); // Index of vibrato type, Duration & Continuation parameter?
        //queueNrpn(0x500E, 0x2A); // Vibrato delay

        queueNrpn(0x5012, phonetics.length); // Number of phonetic symbols (2)
        int control = 0x5012;
        for (int[] symbolAndConsonantAdjustment : phonetics) {
            control++;
            queueNrpn(control, symbolAndConsonantAdjustment[0], symbolAndConsonantAdjustment[1]);
        }
        queueNrpn(0x504F, 0x7F); // End of phonetic symbols

        // Optional parameters where I took the values from the quick test file I made.
        queueNrpn(0x5050, 0x04); // v1mean in Cent/5
        queueNrpn(0x5051, 0x08); // d1mean in ms/5
        queueNrpn(0x5052, 0x14); // d1meanFirstNote in ms/5
        queueNrpn(0x5053, 0x1C); // d2mean in ms/5
        queueNrpn(0x5054, 0x18); // d4mean in ms/5
        queueNrpn(0x5055, 0x0A); // pMeanOnsetFirstNote in Cent/5
        queueNrpn(0x5056, 0x0C); // vMeanNoteTransition in Cent/5
        queueNrpn(0x5057, 0x0C); // pMeanEndingNote in Cent/5
        queueNrpn(0x5058, 0x00); // AddScoopToUpIntervals/AddPortamentoToDownIntervals (False/False)
        queueNrpn(0x5059, 0x32); // changeAfterPeak (0.25)
        queueNrpn(0x505A, 0x32); // Accent (0.0)

        queueNrpn(0x507F, 0x7F); // End of Note Message
    }

    @Override
    public void queueControlChangeVibratoDepth(int delayMillis, int depth, int rate) {
        queueNrpn(0x6500, 0, 0); // version number, device number
        queueNrpn(0x6501, delayMillis/128, delayMillis%128);
        queueNrpn(0x6502, depth);
        queueNrpn(0x6402, rate);
    }

    /**
     * Queues an NRPN (Non-Registered Parameter Number) message.
     * This version of the method is a shortcut when only one byte of data is required.
     *
     * @param code the code, a short value which is transmitted as two bytes.
     * @param dataMsb the most significant byte of the data to send with it.
     */
    private void queueNrpn(int code, int dataMsb) {
        queueNrpn(code, dataMsb, -1);
    }

    /**
     * Queues an NRPN (Non-Registered Parameter Number) message.
     *
     * @param code the code, a short value which is transmitted as two bytes.
     * @param dataMsb the most significant byte of the data to send with it.
     * @param dataLsb the least significant byte of the data to send with it.
     */
    private void queueNrpn(int code, int dataMsb, int dataLsb) {
        queueControlChange(99, code/256);
        queueControlChange(98, code%256);
        queueControlChange(6, dataMsb);
        if (dataLsb >= 0) {
            queueControlChange(38, dataLsb);
        }
    }

    /**
     * Queues a Control Change message.
     *
     * @param data1 the first byte of data to send.
     * @param data2 the second byte of data to send.
     */
    private void queueControlChange(int data1, int data2) {
        try {
            message.setMessage(ShortMessage.CONTROL_CHANGE, CHANNEL, data1, data2);
        } catch (InvalidMidiDataException e) {
            throw new IllegalArgumentException("Invalid MIDI data", e);
        }
        vst.queueMidiMessage(message);
    }

    /**
     * Listener for events. As far as I can tell, this never gets called.
     */
    private static class MyJVstHostListener implements JVstHostListener {
        @Override
        public void onAudioMasterAutomate(JVstHost2 jVstHost2, int i, float v) {
            System.out.println("onAudioMasterAutomate");
        }

        @Override
        public void onAudioMasterProcessMidiEvents(JVstHost2 jVstHost2, ShortMessage shortMessage) {
            System.out.println("onAudioMasterProcessMidiEvents");
        }

        @Override
        public void onAudioMasterIoChanged(JVstHost2 jVstHost2, int i, int i1, int i2, int i3) {
            System.out.println("onAudioMasterIoChanged");
        }

        @Override
        public void onAudioMasterBeginEdit(JVstHost2 jVstHost2, int i) {
            System.out.println("onAudioMasterBeginEdit");
        }

        @Override
        public void onAudioMasterEndEdit(JVstHost2 jVstHost2, int i) {
            System.out.println("onAudioMasterEndEdit");
        }
    }
}
