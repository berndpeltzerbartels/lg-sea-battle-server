package one.xis.seabattle;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

final class PublishedGameSnapshots {

    private static final int RING_SIZE = 3;

    private final AtomicReferenceArray<GameSnapshot> snapshots = new AtomicReferenceArray<>(RING_SIZE);
    private final AtomicLong sequence = new AtomicLong();

    PublishedGameSnapshots(GameSnapshot initialSnapshot) {
        snapshots.set(0, initialSnapshot);
    }

    GameSnapshot current() {
        long currentSequence = sequence.get();
        GameSnapshot snapshot = snapshots.get(slot(currentSequence));
        if (snapshot != null) {
            return snapshot;
        }
        return snapshots.get(0);
    }

    synchronized GameSnapshot publish(GameSnapshot snapshot) {
        long nextSequence = sequence.get() + 1;
        snapshots.set(slot(nextSequence), snapshot);
        sequence.set(nextSequence);
        return snapshot;
    }

    private int slot(long value) {
        return (int) (value % RING_SIZE);
    }
}
