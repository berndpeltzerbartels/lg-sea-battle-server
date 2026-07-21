package one.xis.seabattle;

public record FlakProjectileSnapshot(
        String id,
        String teamId,
        String shipId,
        double x,
        double y,
        double z,
        double vx,
        double vy,
        double vz,
        double firedAt
) {
}
