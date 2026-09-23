package one.xis.seabattle.game;

import java.util.*;

/** Mapped planar sectors used while preparing the server height field. */
final class MappedPlateauGeometry {
    private static final Map<Landmass, List<List<Vertex>>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    record Vertex(double x, double z, double h) {}

    static void validate(Landmass land) {
        var groups = new LinkedHashMap<String, List<HeightPoint>>();
        for (var p : land.heightPoints()) {
            if (p.plateauGroupId() != null) {
                if (p.plateauGroupId().isBlank()) throw invalid(land, "Leere Plateau-Kennung.");
                groups.computeIfAbsent(p.plateauGroupId(), key -> new ArrayList<>()).add(p);
            } else if (p.plateauBoundaryPointId() != null) {
                throw invalid(land, "Randzuordnung ohne Plateau.");
            }
        }
        if (groups.isEmpty()) return;
        var boundaryIds = new HashSet<String>();
        for (var p : land.polygon()) {
            if (p.boundaryPointId() != null && (p.boundaryPointId().isBlank() || !boundaryIds.add(p.boundaryPointId()))) {
                throw invalid(land, "Leere oder doppelte Randpunkt-Kennung.");
            }
        }
        for (var entry : groups.entrySet()) {
            if (entry.getValue().size() < 3) throw invalid(land, "Plateau " + entry.getKey() + " braucht mindestens drei Punkte.");
            var used = new HashSet<String>();
            for (var p : entry.getValue()) {
                String target = p.plateauBoundaryPointId();
                if (target == null || target.isBlank() || !boundaryIds.contains(target)) {
                    throw invalid(land, "Plateau " + entry.getKey() + ": Jeder Punkt braucht eine gueltige Randzuordnung.");
                }
                if (!used.add(target)) throw invalid(land, "Plateau " + entry.getKey() + ": Randpunkt mehrfach zugeordnet.");
            }
        }
        try {
            CACHE.computeIfAbsent(land, MappedPlateauGeometry::prepare);
        } catch (IllegalArgumentException e) {
            throw invalid(land, "Ungueltige Plateau-Geometrie: " + e.getMessage());
        }
    }

    private static IllegalArgumentException invalid(Landmass land, String reason) {
        return new IllegalArgumentException("Landschaft " + land.name() + ": " + reason);
    }

    static boolean complete(Landmass land) {
        var counts = new HashMap<String, Integer>();
        for (var point : land.heightPoints()) if (point.plateauGroupId() != null) counts.merge(point.plateauGroupId(), 1, Integer::sum);
        var points = land.heightPoints().stream().filter(p -> p.plateauGroupId() != null && counts.get(p.plateauGroupId()) >= 3).toList();
        return !points.isEmpty() && points.stream().allMatch(p -> p.plateauBoundaryPointId() != null);
    }

    static double height(Vector2 position, Landmass land) {
        var triangles = CACHE.computeIfAbsent(land, MappedPlateauGeometry::prepare);
        double height = land.baseHeight();
        var p = new Vertex(position.x(), position.z(), 0);
        for (var t : triangles) {
            var a = t.get(0); var b = t.get(1); var c = t.get(2);
            double determinant = cross(a, b, c);
            double wa = cross(p, b, c) / determinant, wb = cross(a, p, c) / determinant, wc = 1 - wa - wb;
            if (Math.min(wa, Math.min(wb, wc)) >= -1e-6) height = Math.max(height, wa * a.h() + wb * b.h() + wc * c.h());
        }
        return height;
    }

    private static List<List<Vertex>> prepare(Landmass land) {
        var boundary = land.polygon().stream().map(p -> new Vertex(p.x(), p.z(), land.baseHeight())).toList();
        var groups = new LinkedHashMap<String, List<HeightPoint>>();
        for (var p : land.heightPoints()) if (p.plateauGroupId() != null) groups.computeIfAbsent(p.plateauGroupId(), k -> new ArrayList<>()).add(p);
        var triangles = new ArrayList<List<Vertex>>();
        for (var points : groups.values()) {
            if (points.size() < 3) continue;
            points = LandGeometry.orderedPlateauPoints(points);
            double h = points.stream().mapToDouble(HeightPoint::h).average().orElseThrow();
            var ring = points.stream().map(p -> new Vertex(p.x(), p.z(), h)).toList();
            int[] targets = points.stream().mapToInt(p -> {
                for (int i = 0; i < land.polygon().size(); i++) if (p.plateauBoundaryPointId().equals(land.polygon().get(i).boundaryPointId())) return i;
                throw new IllegalArgumentException("Missing plateau boundary point");
            }).toArray();
            if (Arrays.stream(targets).distinct().count() != targets.length) throw new IllegalArgumentException("Duplicate plateau boundary point");
            int direction = area(ring) * area(boundary) > 0 ? 1 : -1, steps = 0;
            int start = triangles.size();
            triangles.addAll(triangulate(ring));
            for (int i = 0; i < ring.size(); i++) {
                int j = (i + 1) % ring.size(), cursor = targets[i];
                var sector = new ArrayList<Vertex>();
                sector.add(ring.get(i)); sector.add(boundary.get(cursor));
                while (cursor != targets[j]) {
                    cursor = Math.floorMod(cursor + direction, boundary.size());
                    sector.add(boundary.get(cursor));
                    if (++steps > boundary.size()) throw new IllegalArgumentException("Crossed plateau mapping");
                }
                sector.add(ring.get(j));
                triangles.addAll(triangulate(sector));
            }
            double covered = triangles.subList(start, triangles.size()).stream().mapToDouble(t -> Math.abs(area(t))).sum();
            double expected = Math.abs(area(boundary));
            if (steps != boundary.size() || expected <= 0 || Math.abs(covered - expected) > expected * 1e-7) {
                throw new IllegalArgumentException("Mapped sectors do not cover the boundary exactly");
            }
        }
        return triangles;
    }

    private static double cross(Vertex a, Vertex b, Vertex c) {
        return (b.x() - a.x()) * (c.z() - a.z()) - (b.z() - a.z()) * (c.x() - a.x());
    }

    private static double area(List<Vertex> ring) {
        double sum = 0;
        for (int i = 0; i < ring.size(); i++) {
            var a = ring.get(i); var b = ring.get((i + 1) % ring.size());
            sum += a.x() * b.z() - b.x() * a.z();
        }
        return sum / 2;
    }

    private static List<List<Vertex>> triangulate(List<Vertex> ring) {
        var remaining = new ArrayList<>(ring);
        var result = new ArrayList<List<Vertex>>();
        double sign = Math.signum(area(ring));
        while (remaining.size() > 3) {
            boolean found = false;
            for (int i = 0; i < remaining.size(); i++) {
                var a = remaining.get(Math.floorMod(i - 1, remaining.size()));
                var b = remaining.get(i); var c = remaining.get((i + 1) % remaining.size());
                if (cross(a, b, c) * sign <= 0) continue;
                boolean contains = remaining.stream().anyMatch(p -> p != a && p != b && p != c
                        && cross(a, b, p) * sign >= -1e-6 && cross(b, c, p) * sign >= -1e-6 && cross(c, a, p) * sign >= -1e-6);
                if (contains) continue;
                result.add(List.of(a, b, c)); remaining.remove(i); found = true; break;
            }
            if (!found) throw new IllegalArgumentException("Invalid mapped sector");
        }
        result.add(List.copyOf(remaining));
        return result;
    }
}
