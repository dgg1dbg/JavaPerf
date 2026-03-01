package org.example.seqlock;

abstract class AbstractSlotTable64 implements SlotTable64 {
    private final int capacity;

    protected AbstractSlotTable64(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
    }

    @Override
    public final int capacity() {
        return capacity;
    }

    @Override
    public final void write(int id, Snapshot64 src) {
        final int base = baseOffset(id);
        final int seqOffset = base + SlotLayout64.SEQ;
        final int lastTypeOffset = base + SlotLayout64.LAST_UPDATE_TYPE;

        final int current = getIntPlain(seqOffset);
        final int odd = (current + 1) | 1;

        // Publish write begin.
        putIntRelease(seqOffset, odd);

        putIntPlain(base + SlotLayout64.INT_0, src.int0);
        putIntPlain(base + SlotLayout64.INT_1, src.int1);
        putIntPlain(base + SlotLayout64.INT_2, src.int2);
        putIntPlain(base + SlotLayout64.INT_3, src.int3);
        putIntPlain(base + SlotLayout64.INT_4, src.int4);
        putIntPlain(base + SlotLayout64.INT_5, src.int5);
        putIntPlain(base + SlotLayout64.INT_6, src.int6);
        putIntPlain(base + SlotLayout64.INT_7, src.int7);
        putIntPlain(base + SlotLayout64.INT_8, src.int8);
        putIntPlain(base + SlotLayout64.INT_9, src.int9);
        putIntPlain(base + SlotLayout64.INT_10, src.int10);
        putIntPlain(base + SlotLayout64.INT_11, src.int11);
        putLongPlain(base + SlotLayout64.LONG_0, src.long0);

        putIntRelease(lastTypeOffset, src.lastUpdateType);
        putIntRelease(seqOffset, odd + 1);
    }

    @Override
    public final boolean tryLoad(int id, Snapshot64 dst) {
        final int base = baseOffset(id);
        final int seqOffset = base + SlotLayout64.SEQ;
        final int lastTypeOffset = base + SlotLayout64.LAST_UPDATE_TYPE;

        final int seq1 = getIntAcquire(seqOffset);
        if ((seq1 & 1) != 0) {
            return false;
        }

        final int lastType = getIntAcquire(lastTypeOffset);

        dst.int0 = getIntPlain(base + SlotLayout64.INT_0);
        dst.int1 = getIntPlain(base + SlotLayout64.INT_1);
        dst.int2 = getIntPlain(base + SlotLayout64.INT_2);
        dst.int3 = getIntPlain(base + SlotLayout64.INT_3);
        dst.int4 = getIntPlain(base + SlotLayout64.INT_4);
        dst.int5 = getIntPlain(base + SlotLayout64.INT_5);
        dst.int6 = getIntPlain(base + SlotLayout64.INT_6);
        dst.int7 = getIntPlain(base + SlotLayout64.INT_7);
        dst.int8 = getIntPlain(base + SlotLayout64.INT_8);
        dst.int9 = getIntPlain(base + SlotLayout64.INT_9);
        dst.int10 = getIntPlain(base + SlotLayout64.INT_10);
        dst.int11 = getIntPlain(base + SlotLayout64.INT_11);
        dst.long0 = getLongPlain(base + SlotLayout64.LONG_0);

        final int seq2 = getIntAcquire(seqOffset);
        if (seq1 == seq2 && (seq2 & 1) == 0) {
            dst.lastUpdateType = lastType;
            dst.seq = seq2;
            return true;
        }
        return false;
    }

    @Override
    public final int seq(int id) {
        final int base = baseOffset(id);
        return getIntAcquire(base + SlotLayout64.SEQ);
    }

    @Override
    public final boolean updated(int id, int lastSeq) {
        final int current = seq(id);
        return (current & 1) == 0 && current != lastSeq;
    }

    protected final int baseOffset(int id) {
        if (id < 0 || id >= capacity) {
            throw new IndexOutOfBoundsException("id=" + id + ", capacity=" + capacity);
        }
        return offsetFor(id);
    }

    protected abstract int offsetFor(int id);

    protected abstract int getIntPlain(int offset);

    protected abstract void putIntPlain(int offset, int value);

    protected abstract long getLongPlain(int offset);

    protected abstract void putLongPlain(int offset, long value);

    protected abstract int getIntAcquire(int offset);

    protected abstract void putIntRelease(int offset, int value);
}
