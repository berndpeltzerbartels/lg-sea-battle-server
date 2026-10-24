package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import one.xis.seabattle.webapp.account.Account;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TwoShipDuelSelectionTest {
    private GameSession duel() {
        return new GameSession(new DefaultGameSetupFactory(new WorldMapService()).setup("two-ship-duel"));
    }

    @Test void allBoatAndSubmarineCombinationsKeepExactlyTwoOriginalSlots() {
        for (String light : List.of("torpedo-boat", "submarine")) {
            for (String dark : List.of("torpedo-boat", "submarine")) {
                var session = duel();
                var initial = session.snapshot().ships();
                for (int swap = 0; swap < 4; swap++) {
                    session.restorePlayer("player-L-test", "light", light, initial.get(1), 0);
                    session.restorePlayer("player-D-test", "dark", dark, initial.get(0), 0);
                    var ships = session.snapshot().ships();
                    assertEquals(2, ships.size());
                    assertEquals(initial.stream().map(ShipSnapshot::id).toList(), ships.stream().map(ShipSnapshot::id).toList());
                    assertEquals(light, ships.stream().filter(s -> s.teamId().equals("light")).findFirst().orElseThrow().vehicleType());
                    assertEquals(dark, ships.stream().filter(s -> s.teamId().equals("dark")).findFirst().orElseThrow().vehicleType());
                    session.releasePlayer("player-L-test");
                    session.releasePlayer("player-D-test");
                }
            }
        }
    }

    @Test void occupiedSlotCannotBeTakenOverOrExpandedByLateCommands() {
        var session = duel();
        session.assignPlayerVehicle("player-L-test", "light", "submarine");
        assertThrows(IllegalArgumentException.class, () -> session.restorePlayer("player-X-test", "light", "torpedo-boat", null, 0));
        assertThrows(IllegalArgumentException.class, () -> session.assignPlayerVehicle("player-X-test", "light", "torpedo-boat"));
        assertThrows(IllegalArgumentException.class, () -> session.applyPlayerState(
                new PlayerStateUpdate("player-X-test", "light", 0, 0, 0, 0, 0, 2, 0, 0, false),
                new NavigationService(), session.worldMap()));
        assertThrows(IllegalArgumentException.class, () -> session.applyFireTorpedo(new FireTorpedoRequest("player-X-test", "light")));
        assertEquals(2, session.snapshot().ships().size());
        var light = session.snapshot().ships().stream().filter(s -> s.teamId().equals("light")).findFirst().orElseThrow();
        assertEquals("player-L-test", light.controlledBy());
        assertEquals("submarine", light.vehicleType());
    }

    @Test void crewVehicleChangesUseExistingSlotsAndRejectSameColorWithoutReservingAlias() {
        var game = new GameStateService(new DefaultGameSetupFactory(new WorldMapService()), new RadarService(), new NavigationService());
        game.resetToSetup("two-ship-duel");
        var registry = new SeaBattlePlayerRegistry();
        var crew = new CrewService(game, registry);
        var a = new Account("light-account", "Light", "L", "light", null);
        var b = new Account("dark-account", "Dark", "D", "dark", null);
        for (String vehicle : List.of("submarine", "torpedo-boat", "submarine")) {
            crew.startOwnShip(a, vehicle);
            crew.startOwnShip(b, "submarine");
            assertEquals(2, game.snapshot().ships().size());
        }
        assertThrows(IllegalArgumentException.class, () -> crew.startOwnShip(new Account("extra", "Extra", "X", "light", null), "submarine"));
        assertFalse(registry.isAliasRegistered("X"));
        assertEquals(2, game.snapshot().ships().size());
    }
}
