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
        double vz,
        double weaponYaw,
        double weaponPitch
) {
    public FlakFireRequest(String playerId, String teamId, String shipId,
                           double x, double y, double z,
                           double vx, double vy, double vz) {
        this(playerId, teamId, shipId, x, y, z, vx, vy, vz, 0, 0);
    }
}
