package one.xis.seabattle.game;

import one.xis.seabattle.webapp.account.Account;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrewServiceTest {
    private final GameStateService game = new GameStateService(new DefaultGameSetupFactory(new WorldMapService()), new RadarService(), new NavigationService());
    private final SeaBattlePlayerRegistry players = new SeaBattlePlayerRegistry();
    private final CrewService crew = new CrewService(game, players);
    private final String captain = "player-CAP-test";

    private CrewService.View start() {
        players.register(captain, "CAP", "Captain", "light", "captain");
        game.assignPlayerVehicle(captain, "light", "torpedo-boat");
        return crew.view(captain);
    }
    private Account account(String alias) { return new Account(alias, alias, alias, "light", null); }
    private ShipSnapshot ship(String id) {
        return game.snapshot().ships().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }
    private CrewService.AlignCommand alignment(String player, String mode) {
        var v = crew.view(player);
        return new CrewService.AlignCommand(player, v.shipId(), v.revision(), mode);
    }

    @Test void flakStartsFacingAftForHumansAndBots() {
        var v = start();
        assertEquals(Math.PI, ship(v.shipId()).flakYaw(), 0.001);
        assertTrue(game.snapshot().ships().stream().anyMatch(s -> "bot".equals(s.controlledBy())));
        game.snapshot().ships().stream().filter(s -> "bot".equals(s.controlledBy()))
                .forEach(s -> assertEquals(Math.PI, s.flakYaw(), 0.001));
    }

    @Test void bridgeAlignmentOnlyAffectsCurrentlyUnoccupiedWeapons() {
        var v = start();
        crew.alignUnoccupiedWeapons(alignment(captain, "air-defense"));
        assertEquals(Math.toRadians(18), ship(v.shipId()).flakPitch(), 0.001);
        assertEquals(Math.toRadians(20), ship(v.shipId()).cannonPitch(), 0.001);
        crew.join(captain, account("GUN"));
        var staleBridgeOrder = alignment(captain, "flat");
        crew.alignUnoccupiedWeapons(staleBridgeOrder);
        assertEquals(Math.toRadians(18), ship(v.shipId()).flakPitch(), 0.001);
        assertEquals(0, ship(v.shipId()).cannonPitch());
        assertThrows(IllegalArgumentException.class, () -> crew.alignUnoccupiedWeapons(alignment(player("GUN"), "flat")));
        crew.switchStation(command(player("GUN"), "cannon"));
        crew.alignUnoccupiedWeapons(alignment(captain, "air-defense"));
        assertEquals(0, ship(v.shipId()).cannonPitch());
        crew.alignUnoccupiedWeapons(staleBridgeOrder);
        assertEquals(0, ship(v.shipId()).flakPitch());
        crew.join(captain, account("TWO"));
        crew.alignUnoccupiedWeapons(alignment(captain, "air-defense"));
        assertEquals(0, ship(v.shipId()).flakPitch());
        assertEquals(0, ship(v.shipId()).cannonPitch());
    }
    private String player(String alias) { return players.activePlayerIdForAccountAlias(alias, alias); }
    private CrewService.Command command(String player, String station) {
        var v = crew.view(player);
        return new CrewService.Command(player, v.shipId(), v.revision(), station, null, null, null);
    }

    @Test void joinSharesShipAndStationsAreExclusive() {
        var first = start();
        int count = game.snapshot().ships().size();
        var second = crew.join(captain, account("GUN"));
        assertEquals(first.shipId(), second.shipId());
        assertEquals("flak", second.station());
        assertEquals(count, game.snapshot().ships().size());
        assertThrows(IllegalArgumentException.class, () -> crew.switchStation(command(captain, "flak")));
        assertEquals("bridge", crew.view(captain).station());
        var stale = command(player("GUN"), "flak");
        crew.switchStation(command(player("GUN"), "cannon"));
        assertThrows(IllegalArgumentException.class, () -> crew.switchStation(stale));
        assertEquals("flak", crew.switchStation(command(captain, "flak")).station());
    }

    @Test void gunnerCannotSteerOrLaunchTorpedoesAndBridgeCannotFireGuns() {
        start();
        crew.join(captain, account("GUN"));
        var gun = command(player("GUN"), "flak");
        assertThrows(IllegalArgumentException.class, () -> crew.motion(gun));
        assertThrows(IllegalArgumentException.class, () -> crew.fire(gun, "torpedo"));
        assertThrows(IllegalArgumentException.class, () -> crew.fire(command(captain, "bridge"), "cannon"));
        assertDoesNotThrow(() -> crew.fire(command(captain, "bridge"), "torpedo"));
    }

    @Test void captainDeparturePreservesCrewAndAllowsBridgeTakeover() {
        var first = start();
        crew.join(captain, account("GUN"));
        crew.leave(captain);
        players.unregisterPlayer(captain);
        game.releasePlayer(captain);
        var survivor = crew.view(player("GUN"));
        assertEquals(first.id(), survivor.id());
        assertEquals(first.shipId(), survivor.shipId());
        assertEquals("flak", survivor.station());
        assertEquals(player("GUN"), survivor.controller());
        assertEquals("bridge", crew.switchStation(command(player("GUN"), "bridge")).station());
        crew.leave(player("GUN"));
        assertFalse(crew.exists(first.id()));
    }

    @Test void fullAndEnemyCrewsAreRejected() {
        start();
        assertThrows(IllegalArgumentException.class, () -> crew.join(captain, new Account("E", "Enemy", "E", "dark", null)));
        crew.join(captain, account("ONE"));
        crew.join(captain, account("TWO"));
        assertThrows(IllegalArgumentException.class, () -> crew.join(captain, account("THREE")));
    }

    @Test void gunfireIdentifiesActualGunnerNotBridgeController() {
        var v = start();
        crew.join(captain, account("GUN"));
        String gunner = player("GUN");
        var ship = game.snapshot().ships().stream().filter(s -> s.id().equals(v.shipId())).findFirst().orElseThrow();
        for (String station : java.util.List.of("flak", "cannon")) {
            crew.switchStation(command(gunner, station));
            var cmd = command(gunner, station);
            var shot = new FlakFireRequest(captain, "dark", "forged", ship.x(), 10, ship.z(), 0, 20, 100, 0, 0.2);
            var state = crew.fire(new CrewService.Command(gunner, cmd.shipId(), cmd.revision(), station, null, shot, null), station);
            var projectile = state.flakProjectiles().stream().filter(p -> p.id().startsWith(station + "-")).findFirst().orElseThrow();
            assertEquals(gunner, projectile.shooterPlayerId());
            assertEquals(v.shipId(), projectile.shipId());
        }
    }

    @Test void aimCannotChangeMotionAndBridgeCannotOverwriteOtherWeapons() {
        var v = start();
        crew.join(captain, account("GUN"));
        var before = game.snapshot().ships().stream().filter(s -> s.id().equals(v.shipId())).findFirst().orElseThrow();
        var gun = command(player("GUN"), "flak");
        var forged = new PlayerStateUpdate(player("GUN"), "dark", before.x() + 100, before.z(), 2, 20,
                1, 8, 35, 1, true, "scout-plane", 100, 50, 0.3, 0.4, 2.0, 1.0);
        crew.aim(new CrewService.Command(gun.playerId(), gun.shipId(), gun.revision(), "flak", forged, null, null));
        var aimed = game.snapshot().ships().stream().filter(s -> s.id().equals(v.shipId())).findFirst().orElseThrow();
        assertEquals(before.x(), aimed.x());
        assertEquals(before.engineOrder(), aimed.engineOrder());
        assertEquals(before.cannonYaw(), aimed.cannonYaw());
        assertEquals(0.3, aimed.flakYaw());
        assertEquals("torpedo-boat", aimed.vehicleType());
        var motion = new PlayerStateUpdate(captain, "light", before.x(), before.z(), before.heading(), 0,
                0, 5, 0, 1, false, "torpedo-boat", 0, 0, 1.0, 1.0, 1.0, 1.0);
        crew.motion(new CrewService.Command(captain, v.shipId(), v.revision(), "bridge", motion, null, null));
        var after = game.snapshot().ships().stream().filter(s -> s.id().equals(v.shipId())).findFirst().orElseThrow();
        assertEquals(0.3, after.flakYaw());
        assertEquals(5, after.engineOrder());
        crew.switchStation(command(captain, "cannon"));
        assertEquals(5, game.snapshot().ships().stream().filter(s -> s.id().equals(v.shipId())).findFirst().orElseThrow().engineOrder());
    }

    @Test void simultaneousClaimsHaveExactlyOneWinner() throws Exception {
        start();
        crew.join(captain, account("GUN"));
        var first = command(captain, "cannon");
        var second = command(player("GUN"), "cannon");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks = java.util.List.<java.util.concurrent.Callable<Boolean>>of(
                    () -> claim(first), () -> claim(second));
            int won = 0;
            for (var result : pool.invokeAll(tasks)) if (result.get()) won++;
            assertEquals(1, won);
        } finally { pool.shutdownNow(); }
    }

    private boolean claim(CrewService.Command command) {
        try { crew.switchStation(command); return true; }
        catch (IllegalArgumentException rejected) { return false; }
    }

    @Test void activatingOpposingTeamPreservesMembershipAndInvalidatesOldCommands() {
        var before = start();
        crew.join(captain, account("GUN"));
        var stale = command(captain, "cannon");
        game.activateTeam("dark");
        var after = crew.view(player("GUN"));
        assertEquals(before.id(), after.id());
        assertEquals(2, after.members().size());
        assertEquals("flak", after.station());
        assertThrows(IllegalArgumentException.class, () -> crew.switchStation(stale));
        assertEquals(1, game.snapshot().ships().stream().filter(s -> captain.equals(s.controlledBy())).count());
    }
}
