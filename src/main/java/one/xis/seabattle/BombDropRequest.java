package one.xis.seabattle;

public record BombDropRequest(
        String playerId,
        String teamId,
        double x,
        double y,
        double z,
        double heading,
        double speed,
        double turnVelocity,
        double verticalSpeed,
        String vehicleType
) {
    public BombDropRequest(String playerId, String teamId, double x, double y, double z, double heading,
                           double speed, double verticalSpeed, String vehicleType) {
        this(playerId, teamId, x, y, z, heading, speed, 0, verticalSpeed, vehicleType);
    }
}
