package one.xis.seabattle.game;

import one.xis.seabattle.webapp.gameconfig.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FleetIncreaseSessionTest {
    private final RadarService radar = new RadarService();
    private final NavigationService navigation = new NavigationService();
    private final Vector2 fixedRespawn = new Vector2(400,400);
    private GameSession session(String id) {
        return session(id, () -> new GameRuntimeConfig(TorpedoBoatBotConfig.defaults(), new FleetSizeConfig(2,2)));
    }
    private GameSession session(String id, java.util.function.Supplier<GameRuntimeConfig> configuration) {
        var setup = new GameSetup(id, new WorldMap(1,List.of()), List.of(
                new FleetSetup("light", List.of(new VehicleSetup("L","light",new Vector2(-100,0),0,"bot",2,0,0,"submarine",0))),
                new FleetSetup("dark", List.of(new VehicleSetup("D","dark",new Vector2(100,0),0,"bot",2,0,0,"scout-plane",170)))),
                List.of(fixedRespawn));
        return new GameSession(setup, configuration);
    }
    private void tick(GameSession session, double time) { session.updateIdle(time,radar,navigation,session.worldMap()); }
    @Test void growthUsesScannedPointsAndAircraftCountTowardTheSameLimit() {
        var session = session("islands"); tick(session,0); tick(session,2.99);
        assertEquals(2,session.snapshot().ships().size());
        tick(session,.01);
        assertEquals(3,session.snapshot().ships().size());
        tick(session,3);
        assertEquals(4,session.snapshot().ships().size());
        for (var added : session.snapshot().ships().stream().filter(v -> v.id().contains("-R")).toList()) {
            var position = new Vector2(added.x(),added.z());
            assertNotEquals(fixedRespawn,position);
            assertTrue(session.waterPoints().stream().anyMatch(p -> p.distanceTo(position) < .01));
            if (added.vehicleType().equals("scout-plane")) assertEquals(ScoutPlane.CRUISE_Y,added.y());
        }
        tick(session,30); assertEquals(4,session.snapshot().ships().size());
        assertEquals(List.of(fixedRespawn),session.respawnCandidates());
    }
    @Test void fixedTestSetupsKeepTheirOriginalPopulation() {
        var session = session("two-ship-duel"); tick(session,0); tick(session,30);
        assertEquals(2,session.snapshot().ships().size());
    }
    @Test void ordinaryLossesStillUseTheFixedPoints() throws Exception {
        var session = session("islands"); tick(session,0);
        var method = GameSession.class.getDeclaredMethod("allShips"); method.setAccessible(true);
        Vehicle victim = ((List<Vehicle>)method.invoke(session)).get(0);
        victim.sink(0);
        tick(session,.1);
        assertEquals(fixedRespawn,victim.position());
        assertEquals("active",victim.state());
    }
    @Test void reducedSlotsAreRestoredOnNewPointsOnlyAfterTheNextIncreaseInterval() throws Exception {
        var config = new java.util.concurrent.atomic.AtomicReference<>(new GameRuntimeConfig(TorpedoBoatBotConfig.defaults(),new FleetSizeConfig(0,0)));
        var session = session("islands",config::get); tick(session,0);
        assertEquals(2,session.snapshot().ships().stream().filter(v -> v.state().equals("active")).count());
        var method = GameSession.class.getDeclaredMethod("allShips"); method.setAccessible(true);
        for (Vehicle vehicle : (List<Vehicle>)method.invoke(session)) vehicle.sink(0);
        tick(session,.1);
        assertEquals(0,session.snapshot().ships().stream().filter(v -> v.state().equals("active")).count());
        config.set(new GameRuntimeConfig(TorpedoBoatBotConfig.defaults(),new FleetSizeConfig(1,1)));
        tick(session,0); tick(session,2.9);
        assertEquals(0,session.snapshot().ships().stream().filter(v -> v.state().equals("active")).count());
        tick(session,.11);
        assertEquals(1,session.snapshot().ships().stream().filter(v -> v.state().equals("active")).count());
        tick(session,3);
        assertEquals(2,session.snapshot().ships().size());
        for (var vehicle : session.snapshot().ships()) {
            assertEquals("active",vehicle.state());
            assertNotEquals(fixedRespawn,new Vector2(vehicle.x(),vehicle.z()));
        }
    }

}
