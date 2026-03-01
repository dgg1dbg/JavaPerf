package org.example.seqlock;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

abstract class AbstractHeapByteTable extends AbstractSlotTable64 {
    private static final VarHandle INT_VIEW =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());
    private static final VarHandle LONG_VIEW =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());

    protected final byte[] data;
    protected final int start;

    protected AbstractHeapByteTable(int capacity, byte[] data, int start) {
        super(capacity);
        this.data = data;
        this.start = start;

        if (start < 0 || start >= SlotLayout64.STRIDE) {
            throw new IllegalArgumentException("start must be in [0, 63], start=" + start);
        }

        final int required = Math.addExact(start, Math.multiplyExact(capacity, SlotLayout64.STRIDE));
        if (required > data.length) {
            throw new IllegalArgumentException("insufficient backing array");
        }
    }

    @Override
    protected final int offsetFor(int id) {
        return start + (id * SlotLayout64.STRIDE);
    }

    @Override
    protected final int getIntPlain(int offset) {
        return (int) INT_VIEW.get(data, offset);
    }

    @Override
    protected final void putIntPlain(int offset, int value) {
        INT_VIEW.set(data, offset, value);
    }

    @Override
    protected final long getLongPlain(int offset) {
        return (long) LONG_VIEW.get(data, offset);
    }

    @Override
    protected final void putLongPlain(int offset, long value) {
        LONG_VIEW.set(data, offset, value);
    }

    @Override
    protected final int getIntAcquire(int offset) {
        return (int) INT_VIEW.getAcquire(data, offset);
    }

    @Override
    protected final void putIntRelease(int offset, int value) {
        INT_VIEW.setRelease(data, offset, value);
    }
}
