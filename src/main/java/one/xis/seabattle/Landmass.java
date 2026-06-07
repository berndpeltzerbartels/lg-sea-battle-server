package one.xis.seabattle;

import java.util.List;

record Landmass(
        String kind,
        String name,
        double x,
        double z,
        double rx,
        double rz,
        Double radius,
        double heightScale,
        Double peakBoost,
        Double coastRoughness,
        Caldera caldera,
        List<Fjord> fjords,
        List<Waterway> waterways,
        List<Lake> lakes
) {
}
