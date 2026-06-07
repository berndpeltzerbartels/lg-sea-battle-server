package one.xis.seabattle;

import one.xis.context.Service;

import java.util.List;

@Service
class WorldMapService {

    WorldMap world() {
        return new WorldMap(1, List.of(
                coastline("western_coast", -126, 58, 72, 66, 1, null, 0.22,
                        fjords(new Fjord(1.95, 0.2, 0.9))),
                island("north_island", -32, 54, 21, 1.1, 28, 22),
                island("east_island", 58, 10, 28, 0.9, 36, 27),
                island("south_island", 18, -76, 24, 1.25, 32, 25),
                island("far_island", -92, -26, 16, 0.75, 22, 17),
                coastline("volcanic_highland", 312, -214, 86, 74, 1.55, 34.0, 0.22,
                        new Caldera(0.38, 0.18, 18),
                        fjords(new Fjord(2.72, 0.17, 0.88), new Fjord(-1.18, 0.14, 0.76))),
                coastline("fjord_coast", 194, -344, 64, 54, 1.05, 10.0, 0.24,
                        fjords(new Fjord(0.42, 0.18, 0.82), new Fjord(-2.38, 0.13, 0.7))),
                island("outer_stack", 410, -315, 22, 1.0, 30, 23),
                island("needle_rocks", 240, -112, 17, 1.35, 24, 18),
                island("low_skerries", 118, -238, 14, 0.82, 20, 16),
                coastline("storm_peak", -430, -405, 78, 62, 2.05, 82.0, 0.2,
                        fjords(new Fjord(0.9, 0.13, 0.72))),
                island("storm_north_stack", -372, -312, 16, 1.15, 22, 17),
                island("storm_west_rocks", -536, -390, 18, 1.0, 25, 18),
                island("storm_south_skerries", -462, -512, 14, 0.9, 20, 16),
                island("storm_outer_needle", -318, -482, 12, 1.45, 18, 14),
                island("l_passage_west_arm", 486, -548, 24, 1.0, 38, 20),
                island("l_passage_north_arm", 536, -502, 22, 0.95, 22, 38),
                island("l_passage_outer_rock", 592, -555, 15, 1.2, 20, 16),
                coastline("northern_ridge", 24, 760, 220, 96, 1.28, 38.0, 0.24,
                        fjords(new Fjord(3.02, 0.12, 0.8), new Fjord(-2.52, 0.16, 0.68))),
                easternDeltaCoast(),
                island("delta_outer_bar", 632, 92, 13, 0.7, 19, 15),
                island("delta_split_rocks", 672, 164, 11, 0.8, 16, 13),
                island("delta_south_bar", 604, 18, 12, 0.65, 17, 14),
                coastline("southern_cliffs", 148, -855, 232, 118, 1.38, 24.0, 0.24,
                        fjords(new Fjord(0.12, 0.14, 0.78), new Fjord(-0.48, 0.11, 0.66))),
                island("southern_gate_rocks", -96, -706, 18, 1.15, 25, 18),
                island("southern_outer_stack", 332, -698, 20, 1.05, 28, 21),
                coastline("western_continent", -2350, 120, 820, 1750, 1.18, 46.0, 0.26,
                        fjords(
                                new Fjord(1.46, 0.1, 0.78),
                                new Fjord(1.18, 0.16, 0.62),
                                new Fjord(1.82, 0.13, 0.68),
                                new Fjord(2.22, 0.1, 0.55)
                        )),
                island("western_sound_stack", -1310, 520, 28, 1.2, 38, 28),
                island("western_south_rocks", -1460, -630, 22, 1.05, 30, 24)
        ));
    }

    private Landmass easternDeltaCoast() {
        return new Landmass(
                "coastline",
                "eastern_delta_coast",
                835,
                118,
                168,
                128,
                null,
                0.72,
                4.0,
                0.28,
                null,
                fjords(
                        new Fjord(-1.78, 0.2, 0.88),
                        new Fjord(-1.42, 0.15, 0.72),
                        new Fjord(-2.05, 0.13, 0.64)
                ),
                List.of(
                        new Waterway(new Point2(86, 8), new Point2(22, 0), 18),
                        new Waterway(new Point2(22, 0), new Point2(-52, -10), 30),
                        new Waterway(new Point2(-52, -10), new Point2(-154, -80), 18),
                        new Waterway(new Point2(-46, -3), new Point2(-164, 0), 20),
                        new Waterway(new Point2(-38, 6), new Point2(-138, 72), 17)
                ),
                List.of(new Lake(-48, -5, 32, 21))
        );
    }

    private Landmass island(String name, double x, double z, double radius, double heightScale, double rx, double rz) {
        return new Landmass("island", name, x, z, rx, rz, radius, heightScale, null, null, null, List.of(), List.of(), List.of());
    }

    private Landmass coastline(String name, double x, double z, double rx, double rz, double heightScale, Double peakBoost,
                              double coastRoughness, List<Fjord> fjords) {
        return coastline(name, x, z, rx, rz, heightScale, peakBoost, coastRoughness, null, fjords);
    }

    private Landmass coastline(String name, double x, double z, double rx, double rz, double heightScale, Double peakBoost,
                              double coastRoughness, Caldera caldera, List<Fjord> fjords) {
        return new Landmass(
                "coastline",
                name,
                x,
                z,
                rx,
                rz,
                null,
                heightScale,
                peakBoost,
                coastRoughness,
                caldera,
                fjords,
                List.of(),
                List.of()
        );
    }

    private List<Fjord> fjords(Fjord... fjords) {
        return List.of(fjords);
    }
}
