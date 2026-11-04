package one.xis.seabattle.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class VehicleHierarchyTest {
  private Vehicle create(String type) {
    return Vehicle.create(type, "vehicle", "light", new Vector2(10, 20), .5, "captain");
  }

  @Test
  void factoryCreatesRealTypesWithOnlyTheirOwnSpecialistState() {
    assertInstanceOf(TorpedoBoat.class, create("torpedo-boat"));
    assertInstanceOf(Submarine.class, create("submarine"));
    assertInstanceOf(ScoutPlane.class, create("scout-plane"));
    assertEquals(Set.of("depth", "submarineBot"), fields(Submarine.class));
    assertEquals(Set.of("cannonYaw", "cannonPitch", "submarineSearch"), fields(TorpedoBoat.class));
    assertEquals(Set.of("verticalSpeed", "targetY"), fields(ScoutPlane.class));
    assertFalse(fields(Vehicle.class).contains("vehicleType"));
    assertFalse(SurfaceVehicle.class.isAssignableFrom(ScoutPlane.class));
  }

  private Set<String> fields(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
        .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
        .map(java.lang.reflect.Field::getName)
        .collect(Collectors.toSet());
  }

  @Test
  void fleetTypeReplacementPreservesIdentityAndInvalidatesOldTargets() {
    Vehicle old = create("torpedo-boat");
    old.sink(0);
    old.respawn(new Vector2(30, 40), 1, 0);
    old.controlledBy("captain");
    old.applyCommand(8, 35);
    Fleet fleet = new Fleet("light", List.of(old));
    Vehicle replacement = fleet.changeVehicleType(old, "submarine");
    assertInstanceOf(Submarine.class, replacement);
    assertEquals(old.id(), replacement.id());
    assertEquals(old.lifeId(), replacement.lifeId());
    assertEquals(old.position(), replacement.position());
    assertEquals("captain", replacement.controlledBy());
    assertEquals(8, replacement.snapshot().engineOrder());
    assertEquals(35, replacement.snapshot().rudderDegrees());
    assertEquals("replaced", old.state());
    assertEquals(List.of(replacement), fleet.ships());
    assertSame(replacement, fleet.changeVehicleType(replacement, "submarine"));
  }

  @Test
  void commandLimitsAndRespawnRespectTheConcreteType() {
    Vehicle boat = create("torpedo-boat"), sub = create("submarine"), plane = create("scout-plane");
    for (Vehicle vehicle : List.of(boat, sub, plane)) vehicle.applyCommand(99, 99);
    assertEquals(35, boat.snapshot().rudderDegrees());
    assertEquals(45, sub.snapshot().rudderDegrees());
    assertEquals(35, plane.snapshot().rudderDegrees());
    sub.controlledBy("bot");
    sub.botDepth("submerged");
    sub.sink(0);
    sub.respawn(new Vector2(0, 0), 0, 1);
    assertEquals("surface", sub.depthState());
    plane.sink(0);
    plane.respawn(new Vector2(0, 0), 0, 1);
    assertEquals(ScoutPlane.CRUISE_Y, plane.y());
    assertEquals(ScoutPlane.CRUISE_SPEED, plane.speed());
    assertEquals(0, plane.verticalSpeed());
    assertEquals(7, plane.snapshot().engineOrder());
  }

  @Test
  void playerUpdateCannotChangeTheRuntimeTypeInPlace() {
    Vehicle boat = create("torpedo-boat");
    PlayerStateUpdate update =
        new PlayerStateUpdate("captain", "light", 10, 20, .5, 0, 0, 2, 0, 0, false, "submarine");
    assertThrows(
        IllegalArgumentException.class,
        () -> boat.applyPlayerState(update, new NavigationService(), new WorldMap(1, List.of())));
    assertInstanceOf(TorpedoBoat.class, boat);
  }
}
