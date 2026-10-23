package one.xis.seabattle.game;

import one.xis.http.SseConnectionHub;
import one.xis.http.SseEmitter;
import one.xis.seabattle.webapp.account.Account;
import one.xis.seabattle.webapp.game.GameService;
import one.xis.seabattle.webapp.playsession.PlaySessionService;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import static org.junit.jupiter.api.Assertions.*;

class CrewInactivityTest {
    @Test void expiryReleasesGunnerAndRejectsOldCommands() { checkExpiry(false); }
    @Test void expiryOfCaptainPreservesShipAndFreesBridge() { checkExpiry(true); }

    private void checkExpiry(boolean captainLeaves) {
        var game = new GameStateService(new DefaultGameSetupFactory(new WorldMapService()), new RadarService(), new NavigationService());
        var players = new SeaBattlePlayerRegistry();
        var crew = new CrewService(game, players);
        String captain = crew.startOwnShip(new Account("CAP", "Captain", "CAP", "light", null), "torpedo-boat");
        var gun = crew.join(captain, new Account("GUN", "Gunner", "GUN", "light", null));
        String gunner = gun.members().stream().filter(m -> !m.playerId().equals(captain)).findFirst().orElseThrow().playerId();
        String leaving = captainLeaves ? captain : gunner;
        String staying = captainLeaves ? gunner : captain;
        var before = crew.view(leaving);
        var sessions = new PlaySessionService() {
            public void beginSession(String p, String a, String g, String alias, String t) {}
            public void endSession(String p, int score) {}
            public void endActiveSessionsForGame(String g, LocalDateTime time) {}
            public boolean isAliasActiveForOtherAccount(String g, String a, String id) { return false; }
        };
        var endedGames = new java.util.concurrent.atomic.AtomicInteger();
        var games = (GameService) java.lang.reflect.Proxy.newProxyInstance(GameService.class.getClassLoader(),
                new Class<?>[] { GameService.class }, (proxy, method, args) -> {
                    if (method.getName().equals("endActiveGame")) { endedGames.incrementAndGet(); return null; }
                    return "test";
                });
        var events = new SeaBattleEventService(new SseConnectionHub(), game, players, sessions, games, crew);
        var messages = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        var emitter = new SseEmitter() {
            public CompletionStage<Void> send(String data) { messages.add(data); return CompletableFuture.completedFuture(null); }
            public void close() { closed.set(true); }
            public boolean isOpen() { return !closed.get(); }
        };
        events.register(leaving, emitter);
        events.expireInactivePlayers(System.currentTimeMillis());
        assertTrue(players.isRegisteredPlayer(leaving));
        events.expireInactivePlayers(System.currentTimeMillis() + PlayerActivity.IDLE_MILLIS);
        assertTrue(messages.stream().anyMatch(m -> m.contains("session-expired") && m.contains("inactivity")));
        assertFalse(players.isRegisteredPlayer(leaving));
        assertFalse(events.recordActivity(leaving));
        events.register(leaving, emitter);
        assertTrue(closed.get());
        var survivor = crew.view(staying);
        assertEquals(before.shipId(), survivor.shipId());
        assertEquals(1, survivor.members().size());
        assertEquals(staying, survivor.controller());
        assertThrows(IllegalArgumentException.class, () -> crew.switchStation(new CrewService.Command(
                leaving, before.shipId(), before.revision(), before.station(), null, null, null)));
        assertEquals(before.station(), crew.switchStation(new CrewService.Command(
                staying, survivor.shipId(), survivor.revision(), before.station(), null, null, null)).station());
        assertEquals(0, endedGames.get());
        assertEquals("running", game.snapshot().state());
        events.unregisterPlayer(staying);
        assertEquals(1, endedGames.get());
        assertEquals("finished", game.snapshot().state());
        double stoppedAt = game.snapshot().t();
        assertEquals(stoppedAt, game.tick(60).t());
        String next = crew.startOwnShip(new Account("NEW", "New Captain", "NEW", "light", null), "torpedo-boat");
        assertEquals("running", game.snapshot().state());
        assertEquals(0, game.snapshot().t());
        assertTrue(players.isRegisteredPlayer(next));
    }
}
