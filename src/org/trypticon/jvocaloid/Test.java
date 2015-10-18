package org.trypticon.jvocaloid;

public class Test {
    public static void main(String[] args) throws Exception {
        VocaloidEngine engine = new JVstHostVocaloid2Engine();
        engine.start();

        engine.queueControlChangeNoteMessage(0, 0, 0);
        engine.delay(0x2D3B);
        engine.queueNoteMessage(3920, 0x45, 0x40, 569, 0x3, new int[][]{{'h', 0x40}, {'a', 0x00}});
        engine.delay(0x268);
        engine.queueNoteMessage(3920, 0x44, 0x40, 0x0433, 0x3, new int[][]{{'4', 0x40}, {'o', 0x00}});
        engine.delay(0x135);
        engine.queueControlChangeVibratoDepth(0x0F50, 0x40, 0x32);

        engine.stop();
    }
}
