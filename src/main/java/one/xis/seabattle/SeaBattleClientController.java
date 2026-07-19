package one.xis.seabattle;

import one.xis.http.ContentType;
import one.xis.http.Controller;
import one.xis.http.Get;
import one.xis.http.HttpRequest;
import one.xis.http.HttpResponse;
import one.xis.http.PathVariable;
import one.xis.http.Post;
import one.xis.http.Produces;
import one.xis.http.RequestBody;
import one.xis.http.ResponseEntity;
import one.xis.http.SseEndpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

@Controller
public class SeaBattleClientController {

    private static final Logger LOGGER = Logger.getLogger(SeaBattleClientController.class.getName());
    private static final int MAX_CLIENT_ERRORS = 50;
    private static final List<ClientErrorReport> RECENT_CLIENT_ERRORS = new ArrayList<>();

    private final GameStateService gameStateService;
    private final SseEndpoint sseEndpoint;
    private final SeaBattleEventService eventService;
    private final SeaBattlePlayerRegistry playerRegistry;
    private final AccountService accountService;
    private final GameService gameService;
    private final PlaySessionService playSessionService;
    private final SeaBattleDiagnosticsService diagnosticsService;

    public SeaBattleClientController(GameStateService gameStateService,
                                     SseEndpoint sseEndpoint, SeaBattleEventService eventService,
                                     SeaBattlePlayerRegistry playerRegistry,
                                     AccountService accountService,
                                     GameService gameService,
                                     PlaySessionService playSessionService,
                                     SeaBattleDiagnosticsService diagnosticsService) {
        this.gameStateService = gameStateService;
        this.sseEndpoint = sseEndpoint;
        this.eventService = eventService;
        this.playerRegistry = playerRegistry;
        this.accountService = accountService;
        this.gameService = gameService;
        this.playSessionService = playSessionService;
        this.diagnosticsService = diagnosticsService;
    }

    @Get("/")
    public ResponseEntity<?> redirectToStartPage() {
        return ResponseEntity.redirect("/index.html");
    }

    @Get("/sea-battle")
    public ResponseEntity<?> redirectToClientApp() {
        return ResponseEntity.redirect("/app");
    }

    @Get("/start.htm")
    public ResponseEntity<?> redirectLegacyStartPage() {
        return ResponseEntity.redirect("/start.html");
    }

    @Get("/game/world")
    @Produces(ContentType.JSON_UTF8)
    public WorldMap getWorld() {
        return gameStateService.worldMap();
    }

    @Get("/game/debug/respawn-candidates")
    @Produces(ContentType.JSON_UTF8)
    public List<Vector2> getRespawnCandidates() {
        return gameStateService.respawnCandidates();
    }

    @Get("/game/state")
    @Produces(ContentType.JSON_UTF8)
    public GameSnapshot getGameState() {
        return gameStateService.snapshot();
    }

    @Get("/game/session/{playerId}")
    public ResponseEntity<?> getPlayerSession(@PathVariable("playerId") String playerId) {
        return isRegistered(playerId)
                ? ResponseEntity.noContent()
                : ResponseEntity.status(403, "Player is not registered");
    }

    @Get("/game/session/account/{accountId}")
    @Produces(ContentType.JSON_UTF8)
    public ResponseEntity<?> getPlayerSessionByAccount(@PathVariable("accountId") String accountId) {
        return accountService.findAccountById(accountId)
                .<ResponseEntity<?>>map(this::activeSession)
                .orElseGet(() -> ResponseEntity.status(403, "Account is not registered"));
    }

    @Post("/game/start")
    @Produces(ContentType.JSON_UTF8)
    public ResponseEntity<?> startGame(@RequestBody StartGameRequest request) {
        String accountId = request.accountId() == null || request.accountId().isBlank()
                ? UUID.randomUUID().toString()
                : request.accountId();
        Account account = new Account(
                accountId,
                normalizeName(request.nickname()),
                request.alias() == null ? "" : request.alias().trim().toUpperCase(Locale.ROOT),
                request.team() == null ? "" : request.team().trim().toLowerCase(Locale.ROOT),
                request.email() == null || request.email().isBlank() ? null : request.email().trim()
        );
        if (account.nickname().length() < 2 || account.alias().isBlank() || account.alias().length() > 5
                || (!"light".equals(account.team()) && !"dark".equals(account.team()))) {
            return ResponseEntity.status(400, "Invalid player registration");
        }
        accountService.saveAccount(account);
        ResponseEntity<?> response = startOrFindSession(account);
        if (response.getStatusCode() >= 400) {
            return response;
        }
        return ResponseEntity.ok(new StartGameResponse(accountId, (PlayerLogin) response.getBody()));
    }

    @Post("/game/player-state")
    @Produces(ContentType.JSON_UTF8)
    public ResponseEntity<?> updatePlayerState(@RequestBody PlayerStateUpdate update) {
        String teamId = teamIdFor(update.playerId());
        if (teamId == null) {
            diagnosticsService.logRejectedRequest("player-state", update.playerId(), "not-registered");
            return ResponseEntity.status(403, "Player is not registered");
        }
        return ResponseEntity.ok(gameStateService.updatePlayerState(new PlayerStateUpdate(
                update.playerId(),
                teamId,
                update.x(),
                update.z(),
                update.heading(),
                update.speed(),
                update.turnVelocity(),
                update.engineOrder(),
                update.rudderDegrees(),
                update.clientTime(),
                update.debugTeleport(),
                update.vehicleType()
        )));
    }

    @Post("/game/fire-torpedo")
    @Produces(ContentType.JSON_UTF8)
    public ResponseEntity<?> fireTorpedo(@RequestBody FireTorpedoRequest request) {
        String teamId = teamIdFor(request.playerId());
        if (teamId == null) {
            diagnosticsService.logRejectedRequest("fire", request.playerId(), "not-registered");
            return ResponseEntity.status(403, "Player is not registered");
        }
        GameSnapshot before = gameStateService.snapshot();
        GameSnapshot after = gameStateService.fireTorpedo(new FireTorpedoRequest(request.playerId(), teamId, request.vehicleType()));
        diagnosticsService.logFireRequest(request.playerId(), teamId, before, after, "ok");
        return ResponseEntity.ok(after);
    }

    @Post("/game/reset")
    @Produces(ContentType.JSON_UTF8)
    public GameSnapshot resetGame(@RequestBody ResetGameRequest request) {
        return gameStateService.reset(request);
    }

    @Get("/game/version")
    @Produces(ContentType.JSON_UTF8)
    public BuildInfo getBuildInfo() {
        return loadBuildInfo();
    }

    @Post("/game/client-error")
    public ResponseEntity<?> reportClientError(@RequestBody ClientErrorReport report) {
        if (report == null) {
            LOGGER.warning("Sea Battle client error report was empty");
            return ResponseEntity.noContent();
        }
        rememberClientError(report);
        LOGGER.warning(() -> "Sea Battle client error"
                + " type=" + safe(report.type())
                + " message=" + safe(report.message())
                + " source=" + safe(report.source())
                + " line=" + report.line()
                + " column=" + report.column()
                + " screen=" + safe(report.screen())
                + " viewport=" + safe(report.viewport())
                + " url=" + safe(report.url())
                + " userAgent=" + safe(report.userAgent())
                + " stack=" + safe(report.stack()));
        return ResponseEntity.noContent();
    }

    @Post("/game/client-performance")
    public ResponseEntity<?> reportClientPerformance(@RequestBody ClientDiagnosticsReport report) {
        diagnosticsService.logClientDiagnostics(report);
        return ResponseEntity.noContent();
    }

    @Get("/game/client-errors")
    @Produces(ContentType.JSON_UTF8)
    public List<ClientErrorReport> getClientErrors() {
        synchronized (RECENT_CLIENT_ERRORS) {
            return List.copyOf(RECENT_CLIENT_ERRORS);
        }
    }

    @Get("/game/events/{playerId}")
    public void subscribeToGameEvents(@PathVariable("playerId") String playerId,
                                      HttpRequest request,
                                      HttpResponse response) {
        if (playerId == null || playerId.isBlank()) {
            response.setStatusCode(400);
            response.setBody("Missing playerId");
            return;
        }
        if (!isRegistered(playerId)) {
            response.setStatusCode(403);
            response.setBody("Player is not registered");
            return;
        }

        sseEndpoint.open(request, response, emitter -> {
            eventService.register(playerId, emitter);
            emitter.send(": connected\n\n").whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    eventService.unregister(playerId, emitter);
                }
            });
        }, emitter -> eventService.unregister(playerId, emitter));
    }

    private boolean isRegistered(String playerId) {
        return playerRegistry.isRegisteredPlayer(playerId);
    }

    private ResponseEntity<?> startOrFindSession(Account account) {
        String initials = account.alias() == null ? "" : account.alias().trim().toUpperCase(Locale.ROOT);
        String teamId = account.team() == null ? "" : account.team().trim().toLowerCase(Locale.ROOT);
        if (initials.isBlank() || teamId.isBlank()) {
            return ResponseEntity.status(403, "Account is incomplete");
        }
        if (playerRegistry.isAliasRegisteredForOtherAccount(initials, account.id())) {
            return ResponseEntity.status(409, "Alias is already active");
        }
        if (playSessionService.isAliasActiveForOtherAccount(gameService.activeGameId(), initials, account.id())) {
            return ResponseEntity.status(409, "Alias is already active");
        }

        String playerId = playerRegistry.activePlayerIdForAccountAlias(account.id(), initials);
        if (playerId == null || playerId.isBlank()) {
            playerId = createPlayerId(initials);
        }
        boolean gameIsEmpty = playerRegistry.players().isEmpty();
        playerRegistry.register(playerId, initials, normalizeName(account.nickname()), teamId, account.id());
        if (gameIsEmpty) {
            gameStateService.resetCurrentSetup();
        }
        gameStateService.activateTeam(teamId);
        return ResponseEntity.ok(new PlayerLogin(playerId, initials, teamId));
    }

    private ResponseEntity<?> activeSession(Account account) {
        String initials = account.alias() == null ? "" : account.alias().trim().toUpperCase(Locale.ROOT);
        String teamId = account.team() == null ? "" : account.team().trim().toLowerCase(Locale.ROOT);
        if (initials.isBlank() || teamId.isBlank()) {
            return ResponseEntity.status(403, "Account is incomplete");
        }

        String playerId = playerRegistry.activePlayerIdForAccountAlias(account.id(), initials);
        if (playerId == null || playerId.isBlank() || !playerRegistry.isRegisteredPlayer(playerId)) {
            return ResponseEntity.status(403, "Player is not active");
        }
        return ResponseEntity.ok(new PlayerLogin(playerId, initials, teamId));
    }

    private String teamIdFor(String playerId) {
        if (!isRegistered(playerId)) {
            return null;
        }
        String teamId = playerRegistry.teamIdForPlayer(playerId);
        return teamId == null || teamId.isBlank() ? null : teamId;
    }

    private String createPlayerId(String initials) {
        return "player-" + initials + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static void rememberClientError(ClientErrorReport report) {
        synchronized (RECENT_CLIENT_ERRORS) {
            RECENT_CLIENT_ERRORS.add(report);
            while (RECENT_CLIENT_ERRORS.size() > MAX_CLIENT_ERRORS) {
                RECENT_CLIENT_ERRORS.remove(0);
            }
        }
    }

    private static BuildInfo loadBuildInfo() {
        Properties properties = new Properties();
        try (var input = SeaBattleClientController.class.getResourceAsStream("/sea-battle-build.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "Could not read Sea Battle build info: " + e.getMessage());
        }
        return new BuildInfo(
                properties.getProperty("version", "unknown"),
                properties.getProperty("commit", "unknown"),
                properties.getProperty("buildTime", "unknown")
        );
    }

    public record PlayerLogin(String playerId, String initials, String teamId) {
    }

    public record StartGameRequest(String accountId, String nickname, String alias, String team, String email) {
    }

    public record StartGameResponse(String accountId, PlayerLogin player) {
    }
}
