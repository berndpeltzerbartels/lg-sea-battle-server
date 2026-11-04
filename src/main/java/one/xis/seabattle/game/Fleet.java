package one.xis.seabattle.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

final class Fleet {

    private final String teamId;
    private final List<Vehicle> ships;
    private final int intendedSubmarineCount;
    private final Map<String, String> activeShipIdByPlayerId = new HashMap<>();
    private final Set<String> additionalHumanShipIds = new HashSet<>();

    Fleet(String teamId, List<Vehicle> ships) {
        this.teamId = teamId;
        this.ships = new ArrayList<>(ships);
        intendedSubmarineCount = (int) ships.stream().filter(Vehicle::isSubmarine).count();
    }

    String teamId() {
        return teamId;
    }

    int intendedSubmarineCount() { return intendedSubmarineCount; }

    List<Vehicle> ships() {
        return ships;
    }

    List<Vehicle> activeShips() {
        return ships.stream()
                .filter(ship -> "active".equals(ship.state()))
                .toList();
    }

    boolean hasActiveShips() {
        return !activeShips().isEmpty();
    }

    Optional<Vehicle> assignNextShipToPlayer(String playerId) {
        return assignNextShipToPlayer(playerId, null);
    }

    Optional<Vehicle> assignNextShipToPlayer(String playerId, String vehicleType) {
        Optional<Vehicle> assignedShip = assignedShip(playerId);
        if (assignedShip.isPresent()) {
            if (vehicleType != null) {
                return Optional.of(changeVehicleType(assignedShip.get(), vehicleType));
            }
            return assignedShip;
        }

        List<Vehicle> availableShips = activeShips().stream()
                .filter(ship -> "bot".equals(ship.controlledBy()))
                .filter(ship -> !ship.isScoutPlane())
                .toList();
        // Prefer the chosen type instead of permanently converting another fleet slot on every login.
        List<Vehicle> matchingShips = vehicleType == null ? List.of() : availableShips.stream()
                .filter(ship -> vehicleType.equals(ship.vehicleType())).toList();
        Optional<Vehicle> availableShip = randomShip(matchingShips.isEmpty() ? availableShips : matchingShips);
        if (availableShip.isEmpty() && ships.size() == 1) {
            availableShip = activeShips().stream().filter(ship -> ship.isServerSimulated() || playerId.equals(ship.controlledBy())).findFirst();
        }
        return availableShip.map(available -> {
            Vehicle ship = changeVehicleType(available, vehicleType);
            ship.controlledBy(playerId);
            ship.nextFireTime(0);
            activeShipIdByPlayerId.put(playerId, ship.id());
            return ship;
        });
    }

    Vehicle changeVehicleType(Vehicle current, String type) {
        Vehicle replacement = current.convertedTo(type);
        if (replacement == current) return current;
        int index = ships.indexOf(current);
        if (index < 0) throw new IllegalArgumentException("Vehicle is not in fleet: " + current.id());
        ships.set(index, replacement);
        current.retired();
        return replacement;
    }

    Vehicle assignAdditionalShipToPlayer(String playerId, Vector2 position, double heading, String vehicleType, double y) {
        Vehicle ship = Vehicle.create(vehicleType, nextAdditionalShipId(), teamId, position, MathSupport.normalizeAngle(heading), playerId);
        ship.y(y);
        ship.nextFireTime(0);
        ships.add(ship);
        activeShipIdByPlayerId.put(playerId, ship.id());
        additionalHumanShipIds.add(ship.id());
        return ship;
    }

    private Optional<Vehicle> randomShip(List<Vehicle> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    Optional<Vehicle> assignedShip(String playerId) {
        String shipId = activeShipIdByPlayerId.get(playerId);
        if (shipId == null) {
            return Optional.empty();
        }
        return activeShips().stream()
                .filter(ship -> ship.id().equals(shipId))
                .findFirst();
    }

    void releaseShip(String shipId) {
        activeShipIdByPlayerId.entrySet().removeIf(entry -> shipId.equals(entry.getValue()));
        if (additionalHumanShipIds.remove(shipId)) {
            ships.removeIf(ship -> ship.id().equals(shipId));
        }
    }

    void releasePlayer(String playerId) {
        String shipId = activeShipIdByPlayerId.remove(playerId);
        if (shipId == null) {
            return;
        }
        if (additionalHumanShipIds.remove(shipId)) {
            ships.removeIf(ship -> ship.id().equals(shipId));
            return;
        }
        activeShips().stream()
                .filter(ship -> ship.id().equals(shipId))
                .findFirst()
                .ifPresent(ship -> {
                    ship.controlledBy("bot");
                    ship.nextFireTime(0);
                });
    }

    void transferController(String previous, String next) {
        String shipId = activeShipIdByPlayerId.remove(previous);
        if (shipId == null) return;
        activeShipIdByPlayerId.put(next, shipId);
        ships.stream().filter(s -> s.id().equals(shipId)).findFirst().ifPresent(s -> s.controlledBy(next));
    }

    void removeUnoccupiedShipAt(VehicleSnapshot previous) {
        ships.removeIf(s -> s.id().equals(previous.id()) && "bot".equals(s.controlledBy())
                && s.position().distanceTo(new Vector2(previous.x(), previous.z())) < 0.01);
    }

    private String nextAdditionalShipId() {
        int next = ships.size() + 1;
        String candidate;
        do {
            candidate = teamId + "-S" + next++;
        } while (shipIdExists(candidate));
        return candidate;
    }

    private boolean shipIdExists(String shipId) {
        return ships.stream().anyMatch(ship -> ship.id().equals(shipId));
    }
}
