package one.xis.seabattle;

import com.google.gson.Gson;
import one.xis.context.Service;
import one.xis.http.SseConnectionKey;
import one.xis.http.SseConnectionHub;
import one.xis.http.SseEmitter;

import java.util.Set;
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
    private final SeaBattlePlayerRegistry playerRegistry;
    private final Gson gson = new Gson();
    private final Set<String> players = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "sea-battle-events");
        thread.setDaemon(true);
        return thread;
    });

    public SeaBattleEventService(SseConnectionHub connections, GameStateService gameStateService,
                                 SeaBattlePlayerRegistry playerRegistry) {
        this.connections = connections;
        this.gameStateService = gameStateService;
        this.playerRegistry = playerRegistry;
        executor.scheduleAtFixedRate(this::broadcastTick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    public void register(String playerId, String teamId, SseEmitter emitter) {
        players.add(playerId);
        connections.register(PLAYER_SCOPE, playerId, emitter);
        send(playerId, createMessage(gameStateService.snapshot()));
    }

    public void unregister(String playerId, SseEmitter emitter) {
        connections.unregister(PLAYER_SCOPE, playerId, emitter);
        if (connections.connectionCount(SseConnectionKey.of(PLAYER_SCOPE, playerId)) == 0) {
            unregisterPlayer(playerId);
        }
    }

    public void unregisterPlayer(String playerId) {
        players.remove(playerId);
        playerRegistry.unregisterPlayer(playerId);
        gameStateService.releasePlayer(playerId);
    }

    private void broadcastTick() {
        if (players.isEmpty()) {
            return;
        }

        GameSnapshot state = gameStateService.snapshot();
        players.forEach(playerId -> send(playerId, createMessage(state)));
    }

    private GameStreamMessage createMessage(GameSnapshot state) {
        return new GameStreamMessage("game-stream", state, null);
    }

    private void send(String playerId, GameStreamMessage message) {
        connections.sendData(PLAYER_SCOPE, playerId, gson.toJson(message));
    }
}
