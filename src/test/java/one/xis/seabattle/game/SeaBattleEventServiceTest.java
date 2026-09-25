package one.xis.seabattle.game;

import com.google.gson.Gson;
import one.xis.http.SseConnectionHub;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SeaBattleEventServiceTest {
    @Test
    void sharesSerializedSnapshotWithinTickButRefreshesNextTick() throws Exception {
        GameStateService state = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()), null,
                new GameSelectionService(new MemoryGamePropertyRepository()),
                new RadarService(), new NavigationService());
        RecordingHub hub = new RecordingHub();
        SeaBattleEventService events = new SeaBattleEventService(hub, state, null, null, null);
        try {
            // Drive ticks explicitly so scheduling cannot change the assertions.
            stopExecutor(events);
            stopExecutor(state);
            Field playersField = SeaBattleEventService.class.getDeclaredField("players");
            playersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> players = (Set<String>) playersField.get(events);
            var tick = SeaBattleEventService.class.getDeclaredMethod("broadcastTick");
            tick.setAccessible(true);
            tick.invoke(events);
            assertTrue(hub.payloads.isEmpty());

            players.addAll(List.of("first", "second", "third"));
            String expected = new Gson().toJson(new GameStreamMessage("game-stream", state.snapshot()));
            tick.invoke(events);
            assertEquals(3, hub.payloads.size());
            assertEquals(players, Set.copyOf(hub.recipients));
            assertEquals(expected, hub.payloads.get(0));
            assertSame(hub.payloads.get(0), hub.payloads.get(1));
            assertSame(hub.payloads.get(0), hub.payloads.get(2));

            state.resetToSetup("ram-side");
            tick.invoke(events);
            assertEquals(6, hub.payloads.size());
            assertNotEquals(hub.payloads.get(0), hub.payloads.get(3));
            assertEquals(new Gson().toJson(new GameStreamMessage("game-stream", state.snapshot())),
                    hub.payloads.get(3));
            assertSame(hub.payloads.get(3), hub.payloads.get(4));
            assertSame(hub.payloads.get(3), hub.payloads.get(5));
        } finally {
            stopExecutor(events);
            stopExecutor(state);
        }
    }

    private static void stopExecutor(Object service) throws Exception {
        Field field = service.getClass().getDeclaredField("executor");
        field.setAccessible(true);
        ExecutorService executor = (ExecutorService) field.get(service);
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static final class RecordingHub extends SseConnectionHub {
        final List<String> recipients = new ArrayList<>();
        final List<String> payloads = new ArrayList<>();

        @Override
        public CompletionStage<Void> sendData(String scope, String id, String data) {
            assertEquals("sea-battle-player", scope);
            recipients.add(id);
            payloads.add(data);
            return CompletableFuture.completedFuture(null);
        }
    }
}
