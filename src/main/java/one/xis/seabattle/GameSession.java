package one.xis.seabattle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameSession {

    private final String id;
    private final Map<String, Fleet> fleets;
    private final List<Torpedo> torpedoes = new ArrayList<>();
    private double nowSeconds;
    private String state = "running";

    public GameSession() {
        this("local-test");
    }

    public GameSession(String id) {
        this.id = id;
        this.fleets = createStartingFleets();
    }

    public synchronized GameSnapshot snapshot() {
        return new GameSnapshot(
                "state",
                id,
                state,
                MathSupport.round(nowSeconds),
                allShips().stream()
                        .filter(ship -> "active".equals(ship.state()))
                        .map(Ship::snapshot)
                        .toList(),
                torpedoes.stream()
                        .filter(torpedo -> "running".equals(torpedo.state()))
                        .map(Torpedo::snapshot)
                        .toList()
        );
    }

    public synchronized void update(double deltaSeconds) {
        if (!"running".equals(state)) {
            return;
        }
        nowSeconds += deltaSeconds;
        allShips().forEach(ship -> ship.update(deltaSeconds));
        torpedoes.forEach(torpedo -> torpedo.update(deltaSeconds));
        torpedoes.removeIf(torpedo -> !"running".equals(torpedo.state()));
        checkGameOver();
    }

    private void checkGameOver() {
        long activeFleets = fleets.values().stream()
                .filter(Fleet::hasActiveShips)
                .count();
        if (activeFleets <= 1) {
            state = "finished";
        }
    }

    private List<Ship> allShips() {
        return fleets.values().stream()
                .flatMap(fleet -> fleet.ships().stream())
                .toList();
    }

    private static Map<String, Fleet> createStartingFleets() {
        Map<String, Fleet> fleets = new LinkedHashMap<>();
        fleets.put("red", new Fleet("red", createShips("red", -420, -220, 0.85)));
        fleets.put("blue", new Fleet("blue", createShips("blue", 420, 220, -2.3)));
        return fleets;
    }

    private static List<Ship> createShips(String teamId, double baseX, double baseZ, double heading) {
        List<Ship> ships = new ArrayList<>();
        for (int index = 0; index < 5; index += 1) {
            Ship ship = new Ship(
                    teamId + "-" + (index + 1),
                    teamId,
                    new Vector2(baseX + index * 18, baseZ + (index % 2) * 26),
                    heading,
                    "bot"
            );
            ship.nextFireTime(5 + index * 2.5);
            ships.add(ship);
        }
        return ships;
    }
}
