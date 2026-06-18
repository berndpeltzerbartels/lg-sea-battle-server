package one.xis.seabattle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class SpatialIndex<T> {

    private final double cellSize;
    private final Function<T, Vector2> positionOf;
    private final Map<Cell, List<T>> cells = new LinkedHashMap<>();

    private SpatialIndex(double cellSize, Function<T, Vector2> positionOf) {
        this.cellSize = cellSize;
        this.positionOf = positionOf;
    }

    static <T> SpatialIndex<T> from(Collection<T> values, Function<T, Vector2> positionOf, double cellSize) {
        SpatialIndex<T> index = new SpatialIndex<>(cellSize, positionOf);
        values.forEach(index::add);
        return index;
    }

    List<T> near(Vector2 center, double radius) {
        int minX = cell(center.x() - radius);
        int maxX = cell(center.x() + radius);
        int minZ = cell(center.z() - radius);
        int maxZ = cell(center.z() + radius);
        double radiusSquared = radius * radius;
        List<T> result = new ArrayList<>();
        for (int x = minX; x <= maxX; x += 1) {
            for (int z = minZ; z <= maxZ; z += 1) {
                for (T value : cells.getOrDefault(new Cell(x, z), List.of())) {
                    Vector2 position = positionOf.apply(value);
                    double dx = position.x() - center.x();
                    double dz = position.z() - center.z();
                    if (dx * dx + dz * dz <= radiusSquared) {
                        result.add(value);
                    }
                }
            }
        }
        return result;
    }

    private void add(T value) {
        Vector2 position = positionOf.apply(value);
        cells.computeIfAbsent(new Cell(cell(position.x()), cell(position.z())), ignored -> new ArrayList<>()).add(value);
    }

    private int cell(double coordinate) {
        return (int) Math.floor(coordinate / cellSize);
    }

    private record Cell(int x, int z) {
    }
}
