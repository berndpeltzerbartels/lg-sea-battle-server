package one.xis.seabattle.game;

import java.util.List;

record Landmass(
        String kind,
        String name,
        double x,
        double z,
        double rx,
        double rz,
        double navigationRx,
        double navigationRz,
        double shallowRx,
        double shallowRz,
        Double radius,
        double heightScale,
        Double peakBoost,
        Double coastRoughness,
        Caldera caldera,
        List<Fjord> fjords,
        List<Waterway> waterways,
        List<Lake> lakes,
        List<Point2> polygon,
        List<HeightPoint> heightPoints,
        double seaFloorHeight,
        double baseHeight,
        String baseLevel,
        String baseLandmassId,
        String basePlateauGroupId,
        String material,
        List<MaterialZone> materialZones
) {
    Landmass(
            String kind,
            String name,
            double x,
            double z,
            double rx,
            double rz,
            double navigationRx,
            double navigationRz,
            double shallowRx,
            double shallowRz,
            Double radius,
            double heightScale,
            Double peakBoost,
            Double coastRoughness,
            Caldera caldera,
            List<Fjord> fjords,
            List<Waterway> waterways,
            List<Lake> lakes,
            List<Point2> polygon,
            List<HeightPoint> heightPoints,
            double seaFloorHeight
    ) {
        this(
                kind,
                name,
                x,
                z,
                rx,
                rz,
                navigationRx,
                navigationRz,
                shallowRx,
                shallowRz,
                radius,
                heightScale,
                peakBoost,
                coastRoughness,
                caldera,
                fjords,
                waterways,
                lakes,
                polygon,
                heightPoints,
                seaFloorHeight,
                seaFloorHeight,
                "seaFloor",
                null,
                null,
                "grass",
                List.of()
        );
    }

    Landmass(
            String kind,
            String name,
            double x,
            double z,
            double rx,
            double rz,
            double navigationRx,
            double navigationRz,
            double shallowRx,
            double shallowRz,
            Double radius,
            double heightScale,
            Double peakBoost,
            Double coastRoughness,
            Caldera caldera,
            List<Fjord> fjords,
            List<Waterway> waterways,
            List<Lake> lakes
    ) {
        this(
                kind,
                name,
                x,
                z,
                rx,
                rz,
                navigationRx,
                navigationRz,
                shallowRx,
                shallowRz,
                radius,
                heightScale,
                peakBoost,
                coastRoughness,
                caldera,
                fjords,
                waterways,
                lakes,
                List.of(),
                List.of(),
                0,
                0,
                "seaFloor",
                null,
                null,
                "grass",
                List.of()
        );
    }

    Landmass {
        fjords = fjords == null ? List.of() : List.copyOf(fjords);
        waterways = waterways == null ? List.of() : List.copyOf(waterways);
        lakes = lakes == null ? List.of() : List.copyOf(lakes);
        polygon = polygon == null ? List.of() : List.copyOf(polygon);
        heightPoints = heightPoints == null ? List.of() : List.copyOf(heightPoints);
        baseLevel = baseLevel == null || baseLevel.isBlank() ? "seaFloor" : baseLevel;
        baseLandmassId = baseLandmassId == null || baseLandmassId.isBlank() ? null : baseLandmassId;
        basePlateauGroupId = basePlateauGroupId == null || basePlateauGroupId.isBlank() ? null : basePlateauGroupId;
        material = material == null || material.isBlank() ? "grass" : material;
        materialZones = materialZones == null ? List.of() : List.copyOf(materialZones);
    }
}
