package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class FleetStartPositionsTest {
    private List<VehicleSetup> fleet() {
        List<VehicleSetup> ships = new ArrayList<>();
        for (int i = 0; i < 15; i++) ships.add(new VehicleSetup("ship-"+i, "light", new Vector2(i*30, i*10),
                i*.1, "bot", 2+i%5, i%4, i, i==3 ? "scout-plane" : i==2||i==4||i==6 ? "submarine" : "torpedo-boat", i==3 ? 170 : 0));
        return List.copyOf(ships);
    }

    @Test
    void swapsOnlySurfaceVesselPosesWithoutChangingFleetOrAircraft() {
        List<VehicleSetup> original = fleet();
        List<VehicleSetup> shuffled = FleetStartPositions.shuffle(original, new Random(42));
        assertEquals(original.size(), shuffled.size());
        assertSame(original.get(3), shuffled.get(3));
        assertEquals(new HashSet<>(original.stream().map(VehicleSetup::position).toList()),
                new HashSet<>(shuffled.stream().map(VehicleSetup::position).toList()));
        assertNotEquals(original, shuffled);
        for (int i = 0; i < original.size(); i++) {
            VehicleSetup a=original.get(i),b=shuffled.get(i);
            assertEquals(a.id(),b.id());assertEquals(a.teamId(),b.teamId());assertEquals(a.vehicleType(),b.vehicleType());
            assertEquals(a.engineOrder(),b.engineOrder());assertEquals(a.rudderDegrees(),b.rudderDegrees());
            assertEquals(a.nextFireDelaySeconds(),b.nextFireDelaySeconds());assertEquals(a.y(),b.y());
            VehicleSetup oldPose = original.stream().filter(s -> s.position().equals(b.position())).findFirst().orElseThrow();
            assertEquals(oldPose.heading(), b.heading(), "Keep the safe heading attached to each position");
        }
        assertEquals(fleet(), original, "Do not mutate templates");
    }

    @Test
    void submarineSlotsVaryAcrossTheWholeFleetInsteadOfTheSameThreePlaces() {
        Set<Vector2> submarineStarts = new HashSet<>();
        for (int seed = 0; seed < 40; seed++) {
            FleetStartPositions.shuffle(fleet(), new Random(seed)).stream()
                    .filter(s -> s.vehicleType().equals("submarine")).map(VehicleSetup::position).forEach(submarineStarts::add);
        }
        assertEquals(14, submarineStarts.size(), "Every non-aircraft slot is available to submarines");
    }

    @Test
    void actualFreshRoundsVaryFleetTypesAcrossSafePositionsForBothTeams() {
        DefaultGameSetupFactory factory = new DefaultGameSetupFactory(new WorldMapService());
        NavigationService navigation = new NavigationService();
        Map<String,Set<Vector2>> submarineStarts = new HashMap<>();
        Set<String> arrangements = new HashSet<>();
        for (int round = 0; round < 20; round++) {
            GameSetup setup = factory.defaultSetup();
            arrangements.add(setup.fleets().toString());
            Set<Vector2> occupied = new HashSet<>();
            for (FleetSetup fleet : setup.fleets()) {
                assertEquals(3, fleet.ships().stream().filter(s -> s.vehicleType().equals("submarine")).count());
                assertEquals(1, fleet.ships().stream().filter(s -> s.vehicleType().equals("scout-plane")).count());
                for (VehicleSetup ship : fleet.ships()) {
                    assertTrue(occupied.add(ship.position()), "No duplicate spawn positions");
                    assertFalse(navigation.isShipBlocked(ship.position(), ship.heading(), setup.worldMap()), ship.toString());
                    if (ship.vehicleType().equals("submarine")) submarineStarts.computeIfAbsent(fleet.teamId(), ignored -> new HashSet<>()).add(ship.position());
                }
            }
        }
        assertEquals(20, arrangements.size());
        assertTrue(submarineStarts.values().stream().allMatch(positions -> positions.size() >= 10));
    }

    @Test
    void explicitDuelAndScriptedScenesStayReproducible() {
        DefaultGameSetupFactory factory = new DefaultGameSetupFactory(new WorldMapService());
        assertEquals(factory.setup("two-ship-duel"),factory.setup("two-ship-duel"));
        assertEquals(factory.setup("side-view-sandbox"),factory.setup("side-view-sandbox"));
    }
}
