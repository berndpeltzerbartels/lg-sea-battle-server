package one.xis.seabattle;

import one.xis.http.ContentType;
import one.xis.http.Controller;
import one.xis.http.Get;
import one.xis.http.Produces;
import one.xis.http.PublicResources;
import one.xis.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Controller
@PublicResources("/public")
public class SeaBattleClientController {

    private static final String CLIENT_INDEX_RESOURCE = "/public/sea-battle/index.html";
    private final WorldMapService worldMapService;
    private final GameStateService gameStateService;

    public SeaBattleClientController(WorldMapService worldMapService, GameStateService gameStateService) {
        this.worldMapService = worldMapService;
        this.gameStateService = gameStateService;
    }

    @Get("/sea-battle")
    public ResponseEntity<?> redirectToClientApp() {
        return ResponseEntity.redirect("/sea-battle/app");
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
}
