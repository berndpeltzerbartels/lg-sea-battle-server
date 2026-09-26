package one.xis.seabattle.game;

import one.xis.http.*;
import java.util.function.Supplier;

@Controller
public class CrewController {
    private final CrewService crew;
    private final SeaBattleEventService events;
    public CrewController(CrewService crew, SeaBattleEventService events) { this.crew = crew; this.events = events; }
    @Get("/game/crew/{playerId}") @Produces(ContentType.JSON_UTF8)
    public CrewService.View view(@PathVariable("playerId") String playerId) { return crew.view(playerId); }
    @Post("/game/crew/station") @Produces(ContentType.JSON_UTF8)
    public ResponseEntity<?> station(@RequestBody CrewService.Command command) { return execute(() -> crew.switchStation(command)); }
    @Post("/game/crew/motion") @Produces(ContentType.JSON_UTF8)
    public ResponseEntity<?> motion(@RequestBody CrewService.Command command) { return execute(() -> crew.motion(command)); }
    @Post("/game/crew/aim") @Produces(ContentType.JSON_UTF8)
    public ResponseEntity<?> aim(@RequestBody CrewService.Command command) { return execute(() -> crew.aim(command)); }
    @Post("/game/crew/fire-flak") @Produces(ContentType.JSON_UTF8)
    public ResponseEntity<?> flak(@RequestBody CrewService.Command command) { return execute(() -> crew.fire(command, "flak")); }
    @Post("/game/crew/fire-cannon") @Produces(ContentType.JSON_UTF8)
    public ResponseEntity<?> cannon(@RequestBody CrewService.Command command) { return execute(() -> crew.fire(command, "cannon")); }
    @Post("/game/crew/fire-torpedo") @Produces(ContentType.JSON_UTF8)
    public ResponseEntity<?> torpedo(@RequestBody CrewService.Command command) { return execute(() -> crew.fire(command, "torpedo")); }
    @Post("/game/crew/leave") @Produces(ContentType.JSON_UTF8)
    public ResponseEntity<?> leave(@RequestBody CrewService.Command command) {
        events.unregisterPlayer(command.playerId());
        return ResponseEntity.noContent();
    }
    @Post("/game/crew/report-plane-hit") @Produces(ContentType.JSON_UTF8)
    public ResponseEntity<?> planeHit(@RequestBody CrewService.HitCommand command) { return execute(() -> crew.planeHit(command)); }
    private ResponseEntity<?> execute(Supplier<?> action) {
        try { return ResponseEntity.ok(action.get()); }
        catch (IllegalArgumentException | NullPointerException e) { return ResponseEntity.status(409, "Position oder Schiff nicht verfuegbar."); }
    }
}
