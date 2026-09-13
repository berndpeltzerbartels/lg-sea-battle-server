package one.xis.seabattle.game;

import java.util.List;

record MaterialZone(
        String id,
        String material,
        List<Point2> polygon
) {
    MaterialZone {
        polygon = polygon == null ? List.of() : List.copyOf(polygon);
    }
}
