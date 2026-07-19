package one.xis.seabattle;

public record FireTorpedoRequest(
        String playerId,
        String teamId,
        String vehicleType
) {
    public FireTorpedoRequest(String playerId, String teamId) {
        this(playerId, teamId, "torpedo-boat");
    }
}
