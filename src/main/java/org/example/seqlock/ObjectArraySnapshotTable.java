package org.example.seqlock;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class ObjectArraySnapshotTable implements SlotTable64 {
    private static final VarHandle SEQ_HANDLE;
    private static final VarHandle LAST_UPDATE_TYPE_HANDLE;

    static {
        try {
            final MethodHandles.Lookup l = MethodHandles.lookup();
            SEQ_HANDLE = l.findVarHandle(Snapshot64.class, "seq", int.class);
            LAST_UPDATE_TYPE_HANDLE = l.findVarHandle(Snapshot64.class, "lastUpdateType", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Snapshot64[] slots;

    public ObjectArraySnapshotTable(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.slots = new Snapshot64[capacity];
        for (int i = 0; i < capacity; i++) {
            slots[i] = new Snapshot64();
        }
    }

    @Override
    public int capacity() {
        return slots.length;
    }

    @Override
    public void write(int id, Snapshot64 src) {
        final Snapshot64 slot = slot(id);
        final int current = (int) SEQ_HANDLE.get(slot);
        final int odd = (current + 1) | 1;

        SEQ_HANDLE.setRelease(slot, odd);

        slot.int0 = src.int0;
        slot.int1 = src.int1;
        slot.int2 = src.int2;
        slot.int3 = src.int3;
        slot.int4 = src.int4;
        slot.int5 = src.int5;
        slot.int6 = src.int6;
        slot.int7 = src.int7;
        slot.int8 = src.int8;
        slot.int9 = src.int9;
        slot.int10 = src.int10;
        slot.int11 = src.int11;
        slot.long0 = src.long0;

        LAST_UPDATE_TYPE_HANDLE.setRelease(slot, src.lastUpdateType);
        SEQ_HANDLE.setRelease(slot, odd + 1);
    }

    @Override
    public boolean tryLoad(int id, Snapshot64 dst) {
        final Snapshot64 slot = slot(id);
        final int seq1 = (int) SEQ_HANDLE.getAcquire(slot);
        if ((seq1 & 1) != 0) {
            return false;
        }

        final int lastType = (int) LAST_UPDATE_TYPE_HANDLE.getAcquire(slot);

        dst.int0 = slot.int0;
        dst.int1 = slot.int1;
        dst.int2 = slot.int2;
        dst.int3 = slot.int3;
        dst.int4 = slot.int4;
        dst.int5 = slot.int5;
        dst.int6 = slot.int6;
        dst.int7 = slot.int7;
        dst.int8 = slot.int8;
        dst.int9 = slot.int9;
        dst.int10 = slot.int10;
        dst.int11 = slot.int11;
        dst.long0 = slot.long0;

        final int seq2 = (int) SEQ_HANDLE.getAcquire(slot);
        if (seq1 == seq2 && (seq2 & 1) == 0) {
            dst.lastUpdateType = lastType;
            dst.seq = seq2;
            return true;
        }
        return false;
    }

    @Override
    public int seq(int id) {
        return (int) SEQ_HANDLE.getAcquire(slot(id));
    }

    @Override
    public boolean updated(int id, int lastSeq) {
        final int current = seq(id);
        return (current & 1) == 0 && current != lastSeq;
    }

    private Snapshot64 slot(int id) {
        if (id < 0 || id >= slots.length) {
            throw new IndexOutOfBoundsException("id=" + id + ", capacity=" + slots.length);
        }
        return slots[id];
    }
}
