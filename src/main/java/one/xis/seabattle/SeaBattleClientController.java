package one.xis.seabattle;

import one.xis.http.ContentType;
import one.xis.http.Controller;
import one.xis.http.Get;
import one.xis.http.HttpRequest;
import one.xis.http.HttpResponse;
import one.xis.http.PathVariable;
import one.xis.http.Post;
import one.xis.http.Produces;
import one.xis.http.PublicResources;
import one.xis.http.RequestBody;
import one.xis.http.ResponseEntity;
import one.xis.http.SseEndpoint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Controller
@PublicResources("/public")
public class SeaBattleClientController {

    private static final String CLIENT_INDEX_RESOURCE = "/public/sea-battle/index.html";
    private final WorldMapService worldMapService;
    private final GameStateService gameStateService;
    private final SseEndpoint sseEndpoint;
    private final SeaBattleEventService eventService;

    public SeaBattleClientController(WorldMapService worldMapService, GameStateService gameStateService,
                                     SseEndpoint sseEndpoint, SeaBattleEventService eventService) {
        this.worldMapService = worldMapService;
        this.gameStateService = gameStateService;
        this.sseEndpoint = sseEndpoint;
        this.eventService = eventService;
    }

    @Get("/sea-battle")
    public ResponseEntity<?> redirectToClientApp() {
        return ResponseEntity.redirect("/sea-battle/app");
    }

    @Get("/")
    @Produces(ContentType.TEXT_HTML_UTF8)
    public ResponseEntity<String> getRootClientApp() {
        return getClientApp();
    }

    @Get("/sea-battle/app")
    @Produces(ContentType.TEXT_HTML_UTF8)
    public ResponseEntity<String> getClientApp() {
        try (InputStream input = getClass().getResourceAsStream(CLIENT_INDEX_RESOURCE)) {
            if (input == null) {
                return ResponseEntity.notFound();
            }
            return ResponseEntity.ok(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return ResponseEntity.status(500, "Could not load Sea Battle client.");
        }
    }

    @Get("/game/world")
    @Produces(ContentType.JSON_UTF8)
    public WorldMap getWorld() {
        return worldMapService.world();
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
}
