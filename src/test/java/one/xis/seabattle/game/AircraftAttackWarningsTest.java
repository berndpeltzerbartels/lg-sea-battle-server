package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AircraftAttackWarningsTest {
    private final WorldMap sea = new WorldMap(1, List.of());
    private Ship ship(String id, String team, double z) { return new Ship(id, team, new Vector2(0,z),0,"captain"); }

    @Test void oneNoticePerApproachThenRearmAfterDeparture() {
        var warnings = new AircraftAttackWarnings();
        Ship plane = ship("plane","dark",100), target = ship("boat","light",0);
        warnings.observe(0,plane,target);
        var first = warnings.snapshot().get("boat");
        assertEquals("voraus", first.direction());
        for(int i=1;i<=9;i++) {
            warnings.expire(i);
            warnings.observe(i,plane,target);
            if(i<6) assertEquals(first.id(), warnings.snapshot().get("boat").id());
            else assertTrue(warnings.snapshot().isEmpty());
        }
        warnings.expire(20);
        warnings.observe(20,plane,target);
        assertNotEquals(first.id(), warnings.snapshot().get("boat").id());
    }

    @Test void changingTheTargetWarnsTheNewCrewAndDirectionsUseItsHeading() {
        var warnings = new AircraftAttackWarnings();
        Ship plane = ship("plane","dark",100), a = ship("a","light",0), b = ship("b","light",200);
        warnings.observe(0,plane,a);
        warnings.observe(.1,plane,b);
        assertEquals("achtern", warnings.snapshot().get("b").direction());
        assertEquals(2,warnings.snapshot().size());
    }

    private GameSession session(double distance, double planeHeading, String controller) {
        var plane=new ShipSetup("plane","dark",new Vector2(0,0),planeHeading,"bot",7,0,99,"scout-plane",170);
        var boat=new ShipSetup("boat","light",new Vector2(0,distance),0,controller,2,0,99,"torpedo-boat",0);
        return new GameSession(new GameSetup("aircraft-warning-test",sea,
                List.of(new FleetSetup("dark",List.of(plane)),new FleetSetup("light",List.of(boat))),
                List.of(new Vector2(1000,1000))));
    }
    private void tick(GameSession session) { session.update(.1,new RadarService(),new NavigationService(),sea); }

    @Test void warnsDuringTheAttackApproachBeforeAnyWeaponIsReleased() {
        var session=session(500,0,"human");tick(session);
        assertEquals("achtern",session.snapshot().aircraftWarnings().get("boat").direction());
        assertTrue(session.snapshot().torpedoes().isEmpty());
        assertTrue(session.snapshot().bombs().isEmpty());
    }
    @Test void distantOrTurningAircraftDoNotTriggerAnAttackWarning() {
        var distant=session(900,0,"human");tick(distant);
        assertTrue(distant.snapshot().aircraftWarnings().isEmpty());
        var turning=session(500,Math.PI,"human");tick(turning);
        assertTrue(turning.snapshot().aircraftWarnings().isEmpty());
    }
    @Test void surfacedHumanSubmarineReceivesTheAircraftAttackWarning() {
        var session=session(500,0,"human");
        session.applyPlayerState(new PlayerStateUpdate("human","light",0,500,0,0,0,2,0,0,true,
                "submarine",-.78,0,null,null,null,null,"surface"),new NavigationService(),sea);
        tick(session);
        assertEquals("achtern",session.snapshot().aircraftWarnings().get("boat").direction());
    }

    @Test void botRecipientsDoNotReceiveOrTrackAdditionalWarnings() {
        var session=session(500,0,"bot");tick(session);
        assertTrue(session.snapshot().aircraftWarnings().isEmpty());
    }
}
