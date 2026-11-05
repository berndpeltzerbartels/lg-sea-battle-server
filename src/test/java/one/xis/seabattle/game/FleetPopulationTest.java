package one.xis.seabattle.game;

import one.xis.seabattle.webapp.gameconfig.FleetSizeConfig;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FleetPopulationTest {
    private Vehicle vehicle(String id, String team, String type) {
        return Vehicle.create(type, id, team, new Vector2(0, 0), 0, "bot");
    }

    @Test void increasesAreSharedAcrossColoursAndTypesWithNoCatchUpBurst() {
        var light = new Fleet("light", List.of(vehicle("L", "light", "submarine")));
        var dark = new Fleet("dark", List.of(vehicle("D", "dark", "scout-plane")));
        var fleets = Map.of("light", light, "dark", dark);
        var population = new FleetPopulation();
        population.configure(new FleetSizeConfig(4, 4), 0);
        var added = new ArrayList<Vehicle>();
        java.util.function.BiPredicate<Fleet, Vehicle> place = (fleet, v) -> { added.add(v); return true; };
        population.increase(fleets, 2.99, place); assertTrue(added.isEmpty());
        population.increase(fleets, 3, place); assertEquals(1, added.size());
        assertTrue(added.get(0).isSubmarine());
        population.increase(fleets, 5.99, place); assertEquals(1, added.size());
        population.increase(fleets, 6, place); assertEquals(2, added.size());
        assertTrue(added.get(1).isScoutPlane());
        population.increase(fleets, 60, place); assertEquals(3, added.size());
        population.increase(fleets, 60, place); assertEquals(3, added.size());
        assertEquals("light", added.get(2).teamId());
    }

    @Test void reductionKeepsActiveHumansAndWithheldSlotsReturnOnlyAtTheSlowCadence() {
        Vehicle survivor = vehicle("player", "light", "torpedo-boat"); survivor.controlledBy("bernd");
        Vehicle lost = vehicle("lost", "light", "scout-plane");
        var light = new Fleet("light", List.of(survivor, lost));
        var population = new FleetPopulation();
        population.configure(new FleetSizeConfig(1, 0), 0);
        assertEquals(2, light.activeShips().size());
        lost.sink(0);
        assertFalse(population.mayReplace(lost, light));
        assertEquals("bernd", survivor.controlledBy());
        population.configure(new FleetSizeConfig(2, 0), 10);
        assertFalse(population.mayReplace(lost, light));
        var placed = new ArrayList<Vehicle>();
        population.increase(Map.of("light", light), 12.99, (f,v) -> { placed.add(v); return true; });
        assertTrue(placed.isEmpty());
        population.increase(Map.of("light", light), 13, (f,v) -> { v.respawn(new Vector2(100,0),0,13); placed.add(v); return true; });
        assertEquals(List.of(lost), placed);
        assertEquals(2, light.ships().size(), "Reuse the fleet slot instead of accumulating new instances");
        assertTrue(lost.isScoutPlane());
    }

    @Test void ordinaryLossesReserveCapacityAndABlockedIncreaseWaitsForAnotherInterval() {
        var lost = vehicle("lost", "light", "torpedo-boat"); lost.sink(20);
        var fleet = new Fleet("light", List.of(lost));
        var population = new FleetPopulation(); population.configure(new FleetSizeConfig(1,0), 0);
        var calls = new int[1];
        population.increase(Map.of("light", fleet), 3, (f,v) -> { calls[0]++; return true; });
        assertEquals(0, calls[0], "A pending normal replacement is not an increase");
        population.configure(new FleetSizeConfig(2,0), 4);
        population.increase(Map.of("light", fleet), 7, (f,v) -> { calls[0]++; return false; });
        population.increase(Map.of("light", fleet), 7.1, (f,v) -> { calls[0]++; return true; });
        assertEquals(1, calls[0]);
        assertEquals(1, fleet.ships().size());
        population.increase(Map.of("light", fleet), 10, (f,v) -> { calls[0]++; return true; });
        assertEquals(2, fleet.ships().size());
    }
}
