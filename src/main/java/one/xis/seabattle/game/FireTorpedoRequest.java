package one.xis.seabattle.game;

public record FireTorpedoRequest(
        String playerId,
        String teamId,
        String vehicleType,
        double x,
        double z,
        double heading,
        double speed,
        double turnVelocity,
        int engineOrder,
        double rudderDegrees,
        double y,
        double verticalSpeed,
        Integer tubeSide,
        double clientTime
) {
    public FireTorpedoRequest(String playerId, String teamId) {
        this(playerId, teamId, "torpedo-boat");
    }

    public FireTorpedoRequest(String playerId, String teamId, String vehicleType) {
        this(playerId, teamId, vehicleType, 0, 0, 0, 0, 0, 2, 0, 0, 0, null, 0);
    }

    public FireTorpedoRequest(String playerId, String teamId, String vehicleType, double x, double z, double heading,
                              double speed, double turnVelocity, int engineOrder, double rudderDegrees,
                              Integer tubeSide, double clientTime) {
        this(playerId, teamId, vehicleType, x, z, heading, speed, turnVelocity, engineOrder, rudderDegrees,
                0, 0, tubeSide, clientTime);
    }

    boolean includesPlayerState() {
        return clientTime > 0 && Double.isFinite(x) && Double.isFinite(z) && Double.isFinite(heading)
                && Double.isFinite(speed) && Double.isFinite(turnVelocity) && Double.isFinite(rudderDegrees)
                && Double.isFinite(y) && Double.isFinite(verticalSpeed);
    }
}
