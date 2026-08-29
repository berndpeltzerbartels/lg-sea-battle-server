package one.xis.seabattle.game;

import java.util.List;
import java.util.Map;

public record GameSnapshot(
        String type,
        String sessionId,
        String state,
        double t,
        List<ShipSnapshot> ships,
        List<TorpedoSnapshot> torpedoes,
        List<TorpedoImpactSnapshot> torpedoImpacts,
        List<BombSnapshot> bombs,
        List<BombImpactSnapshot> bombImpacts,
        List<FlakProjectileSnapshot> flakProjectiles,
        List<ProjectileHitSnapshot> flakHits,
        List<FlakImpactSnapshot> flakImpacts,
        List<RamHitSnapshot> ramHits,
        Map<String, Integer> destroyedShipsByTeam,
        Map<String, Integer> killsByPlayer
) {
}
