package one.xis.seabattle;

import one.xis.context.Service;

import java.util.List;

@Service
class WorldMapService {

    WorldMap world() {
        return new WorldMap(8, List.of(
                coastline("western_coast", -126, 58, 88, 76, 1, null, 0.22,
                        fjords(new Fjord(1.95, 0.2, 0.9))),
                island("north_island", -32, 54, 24, 1.1, 34, 26),
                island("east_island", 58, 10, 31, 0.9, 44, 31),
                island("south_island", 18, -76, 27, 1.25, 38, 30),
                island("far_island", -92, -26, 18, 0.75, 27, 20),
                island("central_hook_rock", -188, -96, 16, 1.15, 26, 18),
                island("central_hook_bank", -238, -8, 21, 0.95, 38, 22),
                island("central_hook_outer", -174, 104, 14, 1.22, 20, 16),
                island("central_gate_south", 154, -132, 17, 1.05, 30, 18),
                island("central_gate_north", 184, 86, 19, 0.98, 28, 24),
                coastline("volcanic_highland", 312, -214, 104, 86, 1.55, 34.0, 0.22,
                        new Caldera(0.38, 0.18, 18),
                        fjords(new Fjord(2.72, 0.17, 0.88), new Fjord(-1.18, 0.14, 0.76))),
                coastline("fjord_coast", 168, -410, 72, 58, 1.05, 10.0, 0.24,
                        fjords(new Fjord(0.42, 0.18, 0.82), new Fjord(-2.38, 0.13, 0.7))),
                island("outer_stack", 410, -315, 22, 1.0, 30, 23),
                island("needle_rocks", 240, -112, 17, 1.35, 24, 18),
                island("low_skerries", 118, -238, 14, 0.82, 20, 16),
                coastline("storm_peak", -430, -405, 96, 74, 2.05, 82.0, 0.2,
                        fjords(new Fjord(0.9, 0.13, 0.72))),
                island("storm_north_stack", -372, -312, 19, 1.15, 28, 20),
                island("storm_west_rocks", -536, -390, 21, 1.0, 31, 22),
                island("storm_south_skerries", -462, -512, 17, 0.9, 25, 18),
                island("storm_outer_needle", -318, -482, 12, 1.45, 18, 14),
                island("storm_gate_east", -250, -360, 17, 1.05, 28, 17),
                island("storm_gate_west", -640, -474, 15, 0.95, 24, 16),
                island("l_passage_west_arm", 486, -548, 27, 1.0, 44, 23),
                island("l_passage_north_arm", 536, -502, 25, 0.95, 25, 44),
                island("l_passage_outer_rock", 592, -555, 15, 1.2, 20, 16),
                island("middle_skerry_gate", 690, -560, 16, 1.0, 22, 16),
                island("middle_skerry_hook", 760, -625, 13, 0.9, 18, 14),
                island("middle_skerry_back", 820, -500, 18, 1.15, 25, 18),
                island("middle_skerry_needle", 735, -430, 12, 1.45, 16, 13),
                island("middle_skerry_south_bar", 690, -710, 14, 0.86, 22, 13),
                island("middle_skerry_inner_rock", 840, -660, 11, 1.2, 15, 12),
                island("middle_skerry_east_gate", 920, -560, 17, 1.0, 25, 16),
                island("middle_skerry_north_turn", 608, -390, 18, 1.0, 29, 18),
                island("middle_skerry_south_turn", 832, -760, 17, 0.9, 31, 15),
                coastline("mist_gate_north", 640, -850, 96, 62, 1.24, 24.0, 0.24,
                        fjords(new Fjord(2.9, 0.12, 0.62), new Fjord(-2.55, 0.13, 0.58))),
                coastline("mist_gate_south", 760, -1040, 136, 82, 1.34, 31.0, 0.22,
                        fjords(new Fjord(0.42, 0.13, 0.68), new Fjord(-0.18, 0.1, 0.56))),
                coastline("mist_gate_east", 980, -890, 82, 116, 1.18, 18.0, 0.26,
                        fjords(new Fjord(-1.72, 0.16, 0.72))),
                coastline("northern_ridge", 24, 760, 250, 112, 1.28, 38.0, 0.24,
                        fjords(new Fjord(3.02, 0.12, 0.8), new Fjord(-2.52, 0.16, 0.68))),
                island("north_outer_rocks", 410, 690, 17, 1.05, 23, 17),
                island("north_inner_stack", -250, 620, 15, 1.25, 20, 15),
                island("north_crook_rock", 250, 560, 13, 0.95, 18, 14),
                island("north_crook_stack", 330, 840, 16, 1.25, 22, 16),
                island("north_maze_west", -560, 820, 23, 0.98, 40, 22),
                island("north_maze_inner", -610, 950, 18, 1.12, 28, 20),
                island("north_maze_outer", -520, 1125, 21, 0.9, 35, 23),
                island("north_maze_east", -305, 870, 17, 1.2, 24, 18),
                coastline("north_sound_west", -420, 1010, 130, 82, 1.18, 24.0, 0.25,
                        fjords(new Fjord(1.15, 0.14, 0.7))),
                coastline("north_sound_east", -145, 1120, 112, 96, 1.42, 42.0, 0.22,
                        fjords(new Fjord(-2.05, 0.13, 0.68), new Fjord(2.62, 0.11, 0.55))),
                coastline("north_sound_outer", 110, 1035, 84, 68, 1.08, 16.0, 0.26,
                        fjords(new Fjord(-1.35, 0.12, 0.58))),
                easternDeltaCoast(),
                island("delta_outer_bar", 632, 92, 13, 0.7, 19, 15),
                island("delta_split_rocks", 672, 164, 11, 0.8, 16, 13),
                island("delta_south_bar", 604, 18, 12, 0.65, 17, 14),
                island("delta_north_sentinel", 1010, 330, 16, 1.0, 22, 16),
                island("delta_far_stack", 1160, 120, 14, 1.35, 19, 14),
                island("delta_reef_chain_a", 1110, 250, 12, 0.92, 18, 12),
                island("delta_reef_chain_b", 1215, 300, 15, 1.1, 22, 15),
                island("delta_reef_chain_c", 1270, 180, 10, 1.3, 15, 10),
                coastline("southern_cliffs", 148, -855, 268, 134, 1.38, 24.0, 0.24,
                        fjords(new Fjord(0.12, 0.14, 0.78), new Fjord(-0.48, 0.11, 0.66))),
                island("southern_gate_rocks", -96, -706, 18, 1.15, 25, 18),
                island("southern_outer_stack", 332, -698, 20, 1.05, 28, 21),
                island("southern_inner_turn", -168, -892, 20, 1.0, 32, 19),
                island("southern_outer_turn", 438, -908, 19, 1.08, 31, 20),
                coastline("crown_mountain", 1510, -1490, 245, 178, 1.95, 175.0, 0.16,
                        fjords(new Fjord(2.55, 0.1, 0.58), new Fjord(-0.95, 0.12, 0.62))),
                island("crown_west_skerry", 1160, -1610, 18, 1.05, 26, 17),
                island("crown_west_gate", 1085, -1440, 15, 1.15, 22, 15),
                island("crown_south_rocks", 1520, -1845, 20, 0.95, 29, 19),
                island("crown_south_bar", 1340, -1765, 14, 0.9, 22, 13),
                island("crown_east_stack", 1835, -1445, 17, 1.35, 22, 16),
                island("crown_north_stack", 1540, -1200, 16, 1.25, 22, 16),
                island("crown_outer_needle", 1940, -1640, 12, 1.55, 18, 12),
                westernContinent(),
                island("western_sound_stack", -1310, 520, 28, 1.2, 38, 28),
                island("western_south_rocks", -1460, -630, 22, 1.05, 30, 24),
                coastline("western_sound_north_bank", -1040, 720, 142, 88, 1.18, 21.0, 0.24,
                        fjords(new Fjord(-2.8, 0.13, 0.62))),
                coastline("western_sound_south_bank", -1025, 420, 128, 86, 1.32, 36.0, 0.23,
                        fjords(new Fjord(2.62, 0.12, 0.58), new Fjord(-0.62, 0.11, 0.54)))
        ));
    }

    private Landmass westernContinent() {
        return coastline(
                "western_continent",
                -2350,
                120,
                820,
                1750,
                1.18,
                46.0,
                0.26,
                null,
                fjords(
                        new Fjord(1.46, 0.1, 0.78),
                        new Fjord(1.18, 0.16, 0.62),
                        new Fjord(1.82, 0.13, 0.68),
                        new Fjord(2.22, 0.1, 0.55)
                )
        );
    }

    private Landmass easternDeltaCoast() {
        return coastline(
                "eastern_delta_coast",
                835,
                118,
                168,
                128,
                0.72,
                4.0,
                0.28,
                null,
                fjords(
                        new Fjord(-1.78, 0.2, 0.88),
                        new Fjord(-1.42, 0.15, 0.72),
                        new Fjord(-2.05, 0.13, 0.64)
                ),
                List.of(),
                List.of()
        );
    }

    private Landmass island(String name, double x, double z, double radius, double heightScale, double rx, double rz) {
        boolean steepRock = isSteepRock(name);
        double navigationScale = steepRock ? 0.42 : 0.58;
        double shallowScale = steepRock ? 0.5 : 0.9;
        return new Landmass(
                "island",
                name,
                x,
                z,
                rx,
                rz,
                rx * navigationScale,
                rz * navigationScale,
                rx * shallowScale,
                rz * shallowScale,
                radius,
                heightScale,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private boolean isSteepRock(String name) {
        return name.contains("rock")
                || name.contains("rocks")
                || name.contains("stack")
                || name.contains("needle")
                || name.contains("skerry")
                || name.contains("skerries");
    }

    private Landmass coastline(String name, double x, double z, double rx, double rz, double heightScale, Double peakBoost,
                              double coastRoughness, List<Fjord> fjords) {
        return coastline(name, x, z, rx, rz, heightScale, peakBoost, coastRoughness, null, fjords);
    }

    private Landmass coastline(String name, double x, double z, double rx, double rz, double heightScale, Double peakBoost,
                              double coastRoughness, Caldera caldera, List<Fjord> fjords) {
        return coastline(name, x, z, rx, rz, heightScale, peakBoost, coastRoughness, caldera, fjords, List.of(), List.of());
    }

    private Landmass coastline(String name, double x, double z, double rx, double rz, double heightScale, Double peakBoost,
                              double coastRoughness, Caldera caldera, List<Fjord> fjords,
                              List<Waterway> waterways, List<Lake> lakes) {
        return new Landmass(
                "coastline",
                name,
                x,
                z,
                rx,
                rz,
                rx,
                rz,
                rx * 1.12,
                rz * 1.12,
                null,
                heightScale,
                peakBoost,
                coastRoughness,
                caldera,
                fjords,
                waterways,
                lakes
        );
    }

    private List<Fjord> fjords(Fjord... fjords) {
        return List.of(fjords);
    }
}
