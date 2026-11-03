package one.xis.seabattle.game;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Permute existing safe surface-vessel poses once when creating a new fleet. */
final class FleetStartPositions {
    private FleetStartPositions() {}

    static List<ShipSetup> shuffle(List<ShipSetup> ships, RandomGenerator random) {
        List<ShipSetup> poses = new ArrayList<>(ships.stream()
                .filter(ship -> !"scout-plane".equals(ship.vehicleType())).toList());
        for (int i = poses.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            ShipSetup temporary = poses.get(i);
            poses.set(i, poses.get(j));
            poses.set(j, temporary);
        }
        List<ShipSetup> result = new ArrayList<>(ships.size());
        int next = 0;
        for (ShipSetup ship : ships) {
            if ("scout-plane".equals(ship.vehicleType())) {
                result.add(ship);
                continue;
            }
            ShipSetup pose = poses.get(next++);
            result.add(new ShipSetup(ship.id(), ship.teamId(), pose.position(), pose.heading(), ship.controlledBy(),
                    ship.engineOrder(), ship.rudderDegrees(), ship.nextFireDelaySeconds(), ship.vehicleType(), ship.y()));
        }
        return List.copyOf(result);
    }
}
