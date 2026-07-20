package one.xis.seabattle;

import one.xis.context.Service;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class SeaBattleDiagnosticsService {

    private static final Logger LOGGER = Logger.getLogger(SeaBattleDiagnosticsService.class.getName());
    private static final boolean DIAGNOSTICS_ENABLED = Boolean.getBoolean("seaBattle.diagnostics.enabled");
    private static final long SERVER_SNAPSHOT_SECONDS = Long.getLong("seaBattle.diagnostics.serverSnapshotSeconds", 30);

    private final GameStateService gameStateService;
    private final SeaBattlePlayerRegistry playerRegistry;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "sea-battle-diagnostics");
        thread.setDaemon(true);
        return thread;
    });

    public SeaBattleDiagnosticsService(GameStateService gameStateService, SeaBattlePlayerRegistry playerRegistry) {
        this.gameStateService = gameStateService;
        this.playerRegistry = playerRegistry;
        if (DIAGNOSTICS_ENABLED) {
            executor.scheduleAtFixedRate(this::logServerSnapshotSafely, SERVER_SNAPSHOT_SECONDS, SERVER_SNAPSHOT_SECONDS, TimeUnit.SECONDS);
        }
    }

    public void logClientDiagnostics(ClientDiagnosticsReport report) {
        if (!DIAGNOSTICS_ENABLED) {
            return;
        }
        if (report == null) {
            return;
        }
        LOGGER.info(() -> String.format(
                Locale.ROOT,
                "sea-battle-client-diagnostics player=%s team=%s ship=%s pos=(%.1f,%.1f) heading=%.3f speed=%.2f turn=%.4f engine=%d rudder=%.1f damage=%s torps=%d localTorps=%d serverTorps=%d visuals=%d fire=%s fireError=%s playerState=%s playerStateError=%s eventSource=%s eventReady=%s streamAge=%.2f lastKey=\"%s\" ownLaunch=%s http=%d stateHttp=%d fireHttp=%d maxHttpMs=%.2f expired=%s seconds=%.2f started=%s ended=%s",
                safe(report.playerId()),
                safe(report.teamId()),
                safe(report.shipId()),
                report.x(),
                report.z(),
                report.heading(),
                report.speed(),
                report.turnVelocity(),
                report.engineOrder(),
                report.rudderDegrees(),
                safe(report.playerDamageState()),
                report.playerTorpedoesRemaining(),
                report.localTorpedoCount(),
                report.serverTorpedoes(),
                report.serverTorpedoVisuals(),
                safe(report.fireTorpedoSync()),
                safe(report.fireTorpedoSyncError()),
                safe(report.playerStateSync()),
                safe(report.playerStateSyncError()),
                safe(report.gameEventSource()),
                report.gameEventSourceReady(),
                report.gameStreamAgeSeconds(),
                safe(report.lastKey()),
                safe(report.ownServerTorpedoLaunch()),
                report.httpRequests(),
                report.playerStateHttpRequests(),
                report.fireTorpedoHttpRequests(),
                report.maxHttpMs(),
                safe(report.sessionExpired()),
                report.seconds(),
                safe(report.startedAt()),
                safe(report.endedAt())
        ));
    }

    public void logFireRequest(String playerId, String teamId, GameSnapshot before, GameSnapshot after, String result) {
        ShipSnapshot beforeShip = findPlayerShip(before, playerId);
        ShipSnapshot afterShip = findPlayerShip(after, playerId);
        int beforeTorpedoes = before == null || before.torpedoes() == null ? -1 : before.torpedoes().size();
        int afterTorpedoes = after == null || after.torpedoes() == null ? -1 : after.torpedoes().size();
        LOGGER.info(() -> String.format(
                Locale.ROOT,
                "sea-battle-fire player=%s team=%s result=%s beforeShip=%s beforeStock=%d beforeServerTorps=%d afterShip=%s afterStock=%d afterServerTorps=%d deltaTorps=%d",
                safe(playerId),
                safe(teamId),
                safe(result),
                shipId(beforeShip),
                torpedoStock(beforeShip),
                beforeTorpedoes,
                shipId(afterShip),
                torpedoStock(afterShip),
                afterTorpedoes,
                afterTorpedoes - beforeTorpedoes
        ));
    }

    public void logRejectedRequest(String kind, String playerId, String reason) {
        LOGGER.warning(() -> String.format(
                Locale.ROOT,
                "sea-battle-rejected kind=%s player=%s reason=%s activePlayers=%s",
                safe(kind),
                safe(playerId),
                safe(reason),
                activePlayers()
        ));
    }

    private void logServerSnapshotSafely() {
        try {
            logServerSnapshot();
        } catch (RuntimeException exception) {
            LOGGER.warning(() -> "sea-battle-server-diagnostics failed: " + exception.getMessage());
        }
    }

    private void logServerSnapshot() {
        GameSnapshot snapshot = gameStateService.snapshot();
        long activeShips = snapshot.ships().stream().filter(ship -> "active".equals(ship.state())).count();
        String humanShips = snapshot.ships().stream()
                .filter(ship -> ship.controlledBy() != null && ship.controlledBy().startsWith("player-"))
                .sorted(Comparator.comparing(ShipSnapshot::controlledBy))
                .map(this::shipSummary)
                .collect(Collectors.joining(";"));
        LOGGER.info(() -> String.format(
                Locale.ROOT,
                "sea-battle-server-diagnostics session=%s state=%s t=%.2f ships=%d activeShips=%d torpedoes=%d impacts=%d activePlayers=%s humanShips=%s",
                safe(snapshot.sessionId()),
                safe(snapshot.state()),
                snapshot.t(),
                snapshot.ships().size(),
                activeShips,
                snapshot.torpedoes().size(),
                snapshot.torpedoImpacts().size(),
                activePlayers(),
                humanShips.isBlank() ? "-" : humanShips
        ));
    }

    private String activePlayers() {
        return playerRegistry.players().stream()
                .map(player -> player.initials() + ":" + player.teamId() + ":" + player.playerId())
                .sorted()
                .collect(Collectors.joining(";"));
    }

    private String shipSummary(ShipSnapshot ship) {
        return String.format(
                Locale.ROOT,
                "%s/%s/%s stock=%d pos=(%.0f,%.0f) speed=%.1f state=%s",
                safe(ship.controlledBy()),
                safe(ship.teamId()),
                safe(ship.id()),
                ship.torpedoesRemaining(),
                ship.x(),
                ship.z(),
                ship.speed(),
                safe(ship.state())
        );
    }

    private ShipSnapshot findPlayerShip(GameSnapshot snapshot, String playerId) {
        if (snapshot == null || playerId == null) {
            return null;
        }
        return snapshot.ships().stream()
                .filter(ship -> Objects.equals(playerId, ship.controlledBy()))
                .findFirst()
                .orElse(null);
    }

    private String shipId(ShipSnapshot ship) {
        return ship == null ? "-" : ship.id();
    }

    private int torpedoStock(ShipSnapshot ship) {
        return ship == null ? -1 : ship.torpedoesRemaining();
    }

    private String safe(String value) {
        return value == null || value.isBlank()
                ? "-"
                : value.replace('\n', ' ').replace('\r', ' ');
    }
}
