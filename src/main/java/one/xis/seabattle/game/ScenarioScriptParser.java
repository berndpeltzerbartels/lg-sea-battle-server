package one.xis.seabattle.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ScenarioScriptParser {

    private static final String TEAM_DARK = "dark";
    private static final String TEAM_LIGHT = "light";
    private static final String VEHICLE_TORPEDO_BOAT = "torpedo-boat";
    private static final String VEHICLE_SUBMARINE = "submarine";
    private static final String VEHICLE_SCOUT_PLANE = "scout-plane";
    private static final int ENGINE_STOP = 2;
    private static final int ENGINE_SLOW = 3;
    private static final int ENGINE_HALF = 5;
    private static final int ENGINE_TWO_THIRDS = 6;
    private static final int ENGINE_FULL = 8;

    private ScenarioScriptParser() {
    }

    static GameSetup parse(String script) {
        return parseDefinition(script).setup();
    }

    static ScenarioDefinition parseDefinition(String script) {
        ParsedScript parsed = ParsedScript.from(script);
        Map<Character, Vector2> markers = mapMarkers(parsed.mapRows(), parsed.cellSize());
        List<Landmass> landmasses = landmasses(parsed.mapRows(), parsed.cellSize());
        Map<String, List<ShipSetup>> vehiclesByTeam = new LinkedHashMap<>();
        List<Vector2> respawnCandidates = new ArrayList<>();

        for (String objectLine : parsed.objectLines()) {
            ScenarioObject object = ScenarioObject.parse(objectLine);
            Vector2 position = markers.get(object.marker());
            if (position == null) {
                throw new IllegalArgumentException("Scenario object marker not found on map: " + object.marker());
            }
            String teamId = normalizeTeam(object.team());
            String vehicleType = normalizeVehicleType(object.vehicleType());
            String id = teamId + "-" + vehiclePrefix(vehicleType) + object.marker();
            ShipSetup vehicleSetup = new ShipSetup(
                    id,
                    teamId,
                    position,
                    MathSupport.normalizeAngle(Math.toRadians(object.headingDegrees())),
                    controlledBy(object.controller(), object.marker()),
                    engineOrder(object.speedKnots()),
                    0,
                    99,
                    vehicleType,
                    object.heightMeters()
            );
            vehiclesByTeam.computeIfAbsent(teamId, ignored -> new ArrayList<>()).add(vehicleSetup);
            respawnCandidates.add(position);
        }

        List<FleetSetup> fleets = vehiclesByTeam.entrySet().stream()
                .map(entry -> new FleetSetup(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        return new ScenarioDefinition(
                new GameSetup(
                        parsed.id(),
                        new WorldMap(parsed.worldVersion(), landmasses),
                        fleets,
                        respawnCandidates
                ),
                scenarioActions(parsed.actionLines(), markers)
        );
    }

    private static Map<Character, Vector2> mapMarkers(List<String> rows, double cellSize) {
        Map<Character, Vector2> markers = new LinkedHashMap<>();
        double centerRow = (rows.size() - 1) / 2.0;
        double centerColumn = (maxWidth(rows) - 1) / 2.0;
        for (int row = 0; row < rows.size(); row += 1) {
            String line = rows.get(row);
            for (int column = 0; column < line.length(); column += 1) {
                char marker = line.charAt(column);
                if (Character.isLetterOrDigit(marker)) {
                    markers.put(marker, new Vector2(
                            (column - centerColumn) * cellSize,
                            (row - centerRow) * cellSize
                    ));
                }
            }
        }
        return markers;
    }

    private static List<Landmass> landmasses(List<String> rows, double cellSize) {
        List<Landmass> landmasses = new ArrayList<>();
        double centerRow = (rows.size() - 1) / 2.0;
        double centerColumn = (maxWidth(rows) - 1) / 2.0;
        for (int row = 0; row < rows.size(); row += 1) {
            String line = rows.get(row);
            for (int column = 0; column < line.length(); column += 1) {
                if (line.charAt(column) == '*') {
                    landmasses.add(testLandmass(
                            "test-land-" + row + "-" + column,
                            (column - centerColumn) * cellSize,
                            (row - centerRow) * cellSize,
                            cellSize * 0.42
                    ));
                }
            }
        }
        return landmasses;
    }

    private static Landmass testLandmass(String name, double x, double z, double radius) {
        return new Landmass(
                "island",
                name,
                x,
                z,
                radius,
                radius,
                radius,
                radius,
                radius,
                radius,
                radius,
                0.6,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static int maxWidth(List<String> rows) {
        return rows.stream().mapToInt(String::length).max().orElse(0);
    }

    private static String normalizeTeam(String team) {
        String normalized = team.toLowerCase(Locale.ROOT);
        if (TEAM_DARK.equals(normalized) || TEAM_LIGHT.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unknown scenario team: " + team);
    }

    private static String normalizeVehicleType(String vehicleType) {
        String normalized = vehicleType.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ship", "boat", "boot", "torpedo-boat" -> VEHICLE_TORPEDO_BOAT;
            case "submarine", "u-boat", "uboat", "u-boot", "unterseeboot" -> VEHICLE_SUBMARINE;
            case "plane", "aircraft", "flieger", "flugzeug", "scout-plane" -> VEHICLE_SCOUT_PLANE;
            default -> throw new IllegalArgumentException("Unknown scenario vehicle: " + vehicleType);
        };
    }

    private static String controlledBy(String controller, char marker) {
        String normalized = controller.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "bot" -> "bot";
            case "human", "player" -> "player-scenario-" + marker;
            case "scenario" -> "scenario";
            default -> throw new IllegalArgumentException("Unknown scenario controller: " + controller);
        };
    }

    private static String vehiclePrefix(String vehicleType) {
        if (VEHICLE_SUBMARINE.equals(vehicleType)) {
            return "U";
        }
        return VEHICLE_SCOUT_PLANE.equals(vehicleType) ? "F" : "S";
    }

    private static int engineOrder(double speedKnots) {
        double speed = Math.abs(speedKnots);
        if (speed < 0.1) {
            return ENGINE_STOP;
        }
        if (speed <= 3) {
            return ENGINE_SLOW;
        }
        if (speed <= 7) {
            return ENGINE_HALF;
        }
        if (speed <= 10) {
            return ENGINE_TWO_THIRDS;
        }
        return ENGINE_FULL;
    }

    private static List<ScenarioAction> scenarioActions(List<String> actionLines, Map<Character, Vector2> markers) {
        return actionLines.stream()
                .map(line -> ScenarioAction.parse(line, markers))
                .toList();
    }

    private record ParsedScript(String id, int worldVersion, double cellSize, List<String> mapRows,
                                List<String> objectLines, List<String> actionLines) {

        private static ParsedScript from(String script) {
            String id = "scenario";
            int worldVersion = 9001;
            double cellSize = 50;
            List<String> mapRows = new ArrayList<>();
            List<String> objectLines = new ArrayList<>();
            List<String> actionLines = new ArrayList<>();
            Section section = Section.HEADER;

            for (String rawLine : script.lines().toList()) {
                String line = stripComment(rawLine);
                if (line.isBlank()) {
                    continue;
                }
                String lower = line.toLowerCase(Locale.ROOT);
                if ("map:".equals(lower)) {
                    section = Section.MAP;
                    continue;
                }
                if ("objects:".equals(lower)) {
                    section = Section.OBJECTS;
                    continue;
                }
                if ("actions:".equals(lower)) {
                    section = Section.ACTIONS;
                    continue;
                }
                if (section == Section.MAP) {
                    mapRows.add(line);
                    continue;
                }
                if (section == Section.OBJECTS) {
                    objectLines.add(line);
                    continue;
                }
                if (section == Section.ACTIONS) {
                    actionLines.add(line);
                    continue;
                }
                if (lower.startsWith("scenario:") || lower.startsWith("id:")) {
                    id = valueAfterColon(line);
                } else if (lower.startsWith("cell:")) {
                    cellSize = parseNumber(valueAfterColon(line));
                } else if (lower.startsWith("version:")) {
                    worldVersion = (int) parseNumber(valueAfterColon(line));
                }
            }
            if (mapRows.isEmpty()) {
                throw new IllegalArgumentException("Scenario has no map");
            }
            if (objectLines.isEmpty()) {
                throw new IllegalArgumentException("Scenario has no objects");
            }
            return new ParsedScript(
                    id,
                    worldVersion,
                    cellSize,
                    List.copyOf(mapRows),
                    List.copyOf(objectLines),
                    List.copyOf(actionLines)
            );
        }

        private static String stripComment(String line) {
            int index = line.indexOf('#');
            return (index >= 0 ? line.substring(0, index) : line).strip();
        }

        private static String valueAfterColon(String line) {
            int index = line.indexOf(':');
            if (index < 0 || index + 1 >= line.length()) {
                return "";
            }
            return line.substring(index + 1).strip();
        }
    }

    private enum Section {
        HEADER,
        MAP,
        OBJECTS,
        ACTIONS
    }

    private record ScenarioObject(char marker, String vehicleType, String team, String controller,
                                  double headingDegrees, double speedKnots, double heightMeters) {

        private static ScenarioObject parse(String line) {
            int colon = line.indexOf(':');
            if (colon != 1) {
                throw new IllegalArgumentException("Scenario object line must start with one marker and colon: " + line);
            }
            char marker = line.charAt(0);
            String body = line.substring(colon + 1).strip();
            String options = "";
            int optionStart = body.indexOf('[');
            int optionEnd = body.lastIndexOf(']');
            if (optionStart >= 0 && optionEnd > optionStart) {
                options = body.substring(optionStart + 1, optionEnd);
                body = body.substring(0, optionStart).strip();
            }
            String[] parts = body.replace(",", " ").trim().split("\\s+");
            if (parts.length < 3) {
                throw new IllegalArgumentException("Scenario object needs vehicle, team and controller: " + line);
            }
            Map<String, String> values = optionValues(options);
            double heading = optionNumber(values, "heading", optionNumber(values, "orientation", 0));
            double speed = optionNumber(values, "speed", 0);
            double height = optionNumber(values, "height", 0);
            return new ScenarioObject(marker, parts[0], parts[1], parts[2], heading, speed, height);
        }

        private static Map<String, String> optionValues(String options) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String rawOption : options.split(",")) {
                String option = rawOption.strip();
                if (option.isBlank()) {
                    continue;
                }
                String[] parts = option.split("[:=]", 2);
                if (parts.length == 2) {
                    values.put(parts[0].strip().toLowerCase(Locale.ROOT), parts[1].strip());
                }
            }
            return values;
        }

        private static double optionNumber(Map<String, String> values, String key, double fallback) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return parseNumber(value);
        }
    }

    private static double parseNumber(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace("°", "")
                .replace("deg", "")
                .replace("knt", "")
                .replace("kn", "")
                .replace("m", "")
                .strip();
        return Double.parseDouble(normalized);
    }
}

record ScenarioDefinition(GameSetup setup, List<ScenarioAction> actions) {
}

record ScenarioAction(char actorMarker, String weapon, ScenarioPoint target) {

    static ScenarioAction parse(String line, Map<Character, Vector2> markers) {
        int colon = line.indexOf(':');
        if (colon != 1) {
            throw new IllegalArgumentException("Scenario action line must start with one marker and colon: " + line);
        }
        char actorMarker = line.charAt(0);
        if (!markers.containsKey(actorMarker)) {
            throw new IllegalArgumentException("Scenario action actor marker not found on map: " + actorMarker);
        }
        String body = line.substring(colon + 1).strip();
        String normalized = body.toLowerCase(Locale.ROOT);
        String weapon = scenarioWeapon(normalized, line);
        String options = actionOptions(body, line);
        Map<String, String> values = optionValues(options);
        return new ScenarioAction(
                actorMarker,
                weapon,
                new ScenarioPoint(
                        optionNumber(values, "x", 0),
                        optionNumber(values, "y", 0),
                        optionNumber(values, "z", 0)
                )
        );
    }

    private static String scenarioWeapon(String normalized, String line) {
        if (normalized.startsWith("cannon") || normalized.startsWith("fire cannon") || normalized.startsWith("kanone")) {
            return "cannon";
        }
        if (normalized.startsWith("flak") || normalized.startsWith("fire flak")) {
            return "flak";
        }
        throw new IllegalArgumentException("Unknown scenario action weapon: " + line);
    }

    private static String actionOptions(String body, String line) {
        int optionStart = body.indexOf('[');
        int optionEnd = body.lastIndexOf(']');
        if (optionStart < 0 || optionEnd <= optionStart) {
            throw new IllegalArgumentException("Scenario action needs a target point [x: ..., y: ..., z: ...]: " + line);
        }
        return body.substring(optionStart + 1, optionEnd);
    }

    private static Map<String, String> optionValues(String options) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawOption : options.split(",")) {
            String option = rawOption.strip();
            if (option.isBlank()) {
                continue;
            }
            String[] parts = option.split("[:=]", 2);
            if (parts.length == 2) {
                values.put(parts[0].strip().toLowerCase(Locale.ROOT), parts[1].strip());
            }
        }
        return values;
    }

    private static double optionNumber(Map<String, String> values, String key, double fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return parseNumber(value);
    }

    private static double parseNumber(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace("°", "")
                .replace("deg", "")
                .replace("knt", "")
                .replace("kn", "")
                .replace("m", "")
                .strip();
        return Double.parseDouble(normalized);
    }
}

record ScenarioPoint(double x, double y, double z) {
}
