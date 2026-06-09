package one.xis.seabattle;

import java.util.List;
import java.util.Map;

public record GameSnapshot(
        String type,
        String sessionId,
        String state,
        double t,
        List<ShipSnapshot> ships,
        List<TorpedoSnapshot> torpedoes,
        Map<String, Integer> destroyedShipsByTeam
) {
}
