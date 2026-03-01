package org.example.seqlock;

public final class HeapByteNaiveTable extends AbstractHeapByteTable {
    private final byte[] backing;

    public HeapByteNaiveTable(int capacity) {
        this(capacity, new byte[Math.multiplyExact(capacity, SlotLayout64.STRIDE)]);
    }

    private HeapByteNaiveTable(int capacity, byte[] backing) {
        super(capacity, backing, 0);
        this.backing = backing;
    }

    public int startOffset() {
        return 0;
    }

    public long slot0Address() {
        return UnsafeAccess.byteArrayDataAddress(backing);
    }

    public boolean slot0Aligned64() {
        return (slot0Address() & 63L) == 0L;
    }
}
