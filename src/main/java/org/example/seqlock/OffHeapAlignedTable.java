package org.example.seqlock;

import java.nio.ByteBuffer;
import org.agrona.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;

public final class OffHeapAlignedTable extends AbstractSlotTable64 {
    private final ByteBuffer memory;
    private final UnsafeBuffer buffer;

    public OffHeapAlignedTable(int capacity) {
        super(capacity);
        final int bytes = Math.multiplyExact(capacity, SlotLayout64.STRIDE);
        this.memory = BufferUtil.allocateDirectAligned(bytes, SlotLayout64.STRIDE);
        this.buffer = new UnsafeBuffer(memory);
    }

    @Override
    protected int offsetFor(int id) {
        return id * SlotLayout64.STRIDE;
    }

    @Override
    protected int getIntPlain(int offset) {
        return buffer.getInt(offset);
    }

    @Override
    protected void putIntPlain(int offset, int value) {
        buffer.putInt(offset, value);
    }

    @Override
    protected long getLongPlain(int offset) {
        return buffer.getLong(offset);
    }

    @Override
    protected void putLongPlain(int offset, long value) {
        buffer.putLong(offset, value);
    }

    @Override
    protected int getIntAcquire(int offset) {
        return buffer.getIntVolatile(offset);
    }

    @Override
    protected void putIntRelease(int offset, int value) {
        buffer.putIntOrdered(offset, value);
    }

    public boolean slot0Aligned64() {
        return (buffer.addressOffset() & 63L) == 0L;
    }
}
