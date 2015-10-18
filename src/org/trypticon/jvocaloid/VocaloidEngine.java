package org.trypticon.jvocaloid;

/**
 * Abstraction for talking to a VOCALOID engine.
 *
 * This abstracts the fact that different systems use completely different MIDI mechanisms to change settings.
 * For instance, VOCALOID 2 on PC uses NRPN values to perform pretty much all actions, including playing notes,
 * whereas Pocket Miku uses normal NOTE_ON and NOTE_OFF events along with SysEx messages to set parameters.
 */
public interface VocaloidEngine {

    /**
     * Starts the engine.
     */
    void start();

    /**
     * Stops the engine.
     */
    void stop();

    /**
     * Delays for a given amount of time.
     *
     * @param timeOffset the amount of time to delay as a MIDI time offset value.
     */
    void delay(int timeOffset);

    /**
     * Queues a Control Change Note Message.
     *
     * @param delayMillis the delay value.
     * @param languageType the language type.
     * @param voiceType the voice type.
     */
    void queueControlChangeNoteMessage(int delayMillis, int languageType, int voiceType);

    /**
     * Queues a note message.
     *
     * @param delayMillis the delay value.
     * @param noteNumber the note number. TODO: Make this an enum or similar.
     * @param velocity the velocity to hit the note at, 0 ~ 127.
     * @param noteDuration the duration of the note.
     * @param noteLocation the location in the note.
     * @param phonetics an array of [phoneme,consonant_adjust] values.  TODO: Make this a better structure.
     */
    void queueNoteMessage(int delayMillis, int noteNumber, int velocity, int noteDuration, int noteLocation, int[][] phonetics);

    /**
     * Queues a Control Change Vibrato Depth message.
     *
     * @param delayMillis the delay value.
     * @param depth the new vibrato depth.
     * @param rate the new vibrato rate.
     */
    void queueControlChangeVibratoDepth(int delayMillis, int depth, int rate);
}
