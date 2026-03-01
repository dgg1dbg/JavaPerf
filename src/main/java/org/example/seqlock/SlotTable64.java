package org.example.seqlock;

public interface SlotTable64 {
    int capacity();

    void write(int id, Snapshot64 src);

    boolean tryLoad(int id, Snapshot64 dst);

    int seq(int id);

    boolean updated(int id, int lastSeq);
}
