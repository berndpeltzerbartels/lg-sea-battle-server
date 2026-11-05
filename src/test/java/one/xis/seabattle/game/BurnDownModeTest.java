package one.xis.seabattle.game;

import one.xis.seabattle.webapp.account.Account;
import one.xis.seabattle.webapp.gameconfig.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class BurnDownModeTest {
    static GameSetup setup() {
        return new GameSetup("burn-down-test", new WorldMap(1,List.of()), List.of(
                new FleetSetup("light", List.of(vehicle("L1","light","torpedo-boat",-900), vehicle("L2","light","torpedo-boat",-600), vehicle("LS","light","submarine",-300))),
                new FleetSetup("dark", List.of(vehicle("D1","dark","torpedo-boat",900), vehicle("DS","dark","submarine",600)))), List.of(new Vector2(0,1000)));
    }
    static VehicleSetup vehicle(String id,String team,String type,double x) {
        return new VehicleSetup(id,team,new Vector2(x,0),0,"bot",2,0,0,type,0);
    }
    static List<Vehicle> vehicles(GameSession session) throws Exception {
        var method=GameSession.class.getDeclaredMethod("allShips"); method.setAccessible(true);
        return (List<Vehicle>)method.invoke(session);
    }
    static void sink(GameSession session,String id) throws Exception { vehicles(session).stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow().sink(0); }
    static void tick(GameSession session) { session.updateIdle(.1,new RadarService(),new NavigationService(),session.worldMap()); }
    static GameSession session(Map<String,Integer> humans) {
        var session=new GameSession(setup()); session.configureRound(GameMode.BURN_DOWN,()->humans); return session;
    }
    @Test void capacityUsesAllFourSeatsIncludingOccupiedOnes() {
        assertFalse(GameMode.BURN_DOWN.defeated(1,4));
        assertTrue(GameMode.BURN_DOWN.defeated(1,5));
        assertTrue(GameMode.BURN_DOWN.defeated(0,0));
        assertFalse(GameMode.BURN_DOWN.defeated(1,0));
        assertFalse(GameMode.ENDLESS.defeated(0,10));
    }
    @Test void noReplacementsEvenAfterTheWreckHasDisappeared() throws Exception {
        var session=session(Map.of()); sink(session,"L1");
        for(int i=0;i<800;i++) tick(session);
        assertEquals("running",session.snapshot().state());
        assertEquals(1,session.snapshot().ships().stream().filter(v -> v.teamId().equals("light") && v.vehicleType().equals("torpedo-boat") && v.state().equals("active")).count());
        assertEquals(5,vehicles(session).size());
    }
    @Test void lastTorpedoBoatLosesDespiteSurvivingSubmarine() throws Exception {
        var session=session(Map.of()); sink(session,"D1"); tick(session);
        assertEquals("finished",session.snapshot().state()); assertEquals("light",session.snapshot().winningTeam());
        assertEquals("active",vehicles(session).stream().filter(v -> v.id().equals("DS")).findFirst().orElseThrow().state());
        double end=session.snapshot().t(); tick(session); assertEquals(end,session.snapshot().t());
    }
    @Test void humanSubmarinePlayersCountTowardCapacity() throws Exception {
        var session=session(Map.of("light",5)); sink(session,"L1"); tick(session);
        assertEquals("dark",session.snapshot().winningTeam());
    }
    @Test void simultaneousLossIsADraw() throws Exception {
        var session=session(Map.of()); sink(session,"L1"); sink(session,"L2"); sink(session,"D1"); tick(session);
        assertEquals("finished",session.snapshot().state()); assertNull(session.snapshot().winningTeam());
    }
    @Test void finiteReentryUsesExistingTypeThenAnExistingTorpedoBoat() throws Exception {
        var session=session(Map.of("light",1));
        assertEquals("LS",session.reenter("player-A-test","light","submarine").orElseThrow().id());
        sink(session,"LS");
        assertEquals("torpedo-boat",session.reenter("player-A-test","light",null).orElseThrow().vehicleType());
        assertEquals(5,vehicles(session).size());
    }
    @Test void noVehicleConversionOrTeleportByAnOldClientPacket() throws Exception {
        var session=session(Map.of()); session.assignPlayerVehicle("player-A-test","light","torpedo-boat");
        session.applyPlayerState(new PlayerStateUpdate("player-A-test","light",0,0,0,0,0,2,0,0,true,"submarine"),new NavigationService(),session.worldMap());
        var assigned=session.snapshot().ships().stream().filter(v -> "player-A-test".equals(v.controlledBy())).findFirst().orElseThrow();
        assertEquals("torpedo-boat",assigned.vehicleType()); assertEquals(-900,assigned.x());
    }
    @Test void initialConfiguredFleetIsFixedForTheRound() {
        var original=setup();
        var config=new AtomicReference<>(new GameRuntimeConfig(TorpedoBoatBotConfig.defaults(),new FleetSizeConfig(2,1)));
        var session=new GameSession(new GameSetup("islands",original.worldMap(),original.fleets(),original.respawnCandidates()),config::get);
        session.configureRound(GameMode.BURN_DOWN,Map::of);
        assertEquals(3,session.snapshot().ships().size());
        config.set(new GameRuntimeConfig(TorpedoBoatBotConfig.defaults(),new FleetSizeConfig(20,20)));
        session.updateIdle(30,new RadarService(),new NavigationService(),session.worldMap());
        assertEquals(3,session.snapshot().ships().size());
    }
    @Test void endlessStillReplacesDestroyedBoats() throws Exception {
        var session=new GameSession(setup()); sink(session,"L1"); tick(session);
        assertEquals("active",vehicles(session).stream().filter(v -> v.id().equals("L1")).findFirst().orElseThrow().state());
        assertEquals("running",session.snapshot().state());
    }

    static class Fixture {
        final SeaBattlePlayerRegistry players=new SeaBattlePlayerRegistry();
        final GameStateService game=new GameStateService(new DefaultGameSetupFactory(new WorldMapService()),new RadarService(),new NavigationService(),TestGameConfigService.defaults());
        final CrewService crew=new CrewService(game,players);
        final GameSession session;
        Fixture() throws Exception {
            session=BurnDownModeTest.session(players.teamCounts());
            // Test fixture injects a tiny fixed round, with no connected clients or app started.
            session.configureRound(GameMode.BURN_DOWN,players::teamCounts);
            var field=GameStateService.class.getDeclaredField("session");field.setAccessible(true);field.set(game,session);
            field=GameStateService.class.getDeclaredField("mode");field.setAccessible(true);field.set(game,GameMode.BURN_DOWN);
            publish();
        }
        void publish(){game.restorePersonalScore("test",0);}
        String start(String alias,String type){return crew.startOwnShip(new Account(alias,alias,alias,"light",null),type);}
        void sink(String id)throws Exception{BurnDownModeTest.sink(session,id);publish();}
    }
    @Test void deadSubmarineDriverCanJoinAnOccupiedBoat() throws Exception {
        var f=new Fixture();
        var one=f.start("ONE","torpedo-boat"); var two=f.start("TWO","torpedo-boat"); var sub=f.start("SUB","submarine");
        f.crew.view(one);f.crew.view(two); f.sink("LS");
        var view=f.crew.view(sub);
        assertNotNull(view); assertEquals(2,view.members().size()); assertNotEquals("bridge",view.station());
        assertEquals("torpedo-boat",view.ship().vehicleType()); assertEquals(5,vehicles(f.session).size());
    }
    @Test void crewGroupMovesTogetherToAnExistingBoat() throws Exception {
        var f=new Fixture();var captain=f.start("CAP","torpedo-boat");
        f.crew.view(captain);var gunner=f.crew.join(captain,new Account("GUN","GUN","GUN","light",null));
        f.sink("L1");
        var after=f.crew.view(captain);
        assertEquals("L2",after.shipId());assertEquals(2,after.members().size());
        assertEquals(after.id(),f.crew.view(gunner.members().stream().filter(m->!m.playerId().equals(captain)).findFirst().orElseThrow().playerId()).id());
    }
    @Test void lateJoinCannotCauseDefeat() throws Exception {
        var f=new Fixture(); f.sink("L2");
        String captain=f.start("A","torpedo-boat");f.crew.view(captain);
        for (String alias:List.of("B","C","D")) f.crew.join(captain,new Account(alias,alias,alias,"light",null));
        assertThrows(IllegalArgumentException.class,()->f.start("E","submarine"));
        tick(f.session); assertEquals("running",f.session.snapshot().state());assertEquals(4,f.players.teamCounts().get("light"));
    }
    @Test void joiningAndLeavingCannotManufactureFreshHulls() throws Exception {
        var f=new Fixture();
        String first=f.start("A","submarine");
        f.start("A","torpedo-boat");
        assertEquals(5,vehicles(f.session).size());
        assertFalse(f.players.isRegisteredPlayer(first));
        assertEquals("bot",vehicles(f.session).stream().filter(v -> v.id().equals("LS")).findFirst().orElseThrow().controlledBy());
    }
}
