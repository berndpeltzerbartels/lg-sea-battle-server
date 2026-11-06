package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BotWaypointHierarchyTest {
    private final NavigationService navigation = new NavigationService();
    private final Vector2 destination = new Vector2(3000, 0);
    private TorpedoBoat boat(double x, double z) {
        return new TorpedoBoat("OUT", "dark", new Vector2(x,z), Math.PI/2,"bot");
    }
    private Landmass island(String id,double x,double z,double rx,double rz) {
        return new Landmass("island",id,x,z,rx,rz,rx,rz,rx*1.2,rz*1.2,null,1,null,null,null,List.of(),List.of(),List.of());
    }
    private List<Landmass> ring(double radius, double islandRadius) {
        var islands = new ArrayList<Landmass>();
        for(int i=0;i<36;i++) {
            double angle=i*Math.PI/18;
            islands.add(island("ring-"+i,Math.sin(angle)*radius,Math.cos(angle)*radius,islandRadius,islandRadius));
        }
        return islands;
    }
    @Test void mediumStageFollowsTheLargeWaypointInsteadOfTurningBackTowardTheEnemy() {
        var vehicle=boat(-3440,120);
        var world=new WorldMapService().denseWorld();
        vehicle.botNavigation.planRoute(vehicle,new Vector2(0,120),7,navigation,world);
        var plan=vehicle.botNavigation;
        assertNotNull(plan.largeWaypoint); assertNotNull(plan.routeWaypoint);
        assertTrue(Math.abs(plan.largeWaypoint.z()-vehicle.position().z())>500);
        Vector2 parent=plan.largeWaypoint.subtract(vehicle.position());
        Vector2 child=plan.routeWaypoint.subtract(vehicle.position());
        assertEquals(0,MathSupport.normalizeAngle(Math.atan2(parent.x(),parent.z())-Math.atan2(child.x(),child.z())),1e-8);
    }
    @Test void smallerIslandIsAvoidedTowardTheParentPointAndDoesNotRestoreTheOriginalTarget() {
        var vehicle=boat(-3440,120);
        var world=new WorldMapService().denseWorld();
        var plan=vehicle.botNavigation;
        plan.planRoute(vehicle,new Vector2(0,120),7,navigation,world);
        Vector2 parent=plan.largeWaypoint;
        Vector2 direction=parent.subtract(vehicle.position()).scale(1/vehicle.position().distanceTo(parent));
        Vector2 rock=vehicle.position().add(direction.scale(95));
        var land=new ArrayList<>(world.landmasses());
        land.add(island("small-obstacle",rock.x(),rock.z(),18,18));
        plan.planRoute(vehicle,new Vector2(0,120),7,navigation,new WorldMap(2,land));
        assertTrue(parent.distanceTo(plan.largeWaypoint)<.01,"The coarse scan may miss this small rock");
        Vector2 child=plan.routeWaypoint.subtract(vehicle.position());
        double correction=Math.abs(MathSupport.normalizeAngle(Math.atan2(child.x(),child.z())-Math.atan2(direction.x(),direction.z())));
        assertTrue(correction>.1 && correction<Math.PI/3,"Fine scan detours toward the parent rather than turning east: "+correction);
    }

    @Test void failedLargeScanContributesNoPointAndTheMediumStageUsesTheOriginalDestination() {
        var islands=ring(500,65);
        islands.add(island("large-coast",0,1100,600,600));
        var world=new WorldMap(1,islands);
        var vehicle=boat(0,0);
        assertFalse(navigation.isShipBlocked(vehicle.position(),vehicle.heading(),world));
        vehicle.botNavigation.planRoute(vehicle,destination,7,navigation,world);
        assertNull(vehicle.botNavigation.largeWaypoint);
        assertNotNull(vehicle.botNavigation.routeWaypoint);
        assertEquals(BotNavigation.ROUTE_RANGE,vehicle.botNavigation.routeWaypoint.x(),.001);
        assertEquals(0,vehicle.botNavigation.routeWaypoint.z(),.001);
    }
    @Test void aTightIslandGroupFallsBackToShortPlanningAtReducedSpeed() {
        var vehicle=boat(0,0);
        vehicle.botNavigation.planRoute(vehicle,destination,7,navigation,new WorldMap(1,ring(200,35)));
        assertNull(vehicle.botNavigation.largeWaypoint);
        assertNotNull(vehicle.botNavigation.routeWaypoint);
        assertEquals(BotNavigation.FORWARD_RANGE,vehicle.position().distanceTo(vehicle.botNavigation.routeWaypoint),.001);
        assertTrue(vehicle.botNavigation.route.engineOrder()<=4);
    }
    @Test void noFreeDirectionReturnsNullAndClearsStalePlans() {
        var vehicle=boat(0,0);var plan=vehicle.botNavigation;
        plan.route=new BotNavigation.Course(0,7);plan.near=plan.encounter=plan.forward=plan.route;
        plan.planRoute(vehicle,destination,7,navigation,new WorldMap(1,List.of(island("blocked",0,0,1000,1000))));
        assertNull(plan.routeWaypoint); assertNull(plan.route);
        assertNull(plan.near);assertNull(plan.forward);assertNull(plan.encounter);
    }
    @Test void westOfTheRealContinentReturnsToTheBattleAreaWithoutLosingItsTarget() throws Exception {
        var world=new WorldMapService().denseWorld();
        var setup=new GameSetup("coast-return-test",world,List.of(
                new FleetSetup("dark",List.of(new VehicleSetup("OUT","dark",new Vector2(-3440,120),Math.PI/2,"bot",5,0,100000,"torpedo-boat",0))),
                new FleetSetup("light",List.of(new VehicleSetup("IN","light",new Vector2(0,120),0,"player-PROBE-test",2,0,100000,"submarine",0)))),List.of(new Vector2(1000,0)));
        var session=new GameSession(setup);
        var access=GameSession.class.getDeclaredMethod("allShips");access.setAccessible(true);
        var vehicle=((List<Vehicle>)access.invoke(session)).stream().filter(v->v.id().equals("OUT")).findFirst().orElseThrow();
        var radar=new RadarService();boolean returned=false;
        for(int tick=0;tick<4500;tick++) {
            session.update(.1,radar,navigation,world);
            if(vehicle.position().x()>-800 && vehicle.position().distanceTo(new Vector2(0,120))<1000) { returned=true; break; }
        }
        assertTrue(returned,"The boat must leave the western coast instead of circling there for ten minutes");
        assertNotNull(vehicle.surface().botNavigation.target);
        assertEquals("IN",vehicle.surface().botNavigation.target.id());
        double time=session.snapshot().t();
        var debug=session.botNavigationDebug();
        var plan=debug.plans().stream().filter(p -> p.shipId().equals("OUT")).findFirst().orElseThrow();
        assertEquals("IN",plan.targetId());
        assertNotNull(plan.routeWaypoint());
        assertEquals(time,session.snapshot().t());
    }
}
