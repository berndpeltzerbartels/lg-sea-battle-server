package one.xis.seabattle;

public record BombDropRequest(
        String playerId,
        String teamId,
        double x,
        double y,
        double z,
        double heading,
        double speed,
        double verticalSpeed,
        String vehicleType
) {
}
