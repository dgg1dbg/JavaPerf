package org.example.seqlock;

public final class HeapByteAlignedStartTable extends AbstractHeapByteTable {
    private final byte[] raw;
    private final long rawDataAddress;
    private final int alignedStart;

    public HeapByteAlignedStartTable(int capacity) {
        this(capacity, new byte[Math.multiplyExact(capacity, SlotLayout64.STRIDE) + SlotLayout64.STRIDE]);
    }

    private HeapByteAlignedStartTable(int capacity, byte[] raw) {
        this(capacity, raw, computeStart(raw));
    }

    private HeapByteAlignedStartTable(int capacity, byte[] raw, int start) {
        super(capacity, raw, start);
        this.raw = raw;
        this.rawDataAddress = UnsafeAccess.byteArrayDataAddress(raw);
        this.alignedStart = start;
    }

    private static int computeStart(byte[] raw) {
        final long addr = UnsafeAccess.byteArrayDataAddress(raw);
        final long alignedAddr = (addr + 63L) & ~63L;
        return (int) (alignedAddr - addr);
    }

    public int startOffset() {
        return alignedStart;
    }

    public long rawDataAddress() {
        return rawDataAddress;
    }

    public long slot0Address() {
        return rawDataAddress + alignedStart;
    }

    public boolean slot0Aligned64() {
        return (slot0Address() & 63L) == 0L;
    }

    public int rawLength() {
        return raw.length;
    }
}
