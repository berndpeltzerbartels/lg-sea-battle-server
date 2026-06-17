package one.xis.seabattle;

import com.google.gson.Gson;
import one.xis.context.Service;
import one.xis.http.SseConnectionKey;
import one.xis.http.SseConnectionHub;
import one.xis.http.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public final class SeaBattleEventService {

    private static final String PLAYER_SCOPE = "sea-battle-player";
    private static final long TICK_MILLIS = 100;

    private final SseConnectionHub connections;
    private final GameStateService gameStateService;
    private final Gson gson = new Gson();
    private final Map<String, RadarRequest> subscriptionsByPlayerId = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "sea-battle-events");
        thread.setDaemon(true);
        return thread;
    });

    public SeaBattleEventService(SseConnectionHub connections, GameStateService gameStateService) {
        this.connections = connections;
        this.gameStateService = gameStateService;
        executor.scheduleAtFixedRate(this::broadcastTick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    public void register(String playerId, String teamId, SseEmitter emitter) {
        subscriptionsByPlayerId.put(playerId, new RadarRequest(playerId, teamId));
        connections.register(PLAYER_SCOPE, playerId, emitter);
        send(playerId, createMessage(gameStateService.snapshot()));
    }

    public void unregister(String playerId, SseEmitter emitter) {
        connections.unregister(PLAYER_SCOPE, playerId, emitter);
        if (connections.connectionCount(SseConnectionKey.of(PLAYER_SCOPE, playerId)) == 0) {
            subscriptionsByPlayerId.remove(playerId);
            gameStateService.releasePlayer(playerId);
        }
    }

    private void broadcastTick() {
        if (subscriptionsByPlayerId.isEmpty()) {
            return;
        }

        GameSnapshot state = gameStateService.snapshot();
        subscriptionsByPlayerId.forEach((playerId, request) -> send(playerId, createMessage(state, request)));
    }

    private GameStreamMessage createMessage(GameSnapshot state) {
        return new GameStreamMessage("game-stream", state, null);
    }

    private GameStreamMessage createMessage(GameSnapshot state, RadarRequest request) {
        return new GameStreamMessage("game-stream", state, gameStateService.radar(request));
    }

    private void send(String playerId, GameStreamMessage message) {
        connections.sendData(PLAYER_SCOPE, playerId, gson.toJson(message));
    }
}
