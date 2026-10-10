package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TwoThreadSimulationTest {
    @Test
    void emptyAndSingleItemBatchesStayOnCaller() {
        TwoThreadSimulation.forEach(List.of(), item -> fail("Empty batch"));
        Thread caller = Thread.currentThread();
        TwoThreadSimulation.forEach(List.of(1), item -> assertSame(caller, Thread.currentThread()));
    }

    @Test
    void workerFailuresAreNotLost() {
        var failure = assertThrows(CompletionException.class,
                () -> TwoThreadSimulation.forEach(List.of(0, 1), item -> {
                    if (item == 1) throw new IllegalStateException("worker failed");
                }));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test
    void bothThreadsRunAndFinishBeforeReturning() {
        Set<Thread> threads = ConcurrentHashMap.newKeySet();
        CountDownLatch started = new CountDownLatch(2);
        AtomicInteger completed = new AtomicInteger();
        TwoThreadSimulation.forEach(List.of(0, 1, 2, 3, 4), item -> {
            threads.add(Thread.currentThread());
            started.countDown();
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            completed.incrementAndGet();
        });
        assertEquals(2, threads.size());
        assertEquals(5, completed.get());
    }

    @Test
    void waitsForWorkerEvenWhenCallerFails() {
        AtomicInteger completed = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> TwoThreadSimulation.forEach(List.of(0, 1), item -> {
            if (item == 0) throw new IllegalStateException("test");
            completed.incrementAndGet();
        }));
        assertEquals(1, completed.get());
    }

    @Test
    void serialAndParallelRoundsHaveIdenticalResults() throws Exception {
        var factory = new DefaultGameSetupFactory(new WorldMapService());
        for (String scenario : List.of("default", "fleet-clash", "ram-side", "scout-plane")) {
            GameSetup setup = factory.setup(scenario);
            GameSession serial = new GameSession(setup, 1);
            GameSession parallel = new GameSession(setup, 2);
            GameSession stream = new GameSession(setup, 1, true);
            RadarService radar = new RadarService();
            NavigationService navigation = new NavigationService();
            for (int tick = 0; tick < 600; tick++) {
                serial.update(.1, radar, navigation, setup.worldMap());
                parallel.update(.1, radar, navigation, setup.worldMap());
                stream.update(.1, radar, navigation, setup.worldMap());
                var expected = serial.snapshot();
                var actual = parallel.snapshot();
                var streamSnapshot = stream.snapshot();
                for (var component : GameSnapshot.class.getRecordComponents()) {
                    if (component.getName().equals("instanceId")) continue;
                    assertEquals(component.getAccessor().invoke(expected), component.getAccessor().invoke(actual),
                            scenario + " tick=" + tick + " " + component.getName());
                    assertEquals(component.getAccessor().invoke(expected), component.getAccessor().invoke(streamSnapshot),
                            "stream " + scenario + " tick=" + tick + " " + component.getName());
                }
            }
        }
    }
}
