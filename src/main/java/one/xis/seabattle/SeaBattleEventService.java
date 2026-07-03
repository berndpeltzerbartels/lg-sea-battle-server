package one.xis.seabattle;

import com.google.gson.Gson;
import one.xis.context.Service;
import one.xis.http.SseConnectionKey;
import one.xis.http.SseConnectionHub;
import one.xis.http.SseEmitter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public final class SeaBattleEventService {

    private static final String PLAYER_SCOPE = "sea-battle-player";
    private static final long TICK_MILLIS = 100;
    private static final long DISCONNECT_GRACE_SECONDS = 10;

    private final SseConnectionHub connections;
    private final GameStateService gameStateService;
    private final SeaBattlePlayerRegistry playerRegistry;
    private final PlaySessionService playSessionService;
    private final Gson gson = new Gson();
    private final Set<String> players = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingUnregisters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "sea-battle-events");
        thread.setDaemon(true);
        return thread;
    });

    public SeaBattleEventService(SseConnectionHub connections, GameStateService gameStateService,
                                 SeaBattlePlayerRegistry playerRegistry,
                                 PlaySessionService playSessionService) {
        this.connections = connections;
        this.gameStateService = gameStateService;
        this.playerRegistry = playerRegistry;
        this.playSessionService = playSessionService;
        executor.scheduleAtFixedRate(this::broadcastTick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    public void register(String playerId, SseEmitter emitter) {
        if (!playerRegistry.isRegisteredPlayer(playerId)) {
            emitter.close();
            return;
        }
        cancelPendingUnregister(playerId);
        String replacedPlayerId = playerRegistry.registerPlayer(
                playerId,
                playerRegistry.playerName(initialsFromPlayerId(playerId)),
                playerRegistry.teamIdForPlayer(playerId));
        if (replacedPlayerId != null) {
            unregisterPlayer(replacedPlayerId);
        }
        playSessionService.beginSession(playerId, playerRegistry.accountIdForPlayer(playerId));
        players.add(playerId);
        connections.register(PLAYER_SCOPE, playerId, emitter);
        send(playerId, createMessage(gameStateService.snapshot()));
    }

    public void unregister(String playerId, SseEmitter emitter) {
        connections.unregister(PLAYER_SCOPE, playerId, emitter);
        if (connections.connectionCount(SseConnectionKey.of(PLAYER_SCOPE, playerId)) == 0) {
            scheduleUnregister(playerId);
        }
    }

    public void unregisterPlayer(String playerId) {
        cancelPendingUnregister(playerId);
        int score = gameStateService.snapshot().killsByPlayer().getOrDefault(playerId, 0);
        playSessionService.endSession(playerId, score);
        players.remove(playerId);
        playerRegistry.unregisterPlayer(playerId);
        gameStateService.releasePlayer(playerId);
    }

    private void scheduleUnregister(String playerId) {
        pendingUnregisters.computeIfAbsent(playerId, ignored ->
                executor.schedule(() -> {
                    pendingUnregisters.remove(playerId);
                    if (connections.connectionCount(SseConnectionKey.of(PLAYER_SCOPE, playerId)) == 0) {
                        unregisterPlayer(playerId);
                    }
                }, DISCONNECT_GRACE_SECONDS, TimeUnit.SECONDS));
    }

    private void cancelPendingUnregister(String playerId) {
        ScheduledFuture<?> pending = pendingUnregisters.remove(playerId);
        if (pending != null) {
            pending.cancel(false);
        }
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

    private String initialsFromPlayerId(String playerId) {
        String prefix = "player-";
        if (playerId == null || !playerId.startsWith(prefix)) {
            return "";
        }
        int end = playerId.indexOf('-', prefix.length());
        if (end <= prefix.length()) {
            return playerId.substring(prefix.length()).toUpperCase();
        }
        return playerId.substring(prefix.length(), end).toUpperCase();
    }
}
