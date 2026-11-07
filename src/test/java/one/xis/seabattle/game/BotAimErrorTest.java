package one.xis.seabattle.game;

import one.xis.seabattle.webapp.gameconfig.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static one.xis.seabattle.game.GameRules.*;

class BotAimErrorTest {
    private final NavigationService navigation=new NavigationService();
    private final RadarService radar=new RadarService();

    private VehicleSetup vehicle(String id,String team,String controller,Vector2 position,String type) {
        return new VehicleSetup(id,team,position,0,controller,ENGINE_STOP,0,9999,type,0);
    }
    private String shooterId() {
        for(int i=0;i<100;i++) {
            var shooter=new TorpedoBoat("aim-"+i,"light",new Vector2(0,0),0,"bot");
            var target=new TorpedoBoat("target","dark",new Vector2(0,600),0,"captain");
            if(Math.abs(shooter.botAimError.angle(shooter,target,Math.toRadians(5)))>Math.toRadians(3)) return shooter.id();
        }
        throw new AssertionError("No deterministic test sample");
    }
    private GameSession session(double maximum,List<Landmass> land,boolean friend) {
        var config=new GameRuntimeConfig(BotCombatConfig.defaults().withAimErrorAngle(maximum));
        var ships=new java.util.ArrayList<VehicleSetup>();
        ships.add(vehicle(shooterId(),"light","bot",new Vector2(0,0),"torpedo-boat"));
        if(friend) ships.add(vehicle("friend","light","crew",new Vector2(300,0),"torpedo-boat"));
        var setup=new GameSetup("aim-error",new WorldMap(1,land),List.of(
                new FleetSetup("light",ships),new FleetSetup("dark",List.of(vehicle("target","dark","captain",new Vector2(0,600),"torpedo-boat")))),List.of());
        var session = new GameSession(setup,()->config);
        session.update(0,radar,navigation,session.worldMap());
        shooter(session).nextFireTime(0);
        return session;
    }
    private Vehicle get(GameSession session,String id) { return session.calculationShips().stream().filter(v->v.id().equals(id)).findFirst().orElseThrow(); }
    private Vehicle shooter(GameSession session) { return get(session,shooterId()); }
    private double aim(GameSession session) {
        var shooter=shooter(session);var target=get(session,"target");
        double error=session.botAimErrorAngle(shooter,target);
        shooter.heading=MathSupport.normalizeAngle(BotTorpedoAim.forTarget(shooter,target).heading()+error);
        return error;
    }

    @Test void estimateStaysStableUntilTheNextShotAndScalesWithTheConfiguredLimit() {
        var shooter=new TorpedoBoat("stable","light",new Vector2(0,0),0,"bot");
        var target=new TorpedoBoat("target","dark",new Vector2(0,600),0,"captain");
        double first=shooter.botAimError.angle(shooter,target,Math.toRadians(1));
        assertTrue(Math.abs(first)<=Math.toRadians(1));
        for(int i=0;i<100;i++) {
            target.position=new Vector2(i,600);shooter.heading=.01*i;
            assertEquals(first,shooter.botAimError.angle(shooter,target,Math.toRadians(1)));
        }
        assertEquals(first*2,shooter.botAimError.angle(shooter,target,Math.toRadians(2)));
        shooter.markFired(0,12);
        assertNotEquals(first,shooter.botAimError.angle(shooter,target,Math.toRadians(1)));
        assertEquals(0,shooter.botAimError.angle(shooter,target,0));
    }

    @Test void aPlausibleImperfectShotLeavesTheRealTubeAndCanMissWithoutTargetEvasion() {
        var session=session(Math.toRadians(5),List.of(),false);
        var attacker=shooter(session);var target=get(session,"target");
        double error=aim(session);
        assertTrue(Math.abs(error)>Math.toRadians(3));
        assertTrue(session.fireTorpedoAtTarget(attacker,target,12,0));
        var shot=session.snapshot().torpedoes().get(0);
        assertEquals(attacker.heading(),shot.heading(),.00051); // Snapshots round angles to three decimals.
        // Keep both boats stationary so only the real torpedo trajectory determines the outcome.
        attacker.controlledBy("other-player");attacker.applyCommand(ENGINE_STOP,0);
        for(int i=0;i<230;i++) session.update(.1,radar,navigation,session.worldMap());
        assertEquals("active",target.state(),"No evasion should be necessary to avoid this deliberate near miss");
    }

    @Test void thirtyDegreeShotsAreStillRejectedAndZeroRestoresThePreciseGate() {
        var session=session(Math.toRadians(5),List.of(),false);var attacker=shooter(session);var target=get(session,"target");
        attacker.heading=Math.toRadians(30);
        assertFalse(session.fireTorpedoAtTarget(attacker,target,12,0));assertTrue(attacker.canFire(0));
        var precise=session(0,List.of(),false);attacker=shooter(precise);target=get(precise,"target");
        attacker.heading=Math.toRadians(2);
        assertFalse(precise.fireTorpedoAtTarget(attacker,target,12,0));
        attacker.heading=0;assertTrue(precise.fireTorpedoAtTarget(attacker,target,12,0));
    }

    @Test void friendlyShipOnTheActualImperfectCourseStillBlocksTheShot() {
        var session=session(Math.toRadians(5),List.of(),true);var attacker=shooter(session);var target=get(session,"target");
        aim(session);
        Vector2 muzzle=session.torpedoMuzzlePosition(attacker,attacker.heading(),0);
        get(session,"friend").position=muzzle.add(Vector2.fromHeading(attacker.heading()).scale(200));
        assertFalse(session.fireTorpedoAtTarget(attacker,target,12,0));
        assertTrue(session.snapshot().torpedoes().isEmpty());assertTrue(attacker.canFire(0));
    }

    @Test void landOnEitherTheTargetLineOrTheActualImperfectCourseBlocksTheShot() {
        var sample=session(Math.toRadians(5),List.of(),false);aim(sample);var attacker=shooter(sample);
        Vector2 point=sample.torpedoMuzzlePosition(attacker,attacker.heading(),0)
                .add(Vector2.fromHeading(attacker.heading()).scale(300));
        for(Vector2 location:List.of(point,new Vector2(0,300))) {
            var land=new Landmass("island","blocker",location.x(),location.z(),4,4,4,4,4,4,null,1,null,null,null,List.of(),List.of(),List.of());
            var session=session(Math.toRadians(5),List.of(land),false);aim(session);
            assertFalse(session.fireTorpedoAtTarget(shooter(session),get(session,"target"),12,0));
            assertTrue(shooter(session).canFire(0));
        }
    }
}
