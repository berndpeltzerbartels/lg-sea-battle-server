package one.xis.seabattle;

public record BombSnapshot(
        String id,
        String teamId,
        String shipId,
        double x,
        double y,
        double z,
        double heading,
        double speed,
        String state,
        double droppedAt
) {
}
