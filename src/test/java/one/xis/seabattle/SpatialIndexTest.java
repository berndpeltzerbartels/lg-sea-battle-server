package one.xis.seabattle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialIndexTest {

    @Test
    void findsValuesAcrossCellBoundariesWithinRadius() {
        List<IndexedPoint> points = List.of(
                new IndexedPoint("near-left", new Vector2(49, 0)),
                new IndexedPoint("near-right", new Vector2(51, 0)),
                new IndexedPoint("far", new Vector2(180, 0))
        );

        SpatialIndex<IndexedPoint> index = SpatialIndex.from(points, IndexedPoint::position, 50);

        List<String> ids = index.near(new Vector2(50, 0), 3).stream()
                .map(IndexedPoint::id)
                .toList();

        assertEquals(List.of("near-left", "near-right"), ids);
    }

    @Test
    void returnsEmptyListWhenNothingIsInRange() {
        SpatialIndex<IndexedPoint> index = SpatialIndex.from(
                List.of(new IndexedPoint("far", new Vector2(120, 120))),
                IndexedPoint::position,
                50
        );

        assertTrue(index.near(new Vector2(0, 0), 10).isEmpty());
    }

    private record IndexedPoint(String id, Vector2 position) {
    }
}
