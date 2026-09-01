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
    private final List<Ship> ships;
    private final Map<String, String> activeShipIdByPlayerId = new HashMap<>();
    private final Set<String> additionalHumanShipIds = new HashSet<>();

    Fleet(String teamId, List<Ship> ships) {
        this.teamId = teamId;
        this.ships = new ArrayList<>(ships);
    }

    String teamId() {
        return teamId;
    }

    List<Ship> ships() {
        return ships;
    }

    List<Ship> activeShips() {
        return ships.stream()
                .filter(ship -> "active".equals(ship.state()))
                .toList();
    }

    boolean hasActiveShips() {
        return !activeShips().isEmpty();
    }

    Optional<Ship> assignNextShipToPlayer(String playerId) {
        return assignNextShipToPlayer(playerId, null);
    }

    Optional<Ship> assignNextShipToPlayer(String playerId, String vehicleType) {
        Optional<Ship> assignedShip = assignedShip(playerId);
        if (assignedShip.isPresent()) {
            if (vehicleType != null) {
                assignedShip.get().vehicleType(vehicleType);
            }
            return assignedShip;
        }

        List<Ship> availableShips = activeShips().stream()
                .filter(ship -> "bot".equals(ship.controlledBy()))
                .filter(ship -> !ship.isScoutPlane())
                .toList();
        Optional<Ship> availableShip = randomShip(availableShips);
        if (availableShip.isEmpty() && ships.size() == 1) {
            availableShip = activeShips().stream().findFirst();
        }
        availableShip.ifPresent(ship -> {
            if (vehicleType != null) {
                ship.vehicleType(vehicleType);
            }
            ship.controlledBy(playerId);
            ship.nextFireTime(0);
            activeShipIdByPlayerId.put(playerId, ship.id());
        });
        return availableShip;
    }

    Ship assignAdditionalShipToPlayer(String playerId, Vector2 position, double heading, String vehicleType, double y) {
        Ship ship = new Ship(
                nextAdditionalShipId(),
                teamId,
                position,
                MathSupport.normalizeAngle(heading),
                playerId
        );
        ship.vehicleType(vehicleType);
        ship.y(y);
        ship.nextFireTime(0);
        ships.add(ship);
        activeShipIdByPlayerId.put(playerId, ship.id());
        additionalHumanShipIds.add(ship.id());
        return ship;
    }

    private Optional<Ship> randomShip(List<Ship> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    Optional<Ship> assignedShip(String playerId) {
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
