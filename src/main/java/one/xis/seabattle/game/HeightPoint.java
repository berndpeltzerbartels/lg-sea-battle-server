package one.xis.seabattle.game;

import java.util.List;

record HeightPoint(
        double x,
        double z,
        double h,
        double radius,
        String falloff,
        List<Integer> basePointIndexes,
        String plateauGroupId,
        String basePlateauGroupId,
        Integer plateauOrder,
        String plateauBoundaryPointId
) {
    HeightPoint(double x, double z, double h, double radius, String falloff,
                List<Integer> basePointIndexes, String plateauGroupId, String basePlateauGroupId, Integer plateauOrder) {
        this(x, z, h, radius, falloff, basePointIndexes, plateauGroupId, basePlateauGroupId, plateauOrder, null);
    }
    HeightPoint(double x, double z, double h, double radius, String falloff) {
        this(x, z, h, radius, falloff, List.of(), null, null, null);
    }

    HeightPoint(double x, double z, double h, double radius, String falloff,
                List<Integer> basePointIndexes, String plateauGroupId, String basePlateauGroupId) {
        this(x, z, h, radius, falloff, basePointIndexes, plateauGroupId, basePlateauGroupId, null);
    }

    HeightPoint {
        basePointIndexes = basePointIndexes == null ? List.of() : List.copyOf(basePointIndexes);
        plateauGroupId = plateauGroupId == null || plateauGroupId.isBlank() ? null : plateauGroupId;
        basePlateauGroupId = basePlateauGroupId == null || basePlateauGroupId.isBlank() ? null : basePlateauGroupId;
        plateauOrder = plateauOrder == null || plateauOrder < 0 ? null : plateauOrder;
    }
}
