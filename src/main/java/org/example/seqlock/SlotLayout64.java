package org.example.seqlock;

public final class SlotLayout64 {
    public static final int STRIDE = 64;

    public static final int INT_0 = 0;
    public static final int INT_1 = 4;
    public static final int INT_2 = 8;
    public static final int INT_3 = 12;
    public static final int INT_4 = 16;
    public static final int INT_5 = 20;
    public static final int INT_6 = 24;
    public static final int INT_7 = 28;
    public static final int INT_8 = 32;
    public static final int INT_9 = 36;
    public static final int INT_10 = 40;
    public static final int INT_11 = 44;
    public static final int LONG_0 = 48;
    public static final int SEQ = 56;
    public static final int LAST_UPDATE_TYPE = 60;

    private SlotLayout64() {
    }
}
