package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ScenarioScriptParserTest {

    @Test
    void createsScenarioWorldFromReadableMap() {
        GameSetup setup = ScenarioScriptParser.parse("""
                scenario: readable-test
                version: 9200
                cell: 100
                map:
                .....
                .1.2.
                .....
                objects:
                1: ship, light, human [orientation: 90°, speed: 0knt]
                2: plane, dark, bot [orientation: 270°, speed: 30knt, height: 120m]
                """);

        assertEquals("readable-test", setup.id());
        assertEquals(9200, setup.worldMap().version());
        assertEquals(2, setup.fleets().size());
        assertEquals(List.of("light", "dark"), setup.fleets().stream().map(FleetSetup::teamId).toList());

        ShipSetup boat = setup.fleets().get(0).ships().get(0);
        assertEquals("light-S1", boat.id());
        assertEquals("player-scenario-1", boat.controlledBy());
        assertEquals("torpedo-boat", boat.vehicleType());
        assertEquals(-100, boat.position().x(), 0.001);
        assertEquals(0, boat.position().z(), 0.001);
        assertEquals(Math.PI / 2, boat.heading(), 0.001);
        assertEquals(0, boat.y(), 0.001);

        ShipSetup plane = setup.fleets().get(1).ships().get(0);
        assertEquals("dark-F2", plane.id());
        assertEquals("bot", plane.controlledBy());
        assertEquals("scout-plane", plane.vehicleType());
        assertEquals(120, plane.y(), 0.001);
        assertEquals(7, plane.engineOrder());
    }

    @Test
    void createsSimpleLandmassesFromStarCells() {
        GameSetup setup = ScenarioScriptParser.parse("""
                scenario: basin-test
                cell: 50
                map:
                *****
                *1.2*
                *****
                objects:
                1: ship light human
                2: ship dark bot
                """);

        assertEquals(12, setup.worldMap().landmasses().size());
    }

    @Test
    void parsesCannonActionWithAimPointForRealScenarioVehicle() {
        ScenarioDefinition scenario = ScenarioScriptParser.parseDefinition("""
                scenario: cannon-action-test
                cell: 20
                map:
                .....
                .1.2.
                .....
                objects:
                1: ship light human [orientation: 90°, speed: 0knt]
                2: ship dark bot [orientation: 270°, speed: 0knt]
                actions:
                1: cannon at [x: 20, y: 0.42, z: 0]
                """);

        assertEquals("cannon-action-test", scenario.setup().id());
        assertEquals(1, scenario.actions().size());

        ScenarioAction action = scenario.actions().get(0);
        assertEquals('1', action.actorMarker());
        assertEquals("cannon", action.weapon());
        assertEquals(20, action.target().x(), 0.001);
        assertEquals(0.42, action.target().y(), 0.001);
        assertEquals(0, action.target().z(), 0.001);
    }

    @Test
    void rejectsObjectWithoutMapMarker() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> ScenarioScriptParser.parse("""
                scenario: missing-marker
                map:
                .1.
                objects:
                2: ship dark bot
                """));

        assertEquals("Scenario object marker not found on map: 2", exception.getMessage());
    }
}
