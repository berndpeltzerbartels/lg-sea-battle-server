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

@Controller
public class SeaBattleClientController {

    private final GameStateService gameStateService;
    private final SseEndpoint sseEndpoint;
    private final SeaBattleEventService eventService;

    public SeaBattleClientController(GameStateService gameStateService,
                                     SseEndpoint sseEndpoint, SeaBattleEventService eventService) {
        this.gameStateService = gameStateService;
        this.sseEndpoint = sseEndpoint;
        this.eventService = eventService;
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
