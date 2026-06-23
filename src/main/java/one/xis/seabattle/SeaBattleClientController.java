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
import java.util.Properties;
import java.util.logging.Logger;

@Controller
public class SeaBattleClientController {

    private static final Logger LOGGER = Logger.getLogger(SeaBattleClientController.class.getName());
    private static final int MAX_CLIENT_ERRORS = 50;
    private static final List<ClientErrorReport> RECENT_CLIENT_ERRORS = new ArrayList<>();

    private final GameStateService gameStateService;
    private final SseEndpoint sseEndpoint;
    private final SeaBattleEventService eventService;

    public SeaBattleClientController(GameStateService gameStateService,
                                     SseEndpoint sseEndpoint, SeaBattleEventService eventService) {
        this.gameStateService = gameStateService;
        this.sseEndpoint = sseEndpoint;
        this.eventService = eventService;
    }

    @Get("/")
    public ResponseEntity<?> redirectToStartPage() {
        return ResponseEntity.redirect("/index.html");
    }

    @Get("/sea-battle")
    public ResponseEntity<?> redirectToClientApp() {
        return ResponseEntity.redirect("/sea-battle/app");
    }

    @Get("/game/world")
    @Produces(ContentType.JSON_UTF8)
    public WorldMap getWorld() {
        return gameStateService.worldMap();
    }

    @Get("/game/state")
    @Produces(ContentType.JSON_UTF8)
    public GameSnapshot getGameState() {
        return gameStateService.snapshot();
    }

    @Post("/game/player-state")
    @Produces(ContentType.JSON_UTF8)
    public GameSnapshot updatePlayerState(@RequestBody PlayerStateUpdate update) {
        return gameStateService.updatePlayerState(update);
    }

    @Post("/game/fire-torpedo")
    @Produces(ContentType.JSON_UTF8)
    public GameSnapshot fireTorpedo(@RequestBody FireTorpedoRequest request) {
        return gameStateService.fireTorpedo(request);
    }

    @Post("/game/reset")
    @Produces(ContentType.JSON_UTF8)
    public GameSnapshot resetGame(@RequestBody ResetGameRequest request) {
        return gameStateService.reset(request);
    }

    @Post("/game/radar")
    @Produces(ContentType.JSON_UTF8)
    public RadarSnapshot getRadar(@RequestBody RadarRequest request) {
        return gameStateService.radar(request);
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
    public ResponseEntity<?> reportClientPerformance() {
        return ResponseEntity.noContent();
    }

    @Get("/game/client-errors")
    @Produces(ContentType.JSON_UTF8)
    public List<ClientErrorReport> getClientErrors() {
        synchronized (RECENT_CLIENT_ERRORS) {
            return List.copyOf(RECENT_CLIENT_ERRORS);
        }
    }

    @Get("/game/events/{playerId}/{teamId}")
    public void subscribeToGameEvents(@PathVariable("playerId") String playerId,
                                      @PathVariable("teamId") String teamId,
                                      HttpRequest request,
                                      HttpResponse response) {
        if (playerId == null || playerId.isBlank() || teamId == null || teamId.isBlank()) {
            response.setStatusCode(400);
            response.setBody("Missing playerId or teamId");
            return;
        }

        sseEndpoint.open(request, response, emitter -> {
            eventService.register(playerId, teamId, emitter);
            emitter.send(": connected\n\n").whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    eventService.unregister(playerId, emitter);
                }
            });
        }, emitter -> eventService.unregister(playerId, emitter));
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
}
