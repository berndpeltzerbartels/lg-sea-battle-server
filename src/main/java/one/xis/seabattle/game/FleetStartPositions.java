package one.xis.seabattle.game;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Permute existing safe surface-vessel poses once when creating a new fleet. */
final class FleetStartPositions {
    private FleetStartPositions() {}

    static List<VehicleSetup> shuffle(List<VehicleSetup> ships, RandomGenerator random) {
        List<VehicleSetup> poses = new ArrayList<>(ships.stream()
                .filter(ship -> !"scout-plane".equals(ship.vehicleType())).toList());
        for (int i = poses.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            VehicleSetup temporary = poses.get(i);
            poses.set(i, poses.get(j));
            poses.set(j, temporary);
        }
        List<VehicleSetup> result = new ArrayList<>(ships.size());
        int next = 0;
        for (VehicleSetup ship : ships) {
            if ("scout-plane".equals(ship.vehicleType())) {
                result.add(ship);
                continue;
            }
            VehicleSetup pose = poses.get(next++);
            result.add(new VehicleSetup(ship.id(), ship.teamId(), pose.position(), pose.heading(), ship.controlledBy(),
                    ship.engineOrder(), ship.rudderDegrees(), ship.nextFireDelaySeconds(), ship.vehicleType(), ship.y()));
        }
        return List.copyOf(result);
    }
}
