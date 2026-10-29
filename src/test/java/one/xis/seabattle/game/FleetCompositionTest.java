package one.xis.seabattle.game;

import java.util.List;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetCompositionTest {
    private Ship ship(String id, String type) {
        Ship ship = new Ship(id, "red", new Vector2(0, 0), 0, "bot");
        ship.vehicleType(type);
        return ship;
    }

    @Test void repeatedlyJoiningAndLeavingAsSubmarineDoesNotConvertTorpedoBoats() {
        var ships = List.of(ship("sub", "submarine"), ship("boat-a", "torpedo-boat"), ship("boat-b", "torpedo-boat"));
        Fleet fleet = new Fleet("red", ships);
        for (int join = 0; join < 100; join++) {
            Ship assigned = fleet.assignNextShipToPlayer("player", "submarine").orElseThrow();
            assertEquals("sub", assigned.id());
            fleet.releasePlayer("player");
            assertEquals(1, fleet.ships().stream().filter(Ship::isSubmarine).count());
            assertEquals(3, fleet.ships().size());
        }
    }

    @Test void deadSubmarineSlotsCountWhenAnExtraConvertedSubmarineRespawns() throws Exception {
        for (boolean originalRespawnsFirst : new boolean[]{false, true}) {
            var session = new GameSession(new GameSetup("composition", new WorldMap(1, List.of()),
                    List.of(new FleetSetup("red", List.of(
                            new ShipSetup("sub", "red", new Vector2(0, 0), 0, "bot", 2, 0, 999, "submarine", 0),
                            new ShipSetup("boat", "red", new Vector2(200, 0), 0, "bot", 2, 0, 999, "torpedo-boat", 0)))), List.of()));
            var all = GameSession.class.getDeclaredMethod("allShips"); all.setAccessible(true);
            @SuppressWarnings("unchecked") var ships = (List<Ship>) all.invoke(session);
            Ship original = ships.get(0), converted = ships.get(1);
            original.sink(100);
            converted.vehicleType("submarine"); // Former login behaviour or fallback when every submarine is occupied.
            converted.sink(100);
            Method prepare = GameSession.class.getDeclaredMethod("prepareBotVehicleTypeForRespawn", Ship.class);
            prepare.setAccessible(true);
            for (Ship next : originalRespawnsFirst ? List.of(original, converted) : List.of(converted, original)) {
                prepare.invoke(session, next);
                next.respawn(new Vector2(500, 500), 0, 101);
            }
            assertEquals(1, ships.stream().filter(Ship::isSubmarine).count(),
                    "Both sinking slots must be counted, irrespective of replacement order");
        }
    }

    @Test void occupiedSubmarineCanTemporarilyRequireAnotherTypeWithoutChangingIntendedFleetCount() {
        Ship sub = ship("sub", "submarine"), boat = ship("boat", "torpedo-boat");
        Fleet fleet = new Fleet("red", List.of(sub, boat));
        fleet.assignNextShipToPlayer("first", "submarine");
        assertSame(boat, fleet.assignNextShipToPlayer("second", "submarine").orElseThrow());
        assertEquals(1, fleet.intendedSubmarineCount());
        assertEquals("first", sub.controlledBy());
        assertEquals("second", boat.controlledBy());
    }
}
