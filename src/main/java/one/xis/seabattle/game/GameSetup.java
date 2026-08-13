package one.xis.seabattle.game;

import java.util.List;

record GameSetup(
        String id,
        WorldMap worldMap,
        List<FleetSetup> fleets,
        List<Vector2> respawnCandidates
) {
}
