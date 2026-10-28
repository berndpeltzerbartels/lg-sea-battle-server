package one.xis.seabattle.game;

// A frozen death event, independent of the reusable active fleet slot.
public record ShipWreckSnapshot(String id, double sunkAt, ShipSnapshot ship) {
}
