package one.xis.seabattle.game;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

final class BotNavigationStream {
    private static final ForkJoinPool POOL = new ForkJoinPool(2, pool -> {
        var worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        worker.setName("sea-battle-bot-navigation-" + worker.getId());
        return worker;
    }, null, false, 2, 2, 1, pool -> true, 60, TimeUnit.SECONDS);

    private BotNavigationStream() {
    }

    static <T, R> List<R> map(List<T> items, Function<T, R> calculation) {
        if (items.size() < 2) return items.stream().map(calculation).toList();
        List<Outcome<R>> results = POOL.submit(() -> items.parallelStream().map(item -> {
            try {
                return new Outcome<R>(calculation.apply(item), null);
            } catch (RuntimeException | Error failure) {
                // Finish every reader before releasing the session lock, including on failure.
                return new Outcome<R>(null, failure);
            }
        }).toList()).join();
        for (Outcome<R> result : results) {
            if (result.failure() instanceof RuntimeException failure) throw failure;
            if (result.failure() instanceof Error failure) throw failure;
        }
        return results.stream().map(Outcome::value).toList();
    }

    private record Outcome<T>(T value, Throwable failure) {
    }
}
