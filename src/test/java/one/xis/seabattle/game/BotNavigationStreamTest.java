package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BotNavigationStreamTest {
    @Test
    void usesTwoDedicatedWorkersAndPreservesOrder() {
        Set<Thread> workers = ConcurrentHashMap.newKeySet();
        CountDownLatch started = new CountDownLatch(2);
        List<Integer> inputs = IntStream.range(0, 64).boxed().toList();
        var result = BotNavigationStream.map(inputs, i -> {
            workers.add(Thread.currentThread());
            started.countDown();
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            return i * 2;
        });
        assertEquals(2, workers.size());
        assertTrue(workers.stream().allMatch(thread -> thread.getName().startsWith("sea-battle-bot-navigation-")));
        assertEquals(inputs.stream().map(i -> i * 2).toList(), result);
    }

    @Test
    void failureDoesNotLeaveCalculationsRunningAfterReturn() {
        AtomicInteger completed = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> BotNavigationStream.map(
                IntStream.range(0, 64).boxed().toList(), i -> {
                    completed.incrementAndGet();
                    if (i == 0) throw new IllegalStateException("test");
                    return i;
                }));
        assertEquals(64, completed.get());
    }

    @Test
    void permitsEmptyBatchesAndAbsentCommands() {
        assertEquals(List.of(), BotNavigationStream.map(List.of(), i -> fail("Empty batch")));
        assertNull(BotNavigationStream.map(List.of(1), i -> null).get(0));
        assertEquals(2, BotNavigationStream.map(List.of(1, 2), i -> null).size());
    }
}
