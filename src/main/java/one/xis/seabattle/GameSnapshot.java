package one.xis.seabattle;

import java.util.List;

public record GameSnapshot(
        String type,
        String sessionId,
        String state,
        double t,
        List<ShipSnapshot> ships,
        List<TorpedoSnapshot> torpedoes
) {
}
