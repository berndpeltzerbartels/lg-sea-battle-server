package one.xis.seabattle.game;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class TwoThreadSimulation {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sea-battle-simulation-worker");
        thread.setDaemon(true);
        return thread;
    });

    private TwoThreadSimulation() {
    }

    static <T> void forEach(List<T> items, Consumer<T> update) {
        if (items.size() < 2) {
            items.forEach(update);
            return;
        }
        // Interleave fleets so the two batches have a similar mix of vehicle types.
        CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
            for (int i = 1; i < items.size(); i += 2) update.accept(items.get(i));
        }, WORKER);
        try {
            for (int i = 0; i < items.size(); i += 2) update.accept(items.get(i));
        } finally {
            // Never release the session monitor while a worker is still changing ships.
            worker.join();
        }
    }
}
