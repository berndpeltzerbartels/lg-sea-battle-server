package one.xis.seabattle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class Fleet {

    private final String teamId;
    private final List<Ship> ships;
    private final Map<String, String> activeShipIdByPlayerId = new HashMap<>();

    Fleet(String teamId, List<Ship> ships) {
        this.teamId = teamId;
        this.ships = ships;
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
        Optional<Ship> assignedShip = assignedShip(playerId);
        if (assignedShip.isPresent()) {
            return assignedShip;
        }

        Optional<Ship> availableShip = activeShips().stream()
                .filter(ship -> "bot".equals(ship.controlledBy()))
                .findFirst();
        if (availableShip.isEmpty() && ships.size() == 1) {
            availableShip = activeShips().stream().findFirst();
        }
        availableShip.ifPresent(ship -> {
            ship.controlledBy(playerId);
            ship.nextFireTime(0);
            activeShipIdByPlayerId.put(playerId, ship.id());
        });
        return availableShip;
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
    }
}
