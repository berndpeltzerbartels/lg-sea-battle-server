package one.xis.seabattle.game;

import java.util.List;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetCompositionTest {
    private Vehicle ship(String id, String type) {
        Vehicle ship = new TorpedoBoat(id, "red", new Vector2(0, 0), 0, "bot");
        ship = ship.convertedTo(type);
        return ship;
    }

    @Test void repeatedlyJoiningAndLeavingAsSubmarineDoesNotConvertTorpedoBoats() {
        var ships = List.of(ship("sub", "submarine"), ship("boat-a", "torpedo-boat"), ship("boat-b", "torpedo-boat"));
        Fleet fleet = new Fleet("red", ships);
        for (int join = 0; join < 100; join++) {
            Vehicle assigned = fleet.assignNextShipToPlayer("player", "submarine").orElseThrow();
            assertEquals("sub", assigned.id());
            fleet.releasePlayer("player");
            assertEquals(1, fleet.ships().stream().filter(Vehicle::isSubmarine).count());
            assertEquals(3, fleet.ships().size());
        }
    }

    @Test void deadSubmarineSlotsCountWhenAnExtraConvertedSubmarineRespawns() throws Exception {
        for (boolean originalRespawnsFirst : new boolean[]{false, true}) {
            var session = new GameSession(new GameSetup("composition", new WorldMap(1, List.of()),
                    List.of(new FleetSetup("red", List.of(
                            new VehicleSetup("sub", "red", new Vector2(0, 0), 0, "bot", 2, 0, 999, "submarine", 0),
                            new VehicleSetup("boat", "red", new Vector2(200, 0), 0, "bot", 2, 0, 999, "torpedo-boat", 0)))), List.of()));
            var all = GameSession.class.getDeclaredMethod("allShips"); all.setAccessible(true);
            @SuppressWarnings("unchecked") var ships = (List<Vehicle>) all.invoke(session);
            Vehicle original = ships.get(0), converted = ships.get(1);
            original.sink(100);
            var field = GameSession.class.getDeclaredField("fleets");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") var fleets = (java.util.Map<String, Fleet>) field.get(session);
            converted = fleets.get("red").changeVehicleType(converted, "submarine"); // Former login behaviour or fallback when every submarine is occupied.
            converted.sink(100);
            Method prepare = GameSession.class.getDeclaredMethod("prepareBotVehicleTypeForRespawn", Vehicle.class);
            prepare.setAccessible(true);
            for (Vehicle next : originalRespawnsFirst ? List.of(original, converted) : List.of(converted, original)) {
                next = (Vehicle) prepare.invoke(session, next);
                next.respawn(new Vector2(500, 500), 0, 101);
            }
            assertEquals(1, ((List<Vehicle>) all.invoke(session)).stream().filter(Vehicle::isSubmarine).count(),
                    "Both sinking slots must be counted, irrespective of replacement order");
        }
    }

    @Test void occupiedSubmarineCanTemporarilyRequireAnotherTypeWithoutChangingIntendedFleetCount() {
        Vehicle sub = ship("sub", "submarine"), boat = ship("boat", "torpedo-boat");
        Fleet fleet = new Fleet("red", List.of(sub, boat));
        fleet.assignNextShipToPlayer("first", "submarine");
        Vehicle replacement = fleet.assignNextShipToPlayer("second", "submarine").orElseThrow();
        assertInstanceOf(Submarine.class, replacement);
        assertEquals(boat.id(), replacement.id());
        assertEquals("replaced", boat.state());
        assertTrue(fleet.ships().contains(replacement));
        assertEquals(1, fleet.intendedSubmarineCount());
        assertEquals("first", sub.controlledBy());
        assertEquals("second", replacement.controlledBy());
    }
}
