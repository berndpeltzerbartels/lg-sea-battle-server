package one.xis.seabattle.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

class LandscapeModelConverter {

    private static final double DEFAULT_SEA_FLOOR_HEIGHT = -80;
    private static final double MIN_LAND_RADIUS = 12;

    WorldMap convertEditorLandscape(JsonObject root) {
        JsonArray islands = arrayValue(root, "islands");
        if (islands.isEmpty()) {
            throw new IllegalArgumentException("Die Landschaft enthält keine Inseln.");
        }
        List<JsonObject> islandObjects = islands.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .toList();
        Map<String, Map<String, Double>> plateauHeights = plateauHeightsByIslandId(islandObjects);
        List<Landmass> landmasses = islandObjects.stream()
                .map(island -> convertIsland(island, plateauHeights))
                .toList();
        List<MapObject> mapObjects = mapObjects(root, islands);
        int version = Math.max(10_000, Math.floorMod(root.toString().hashCode(), 90_000));
        return new WorldMap(version, landmasses, mapObjects);
    }

    private List<MapObject> mapObjects(JsonObject root, JsonArray islands) {
        List<MapObject> globalObjects = landmarkObjects(arrayValue(root, "landmarks"), null);
        List<MapObject> islandObjects = islands.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .flatMap(island -> landmarkObjects(arrayValue(island, "landmarks"), island).stream())
                .toList();
        if (globalObjects.isEmpty()) {
            return islandObjects;
        }
        if (islandObjects.isEmpty()) {
            return globalObjects;
        }
        return java.util.stream.Stream.concat(globalObjects.stream(), islandObjects.stream()).toList();
    }

    private List<MapObject> landmarkObjects(JsonArray landmarks, JsonObject island) {
        return landmarks.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .filter(this::isGameMapObject)
                .map(landmark -> mapObject(landmark, island))
                .toList();
    }

    private boolean isGameMapObject(JsonObject landmark) {
        String type = normalizedObjectType(stringValue(landmark, "type").orElse(""));
        return !type.isBlank();
    }

    private MapObject mapObject(JsonObject landmark, JsonObject island) {
        String type = normalizedObjectType(stringValue(landmark, "type").orElse("landmark"));
        String id = normalizeName(stringValue(landmark, "id")
                .or(() -> stringValue(landmark, "name"))
                .orElse(type));
        String name = stringValue(landmark, "name").orElse(id);
        double x = numberValue(landmark, "x").orElseGet(() -> island == null ? 0 : bounds(arrayValue(island, "polygon")).centerX());
        double y = numberValue(landmark, "y")
                .orElseThrow(() -> new IllegalArgumentException("Landmark " + id + " braucht eine y-Koordinate."));
        double z = numberValue(landmark, "z").orElseGet(() -> island == null ? 0 : bounds(arrayValue(island, "polygon")).centerZ());
        Double heading = numberValue(landmark, "heading").orElse(null);
        Double scale = numberValue(landmark, "scale").orElse(null);
        return new MapObject(id, type, name, x, y, z, heading, scale);
    }

    private String normalizedObjectType(String type) {
        return switch (type == null ? "" : type.trim().toLowerCase(Locale.ROOT)) {
            case "striped-lighthouse", "red-white-lighthouse", "lighthouse-striped" -> "lighthouse-striped";
            case "beacon", "rock-light", "rock-beacon" -> "rock-beacon";
            case "rocks", "water-rock", "rock" -> "rock";
            default -> type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        };
    }

    private Landmass convertIsland(JsonObject island, Map<String, Map<String, Double>> plateauHeights) {
        JsonArray polygon = arrayValue(island, "polygon");
        Bounds bounds = bounds(polygon);
        double centerX = bounds.centerX();
        double centerZ = bounds.centerZ();
        double rx = Math.max(MIN_LAND_RADIUS, bounds.width() * 0.5);
        double rz = Math.max(MIN_LAND_RADIUS, bounds.depth() * 0.5);
        double authoredPeak = peakHeight(island);
        double seaFloorHeight = numberValue(island, "seaFloorHeight").orElse(DEFAULT_SEA_FLOOR_HEIGHT);
        String baseLevel = normalizedBaseLevel(stringValue(island, "baseLevel").orElse("seaFloor"));
        String baseLandmassId = stringValue(island, "baseLandmassId").orElse(null);
        String basePlateauGroupId = stringValue(island, "basePlateauGroupId").orElse(null);
        double baseHeight = terrainBaseHeight(baseLevel, baseLandmassId, basePlateauGroupId, seaFloorHeight, plateauHeights);
        double aboveSeaHeight = Math.max(0, authoredPeak);
        double heightScale = clamp(0.55, 3.0, 0.7 + aboveSeaHeight / 2200.0);
        Double peakBoost = aboveSeaHeight > 90 ? clamp(0, 220, aboveSeaHeight / 26.0) : null;
        String name = stringValue(island, "name")
                .or(() -> stringValue(island, "id"))
                .orElse("landscape-island");
        String kind = touchesWorldEdge(island) ? "coastline" : "island";
        double navigationRx = rx * (seaFloorHeight < -20 ? 0.98 : 1.02);
        double navigationRz = rz * (seaFloorHeight < -20 ? 0.98 : 1.02);
        List<Point2> authoredPolygon = polygonPoints(polygon);
        List<HeightPoint> authoredHeightPoints = heightPoints(arrayValue(island, "heights"));
        String material = normalizedMaterial(stringValue(island, "material").orElse("grass"));
        List<MaterialZone> materialZones = materialZones(arrayValue(island, "materialZones"));
        return new Landmass(
                kind,
                normalizeName(name),
                centerX,
                centerZ,
                rx,
                rz,
                Math.max(MIN_LAND_RADIUS, navigationRx),
                Math.max(MIN_LAND_RADIUS, navigationRz),
                rx,
                rz,
                Math.min(rx, rz),
                heightScale,
                peakBoost,
                "coastline".equals(kind) ? 0.22 : null,
                null,
                List.of(),
                List.of(),
                List.of(),
                authoredPolygon,
                authoredHeightPoints,
                seaFloorHeight,
                baseHeight,
                baseLevel,
                baseLandmassId,
                basePlateauGroupId,
                material,
                materialZones
        );
    }

    private Map<String, Map<String, Double>> plateauHeightsByIslandId(List<JsonObject> islands) {
        Map<String, Map<String, DoubleAccumulator>> accumulators = new LinkedHashMap<>();
        for (JsonObject island : islands) {
            String id = stringValue(island, "id").orElse(null);
            if (id == null || id.isBlank()) {
                continue;
            }
            Map<String, DoubleAccumulator> islandAccumulators = accumulators.computeIfAbsent(id, ignored -> new LinkedHashMap<>());
            for (JsonElement element : arrayValue(island, "heights")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject point = element.getAsJsonObject();
                String plateauGroupId = stringValue(point, "plateauGroupId").orElse(null);
                if (plateauGroupId == null || plateauGroupId.isBlank()) {
                    continue;
                }
                islandAccumulators
                        .computeIfAbsent(plateauGroupId, ignored -> new DoubleAccumulator())
                        .add(clamp(-500, 8000, numberValue(point, "h").orElse(0.0)));
            }
        }
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        accumulators.forEach((islandId, groups) -> {
            Map<String, Double> heights = new LinkedHashMap<>();
            groups.forEach((groupId, accumulator) -> heights.put(groupId, accumulator.average()));
            result.put(islandId, heights);
        });
        return result;
    }

    private double terrainBaseHeight(String baseLevel, String baseLandmassId, String basePlateauGroupId,
                                     double seaFloorHeight, Map<String, Map<String, Double>> plateauHeights) {
        if ("plateau".equals(baseLevel)) {
            return Optional.ofNullable(plateauHeights.get(baseLandmassId))
                    .map(groups -> groups.get(basePlateauGroupId))
                    .orElse(0.1);
        }
        if ("beach".equals(baseLevel)) {
            return 0.1;
        }
        return seaFloorHeight;
    }

    private boolean touchesWorldEdge(JsonObject island) {
        return "coastline".equalsIgnoreCase(stringValue(island, "kind").orElse(""));
    }

    private Bounds bounds(JsonArray points) {
        if (points.isEmpty()) {
            return new Bounds(-MIN_LAND_RADIUS, MIN_LAND_RADIUS, -MIN_LAND_RADIUS, MIN_LAND_RADIUS);
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (JsonElement element : points) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject point = element.getAsJsonObject();
            double x = numberValue(point, "x").orElse(0.0);
            double z = numberValue(point, "z").orElse(0.0);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        if (!Double.isFinite(minX)) {
            return new Bounds(-MIN_LAND_RADIUS, MIN_LAND_RADIUS, -MIN_LAND_RADIUS, MIN_LAND_RADIUS);
        }
        return new Bounds(minX, maxX, minZ, maxZ);
    }

    private double peakHeight(JsonObject island) {
        JsonArray heights = arrayValue(island, "heights");
        return heights.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map(point -> numberValue(point, "h").orElse(0.0))
                .max(Comparator.naturalOrder())
                .orElse(0.0);
    }

    private List<Point2> polygonPoints(JsonArray polygon) {
        return polygon.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map(point -> new Point2(
                        numberValue(point, "x").orElse(0.0),
                        numberValue(point, "z").orElse(0.0)
                ))
                .toList();
    }

    private List<HeightPoint> heightPoints(JsonArray heights) {
        return heights.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map(point -> new HeightPoint(
                        numberValue(point, "x").orElse(0.0),
                        numberValue(point, "z").orElse(0.0),
                        clamp(-500, 8000, numberValue(point, "h").orElse(0.0)),
                        clamp(20, 5000, numberValue(point, "radius").orElse(160.0)),
                        normalizedFalloff(stringValue(point, "falloff").orElse("spike")),
                        basePointIndexes(arrayValue(point, "basePointIndexes")),
                        stringValue(point, "plateauGroupId").orElse(null),
                        stringValue(point, "basePlateauGroupId").orElse(null),
                        integerValue(point.get("plateauOrder")).orElse(null)
                ))
                .toList();
    }

    private List<Integer> basePointIndexes(JsonArray indexes) {
        return indexes.asList().stream()
                .map(this::integerValue)
                .flatMap(Optional::stream)
                .filter(index -> index >= 0)
                .distinct()
                .sorted()
                .toList();
    }

    private List<MaterialZone> materialZones(JsonArray zones) {
        return zones.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map(zone -> new MaterialZone(
                        normalizeName(stringValue(zone, "id").orElseGet(() -> stringValue(zone, "name").orElse("material-zone"))),
                        normalizedMaterial(stringValue(zone, "material").orElse("grass")),
                        polygonPoints(arrayValue(zone, "polygon"))
                ))
                .filter(zone -> zone.polygon().size() >= 3)
                .toList();
    }

    private String normalizedMaterial(String value) {
        String material = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (material) {
            case "sand", "snow", "rock", "seafloor", "sea-floor", "underwater" -> material;
            default -> "grass";
        };
    }

    private String normalizedFalloff(String value) {
        String falloff = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (falloff) {
            case "hill", "plateau" -> falloff;
            default -> "spike";
        };
    }

    private String normalizedBaseLevel(String value) {
        String baseLevel = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (baseLevel) {
            case "beach", "plateau" -> baseLevel;
            default -> "seaFloor";
        };
    }

    private JsonArray arrayValue(JsonObject object, String property) {
        JsonElement element = object.get(property);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private Optional<String> stringValue(JsonObject object, String property) {
        JsonElement element = object.get(property);
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        return Optional.of(element.getAsString());
    }

    private Optional<Double> numberValue(JsonObject object, String property) {
        JsonElement element = object.get(property);
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        try {
            return Optional.of(element.getAsDouble());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Integer> integerValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        try {
            return Optional.of(element.getAsInt());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private String normalizeName(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "landscape_island" : normalized;
    }

    private double clamp(double min, double max, double value) {
        return Math.max(min, Math.min(max, value));
    }

    private record Bounds(double minX, double maxX, double minZ, double maxZ) {
        double centerX() {
            return (minX + maxX) * 0.5;
        }

        double centerZ() {
            return (minZ + maxZ) * 0.5;
        }

        double width() {
            return maxX - minX;
        }

        double depth() {
            return maxZ - minZ;
        }
    }

    private static final class DoubleAccumulator {
        private double sum;
        private int count;

        void add(double value) {
            sum += value;
            count += 1;
        }

        double average() {
            return count == 0 ? 0 : sum / count;
        }
    }
}
