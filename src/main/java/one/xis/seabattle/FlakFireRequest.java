package one.xis.seabattle;

public record FlakFireRequest(
        String playerId,
        String teamId,
        String shipId,
        double x,
        double y,
        double z,
        double vx,
        double vy,
        double vz
) {
}
