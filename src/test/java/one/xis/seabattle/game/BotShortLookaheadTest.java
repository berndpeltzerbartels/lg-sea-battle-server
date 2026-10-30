package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BotShortLookaheadTest {
    private final NavigationService navigation = new NavigationService();
    private final WorldMap water = new WorldMap(87001, List.of());
    private WorldMap island() {
        return new WorldMap(87001,List.of(new Landmass("island","between-coarse-samples",0,40,6,4,6,4,7.2,4.8,
                null,1,null,null,null,List.of(),List.of(),List.of())));
    }
    @Test void halfSecondSlotIsAdditionalAndDoesNotCoincideWithTheExistingPlanningSlots() {
        var plan=new BotNavigation("schedule");int checks=0;
        for(int tick=0;tick<=200;tick++) {
            boolean target=plan.targetDue(tick),route=plan.routeDue(tick),encounter=plan.encounterDue(tick),forward=plan.forwardDue(tick);
            if(tick==0)continue;
            if(forward){checks++;assertFalse(target||route||encounter);}
        }
        assertEquals(40,checks);
        assertEquals(2*BotNavigation.SHIP_LENGTH,BotNavigation.FORWARD_RANGE);
    }
    @Test void newLayerCatchesAnIslandBetweenTheCoarseRouteSamplesBeforeTheEmergencyDistance() {
        Ship ship=new Ship("boat","light",new Vector2(0,0),0,"bot");
        var plan=ship.botNavigation;
        plan.route=BotNavigation.freeRoute(ship,new BotNavigation.Course(0,8),BotNavigation.ROUTE_RANGE,navigation,island());
        assertEquals(0,plan.route.heading(),1e-9,"The small island deliberately falls between the long scan samples");
        plan.checkForward(ship,navigation,island());
        assertNotNull(plan.forward);
        assertNotEquals(0,plan.forward.heading(),1e-9);
        assertEquals(4,plan.forward.engineOrder());
        assertSame(plan.forward,plan.selected(null));
        plan.near=new BotNavigation.Course(.3,2);
        assertSame(plan.near,plan.selected(null),"Immediate ship avoidance keeps priority");
        plan.checkForward(ship,navigation,water);
        assertNull(plan.forward,"A clear forward check releases only its own layer");
        assertNotNull(plan.near);
    }
    @Test void actualBoatTurnsBeforeReachingTheSmallIslandWithoutReversing() {
        var map=island();var radar=new RadarService();
        var session=new GameSession(new GameSetup("short-lookahead",map,List.of(
                new FleetSetup("light",List.of(new ShipSetup("boat","light",new Vector2(0,0),0,"bot",2,0,9999,"torpedo-boat",0))),
                new FleetSetup("dark",List.of(new ShipSetup("enemy","dark",new Vector2(0,600),0,"scenario",2,0,9999,"torpedo-boat",0)))),List.of()));
        double furthest=0;boolean turned=false;
        for(int tick=0;tick<400;tick++) {
            session.update(.1,radar,navigation,map);
            var boat=session.snapshot().ships().stream().filter(s->s.id().equals("boat")).findFirst().orElseThrow();
            assertTrue(boat.engineOrder()>=2,"The early check must avoid having to back out");
            assertFalse(navigation.isShipBlocked(new Vector2(boat.x(),boat.z()),boat.heading(),map));
            if(boat.z()<25&&Math.abs(boat.heading())>.05)turned=true;
            furthest=Math.max(furthest,Math.hypot(boat.x(),boat.z()));
        }
        assertTrue(turned);assertTrue(furthest>100);
    }
    @Test void existingNearScanRecordsEvenNonConflictingFriendsWithoutAnExtraScan() {
        Ship ship=new Ship("boat","light",new Vector2(0,0),0,"bot");
        Ship friend=new Ship("friend","light",new Vector2(40,0),0,"bot");
        var plan=new BotNavigation("separate-plan");plan.route=new BotNavigation.Course(0,5);
        plan.checkNear(ship,List.of(ship,friend),navigation,water);
        assertTrue(plan.nearbyTraffic,"Startup must not interfere with a close passing situation even before avoidance is needed");
        plan.checkNear(ship,List.of(ship),navigation,water);
        assertFalse(plan.nearbyTraffic);
    }

    @Test void ordinaryStartupUsesRequestedGearStepsAndNeverRaisesTheRequestedMaximum() {
        var ramp=new BotCruiseAcceleration();
        assertEquals(4,ramp.order(8,0,0,true));
        assertEquals(4,ramp.order(8,1,.9,true));
        assertEquals(5,ramp.order(8,2,1,true));
        assertEquals(7,ramp.order(8,4,2,true));
        assertEquals(8,ramp.order(8,6,3,true));
        assertEquals(2,ramp.order(2,0,4,true));
        assertEquals(4,ramp.order(5,0,5,true));
        assertEquals(5,ramp.order(5,2,7,true));
        assertEquals(4,ramp.order(4,4,8,true));
    }
    @Test void combatAndAvoidanceBypassTheRampAndMovingCruiseIsNotSlowedDown() {
        var ramp=new BotCruiseAcceleration();
        assertEquals(8,ramp.order(8,0,0,false));
        assertEquals(0,ramp.order(0,-1,.1,false));
        assertEquals(8,ramp.order(8,10,.2,true));
        assertEquals(4,ramp.order(8,0,4,true));
        assertEquals(8,ramp.order(8,1,4.1,false));
        assertEquals(8,ramp.order(8,5,4.2,true));
    }
}
