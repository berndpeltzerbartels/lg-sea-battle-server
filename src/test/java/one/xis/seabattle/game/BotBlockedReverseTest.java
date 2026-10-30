package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class BotBlockedReverseTest {
    private final NavigationService navigation = new NavigationService();
    private final RadarService radar = new RadarService();
    private Landmass land(String name, double z, double rx, double rz) {
        return new Landmass("island", name, 0, z, rx, rz, rx, rz, rx*1.2, rz*1.2,
                null, 1, null, null, null, List.of(), List.of(), List.of());
    }
    private GameSession scenario(boolean rearIsland, boolean parallel) {
        var map = new WorldMap(86001, rearIsland
                ? List.of(land("front",12,3,10), land("rear",-36,80,30))
                : List.of(land("front",12,3,10)));
        return new GameSession(new GameSetup("blocked-reverse",map,List.of(
                new FleetSetup("light",List.of(new ShipSetup("boat","light",new Vector2(0,0),0,"bot",7,0,9999,"torpedo-boat",0))),
                new FleetSetup("dark",List.of(new ShipSetup("enemy","dark",new Vector2(0,600),0,"scenario",2,0,9999,"torpedo-boat",0)))),
                List.of(new Vector2(1000,1000))),1,parallel);
    }
    @SuppressWarnings("unchecked")
    private Ship boat(GameSession session) throws Exception {
        Method m=GameSession.class.getDeclaredMethod("allShips");m.setAccessible(true);
        return ((List<Ship>)m.invoke(session)).stream().filter(s->s.id().equals("boat")).findFirst().orElseThrow();
    }

    @Test void freeReverseStillUsesTheOrdinaryEscapeAndEventuallyLeavesTheIsland() throws Exception {
        GameSession session=scenario(false,false);Ship boat=boat(session);
        double maximum=0;
        for(int tick=0;tick<900;tick++) {
            session.update(.1,radar,navigation,session.worldMap());
            assertEquals(0,boat.botForwardEscapeUntil,"A free reverse must not activate the exception");
            maximum=Math.max(maximum,Math.hypot(boat.position().x(),boat.position().z()));
        }
        assertTrue(maximum>100,"Normal backing out must still make progress");
    }

    @Test void blockedReverseReplansOnceAndLeavesTheNarrowGapWithBothPlanningModes() throws Exception {
        for(boolean parallel:List.of(false,true)) {
            GameSession session=scenario(true,parallel);Ship boat=boat(session);
            double maximum=0,firstEscape=-1;int changes=0,previousDirection=0;boolean exception=false;
            for(int tick=0;tick<900;tick++) {
                session.update(.1,radar,navigation,session.worldMap());
                exception |= boat.botForwardEscapeUntil>0;
                maximum=Math.max(maximum,Math.hypot(boat.position().x(),boat.position().z()));
                if(maximum>100&&firstEscape<0)firstEscape=(tick+1)*.1;
                int direction=Integer.compare(boat.snapshot().engineOrder(),2);
                if(direction!=0&&previousDirection!=0&&direction!=previousDirection)changes++;
                if(direction!=0)previousDirection=direction;
            }
            assertTrue(exception,"The rear island must actually trigger the exceptional replan");
            assertTrue(firstEscape>0&&firstEscape<75,"Must leave the gap with the target unchanged: time="+firstEscape+", distance="+maximum);
            assertTrue(changes<15,"Do not keep hectically reversing: "+changes);
            boat.respawn(new Vector2(0,200),0,100);
            assertEquals(0,boat.botForwardEscapeUntil);
        }
    }
}
