package one.xis.seabattle;

import one.xis.context.Service;

import java.util.ArrayList;
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
                coastline("southern_cliffs", 148, -855, 268, 134, 1.78, 20.0, 0.22,
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

    WorldMap denseWorld() {
        List<Landmass> landmasses = new ArrayList<>();
        for (Landmass landmass : world().landmasses()) {
            if (keepInDenseWorld(landmass)) {
                landmasses.add(landmass);
            }
        }
        landmasses.addAll(List.of(
                coastline("ember_volcano", -1180, -1120, 220, 165, 1.92, 138.0, 0.16,
                        new Caldera(0.34, 0.18, 15),
                        fjords(new Fjord(1.8, 0.1, 0.58), new Fjord(-0.65, 0.12, 0.64))),
                coastline("ash_volcano", 1160, 980, 230, 175, 1.85, 128.0, 0.16,
                        new Caldera(0.36, 0.18, 16),
                        fjords(new Fjord(-2.3, 0.11, 0.62), new Fjord(0.82, 0.1, 0.55))),
                coastline("needle_peak_dense", 430, -1435, 190, 142, 2.28, 170.0, 0.15,
                        fjords(new Fjord(2.58, 0.1, 0.56))),
                coastline("blackwater_basin", 1580, -555, 260, 205, 1.18, 30.0, 0.19,
                        fjords(new Fjord(2.9, 0.13, 0.64), new Fjord(-2.4, 0.13, 0.62))),
                coastline("blackwater_ridge_north", 1440, -340, 210, 118, 1.38, 56.0, 0.2,
                        fjords(new Fjord(2.65, 0.1, 0.5))),
                coastline("blackwater_ridge_east", 1760, -410, 190, 132, 1.28, 42.0, 0.21,
                        fjords(new Fjord(-2.55, 0.11, 0.56), new Fjord(0.8, 0.09, 0.46))),
                coastline("delta_head", -1550, 520, 260, 205, 1.08, 18.0, 0.22,
                        fjords(new Fjord(-0.38, 0.18, 0.82), new Fjord(0.08, 0.16, 0.72), new Fjord(-0.72, 0.13, 0.6))),
                coastline("delta_head_north_bank", -1480, 780, 235, 128, 1.24, 36.0, 0.22,
                        fjords(new Fjord(-0.2, 0.13, 0.58), new Fjord(2.4, 0.1, 0.46))),
                coastline("delta_head_south_bank", -1420, 275, 215, 125, 1.18, 28.0, 0.23,
                        fjords(new Fjord(0.1, 0.14, 0.62), new Fjord(-2.6, 0.1, 0.48))),
                coastline("raven_ridge", -1390, 1280, 230, 150, 1.48, 54.0, 0.22,
                        fjords(new Fjord(-2.65, 0.12, 0.58), new Fjord(0.45, 0.1, 0.52))),
                coastline("raven_ridge_west", -1590, 1370, 175, 116, 1.22, 32.0, 0.22,
                        fjords(new Fjord(1.95, 0.11, 0.48))),
                coastline("raven_ridge_south", -1280.0, 1025, 185, 118, 1.38, 42.0, 0.23,
                        fjords(new Fjord(-2.2, 0.12, 0.54))),
                coastline("greyhook_island", -860, -1260, 210, 145, 1.24, 34.0, 0.21,
                        fjords(new Fjord(1.82, 0.12, 0.62), new Fjord(-0.2, 0.1, 0.48))),
                coastline("greyhook_west_ridge", -1090, -1320, 180, 116, 1.22, 30.0, 0.23,
                        fjords(new Fjord(2.4, 0.1, 0.5))),
                coastline("greyhook_south_ridge", -765, -1500, 165, 118, 1.2, 28.0, 0.22,
                        fjords(new Fjord(-0.4, 0.1, 0.48))),
                coastline("redcliff_bank", -520, 1480, 190, 132, 1.22, 32.0, 0.2,
                        fjords(new Fjord(-1.9, 0.11, 0.54))),
                coastline("redcliff_east_bank", -330, 1515, 170, 112, 1.18, 26.0, 0.21,
                        fjords(new Fjord(-2.6, 0.1, 0.46))),
                coastline("sable_highland", 720, 1420, 225, 155, 1.32, 48.0, 0.21,
                        fjords(new Fjord(2.7, 0.12, 0.58), new Fjord(-0.9, 0.1, 0.48))),
                coastline("sable_south_spur", 610, 1210, 185, 118, 1.2, 30.0, 0.23,
                        fjords(new Fjord(2.9, 0.1, 0.48))),
                coastline("sable_east_spur", 945, 1320, 170, 122, 1.18, 28.0, 0.23,
                        fjords(new Fjord(-2.4, 0.1, 0.48))),
                coastline("eagle_sound", 1280, 520, 210, 148, 1.2, 28.0, 0.23,
                        fjords(new Fjord(-2.45, 0.14, 0.68), new Fjord(1.18, 0.1, 0.5))),
                coastline("eagle_sound_outer", 1490, 690, 180, 112, 1.16, 24.0, 0.22,
                        fjords(new Fjord(-2.9, 0.12, 0.52))),
                coastline("brass_island", 1360, -1180, 230, 158, 1.26, 38.0, 0.2,
                        fjords(new Fjord(2.32, 0.12, 0.58), new Fjord(-1.1, 0.11, 0.52))),
                coastline("brass_west_spur", 1140, -1110, 175, 112, 1.14, 24.0, 0.22,
                        fjords(new Fjord(2.65, 0.1, 0.5))),
                coastline("brass_south_spur", 1445, -1395, 180, 122, 1.2, 28.0, 0.22,
                        fjords(new Fjord(-0.65, 0.11, 0.52))),
                coastline("sealight_bank", -1780, -220, 190, 138, 1.12, 24.0, 0.23,
                        fjords(new Fjord(0.28, 0.12, 0.6))),
                coastline("sealight_west_bank", -2005, -210, 170, 118, 1.1, 20.0, 0.23,
                        fjords(new Fjord(0.12, 0.1, 0.48))),
                coastline("copper_sound", 260, 520, 185, 132, 1.18, 28.0, 0.21,
                        fjords(new Fjord(-2.1, 0.11, 0.56), new Fjord(0.72, 0.1, 0.48))),
                coastline("copper_north_ridge", 260, 820, 158, 104, 1.24, 34.0, 0.22,
                        fjords(new Fjord(-2.2, 0.1, 0.48))),
                coastline("nightfall_island", -940, 1760, 175, 126, 1.2, 30.0, 0.2,
                        fjords(new Fjord(-0.4, 0.1, 0.52))),
                coastline("outer_reef_highland", 1980, -920, 185, 132, 1.16, 26.0, 0.22,
                        fjords(new Fjord(2.75, 0.12, 0.58))),
                island("kestrel_island", -260, 560, 30, 1.15, 58, 36),
                island("kestrel_east", 40, 570, 24, 1.05, 44, 28),
                island("kestrel_west", -545, 610, 21, 1.05, 38, 25),
                island("harbor_outer", -90, -365, 22, 1.0, 40, 24),
                island("harbor_east_turn", 315, -620, 20, 1.12, 34, 24),
                island("crow_inner", 720, 285, 23, 1.05, 42, 26),
                island("crow_west", 390, 660, 21, 1.08, 36, 24),
                island("crow_outer_bank", 905, 430, 20, 0.95, 34, 22),
                island("wolf_outer", -1160, 760, 25, 1.08, 42, 28),
                island("wolf_inner", -680, 510, 20, 1.0, 36, 22),
                island("southern_teeth_d", 505, -1285, 20, 1.15, 34, 20),
                island("southern_teeth_e", 1080, -940, 22, 1.05, 38, 24),
                island("ember_gate", -650, -1080, 22, 1.05, 38, 24),
                island("ember_outer", -1380, -920, 28, 0.98, 50, 28),
                island("ash_gate", 820, 1010, 22, 1.08, 38, 24),
                island("ash_outer", 1510, 760, 28, 1.0, 50, 30),
                island("midway_bank", 120, 170, 27, 1.0, 52, 30),
                island("midway_south", -115, -105, 22, 1.0, 38, 24),
                island("midway_west", -465, -80, 23, 1.06, 40, 25),
                island("midway_east", 610, -80, 22, 1.06, 38, 24),
                island("north_chain_a", -720, 1110, 24, 1.08, 42, 28),
                island("north_chain_d", 650, 1110, 24, 1.08, 42, 28),
                island("west_gap_outer", -1710, -650, 34, 1.0, 66, 36),
                island("east_gap_outer", 1740, 260, 36, 1.0, 68, 38),
                island("far_west_bank", -2060, -520, 42, 1.0, 78, 42),
                island("far_east_bank", 2110, 210, 46, 1.02, 82, 44),
                island("north_watch_bank", 278, 2235, 46, 1.02, 44, 82),
                island("south_watch_bank", 920, -2700, 46, 1.02, 44, 82)
        ));
        return new WorldMap(15, landmasses);
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
        double shapeScale = steepRock ? 0.34 : 1.0;
        double shapeRx = rx * shapeScale;
        double shapeRz = rz * shapeScale;
        return new Landmass(
                "island",
                name,
                x,
                z,
                shapeRx,
                shapeRz,
                shapeRx,
                shapeRz,
                shapeRx,
                shapeRz,
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

    private boolean keepInDenseWorld(Landmass landmass) {
        if (!"island".equals(landmass.kind()) || !isSteepRock(landmass.name())) {
            return true;
        }
        return landmass.name().equals("outer_stack")
                || landmass.name().equals("needle_rocks")
                || landmass.name().equals("western_sound_stack");
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
                rx,
                rz,
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
        return List.of();
    }

    private Waterway waterway(double fromX, double fromZ, double toX, double toZ, double width) {
        return new Waterway(new Point2(fromX, fromZ), new Point2(toX, toZ), width);
    }
}
