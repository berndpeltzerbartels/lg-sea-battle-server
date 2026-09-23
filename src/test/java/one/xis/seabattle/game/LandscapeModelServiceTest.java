package one.xis.seabattle.game;

import one.xis.UploadedFile;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandscapeModelServiceTest {

    @Test
    void unmappedPlateausCannotBeUploadedOrLoadedFromLegacyStorage() {
        var repository = new MemoryLandscapeRepository();
        var service = new LandscapeModelService(repository);
        String source = """
                {"format":"game-landscape-designer.v2","islands":[{"id":"invalid",
                "polygon":[{"x":-100,"z":-100},{"x":100,"z":-100},{"x":0,"z":100}],
                "heights":[{"x":-10,"z":-10,"h":2,"plateauGroupId":"p"},
                {"x":10,"z":-10,"h":2,"plateauGroupId":"p"},
                {"x":0,"z":10,"h":2,"plateauGroupId":"p"}]}]}
                """;
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                service.saveUpload(new UploadedFile("landscapeFile", "invalid.json", "application/json", source.getBytes(StandardCharsets.UTF_8))));
        assertEquals(0, repository.saveCount);
        repository.save(new LandscapeModelEntity("old", "Old", "game-landscape-designer.v2", source, "{}", "[]", LocalDateTime.now()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.find("old"));
        assertEquals(1, repository.saveCount);
    }

    @Test
    void uploadedEditorLandscapeCanRestartGameSession() {
        MemoryLandscapeRepository repository = new MemoryLandscapeRepository();
        LandscapeModelService service = new LandscapeModelService(repository);
        LandscapeModelService.LandscapeModelSummary summary = service.saveUpload(new UploadedFile(
                "landscapeFile",
                "fjord.json",
                "application/json",
                """
                        {
                          "format": "game-landscape-designer.v2",
                          "name": "Test Fjord",
                          "createdAt": "2026-09-22T18:30:00.000Z",
                          "islands": [
                            {
                              "id": "north",
                              "name": "North Ridge",
                              "material": "grass",
                              "seaFloorHeight": -80,
                              "materialZones": [
                                {
                                  "id": "north-beach",
                                  "material": "sand",
                                  "polygon": [
                                    { "x": -90, "z": -50 },
                                    { "x": 40, "z": -60 },
                                    { "x": 35, "z": 10 },
                                    { "x": -95, "z": 20 }
                                  ]
                                }
                              ],
                              "polygon": [
                                { "x": -100, "z": -50 },
                                { "x": 120, "z": -70 },
                                { "x": 140, "z": 80 },
                                { "x": -90, "z": 90 }
                              ],
	                              "heights": [
	                                { "x": 20, "z": 10, "h": 4500, "radius": 1800, "falloff": "plateau" }
	                              ],
	                              "landmarks": [
	                                { "id": "striped-light", "type": "lighthouse-striped", "name": "Rot-Weiss", "x": 20, "y": 4500, "z": 10 },
	                                { "id": "outer-rock", "type": "rock", "name": "Felsen", "x": 145, "y": -30, "z": 85, "scale": 1.4 }
	                              ]
	                            }
	                          ]
                        }
                        """.getBytes(StandardCharsets.UTF_8)
        ));
        GameStateService gameStateService = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                service,
                new GameSelectionService(new MemoryGamePropertyRepository()),
                new RadarService(),
                new NavigationService()
        );

        gameStateService.resetToLandscapeModel(summary.id());

        assertEquals("Test Fjord", summary.name());
        assertEquals(OffsetDateTime.parse("2026-09-22T18:30:00.000Z")
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(), summary.createdAt());
        assertEquals(1, summary.landmassCount());
        assertEquals(summary.id(), gameStateService.landscapeModelId());
        assertEquals("Test Fjord", gameStateService.landscapeModelName());
        assertEquals(1, gameStateService.landmassCount());
        WorldMap worldMap = service.find(summary.id()).orElseThrow().worldMap();
        assertEquals(2, worldMap.mapObjects().size());
        assertTrue(worldMap.mapObjects().stream().anyMatch(object -> "lighthouse-striped".equals(object.type())));
        assertTrue(worldMap.mapObjects().stream().anyMatch(object -> "rock".equals(object.type())));
        assertTrue(worldMap.mapObjects().stream().anyMatch(object -> "striped_light".equals(object.id()) && object.y() == 4500));
        assertEquals(4, worldMap.landmasses().get(0).polygon().size());
        assertEquals("grass", worldMap.landmasses().get(0).material());
        assertEquals(1, worldMap.landmasses().get(0).materialZones().size());
        assertEquals("sand", worldMap.landmasses().get(0).materialZones().get(0).material());
        assertEquals(1, worldMap.landmasses().get(0).heightPoints().size());
        assertEquals(4500, worldMap.landmasses().get(0).heightPoints().get(0).h());
        assertEquals(1800, worldMap.landmasses().get(0).heightPoints().get(0).radius());
        assertEquals("plateau", worldMap.landmasses().get(0).heightPoints().get(0).falloff());
        LandscapeModelEntity entity = repository.findById(summary.id()).orElseThrow();
        assertNotNull(entity.getRespawnCandidatesJson());
        assertTrue(entity.getRespawnCandidatesJson().contains("\"x\""));
        assertFalse(service.find(summary.id()).orElseThrow().respawnCandidates().isEmpty());
        assertEquals(service.find(summary.id()), new LandscapeModelService(repository).find(summary.id()));
        assertEquals(1, repository.saveCount);
        assertTrue(LandGeometry.maxTerrainHeight(worldMap) >= 4500);
        assertTrue(LandGeometry.terrainHeightAt(new Vector2(20, 10), worldMap) >= 4500);
        assertNotNull(gameStateService.snapshot());
        assertTrue(service.delete(summary.id()));
        assertTrue(service.find(summary.id()).isEmpty());
    }

    @Test
    void legacyLandscapeIsPreparedAndPersistedOnce() {
        MemoryLandscapeRepository repository = new MemoryLandscapeRepository();
        LandscapeModelService service = new LandscapeModelService(repository);
        repository.save(new LandscapeModelEntity(
                "stale-world-map",
                "Reconvert Me",
                "game-landscape-designer.v2",
                """
                        {
                          "format": "game-landscape-designer.v2",
                          "name": "Reconvert Me",
                          "islands": [
                            {
                              "id": "hill",
                              "name": "Hill",
                              "seaFloorHeight": -80,
                              "polygon": [
                                { "x": -100, "z": -100 },
                                { "x": 100, "z": -100 },
                                { "x": 100, "z": 100 },
                                { "x": -100, "z": 100 }
                              ],
                              "heights": [
                                { "x": 0, "z": 0, "h": 360, "radius": 180, "falloff": "hill" }
                              ],
                              "landmarks": [
                                { "id": "hill-light", "type": "lighthouse-striped", "name": "Rot-Weiss", "x": 0, "y": 360, "z": 0 }
                              ]
                            }
                          ]
                        }
                        """,
                """
                        {
                          "version": 1,
                          "landmasses": [],
                          "mapObjects": [
                            { "id": "hill_light", "type": "lighthouse-striped", "name": "Rot-Weiss", "x": 0, "z": 0, "yOffset": 0 }
                          ]
                        }
                        """,
                null,
                LocalDateTime.now()
        ));

        var model = service.find("stale-world-map").orElseThrow();
        assertEquals(1, model.worldMap().landmasses().size());
        assertFalse(model.respawnCandidates().isEmpty());
        assertEquals(2, repository.saveCount);
        var reloaded = new LandscapeModelService(repository).find("stale-world-map").orElseThrow();
        assertEquals(model, reloaded);
        assertEquals(2, repository.saveCount);
    }

    @Test
    void persistedPreparationSurvivesRestartAndListingWithoutRebuilding() {
        MemoryLandscapeRepository repository = preparedRepository();
        var before = new LandscapeModelService(repository).find("fixture").orElseThrow();
        assertEquals(2, repository.saveCount);
        var restarted = new LandscapeModelService(repository);
        assertEquals(1, restarted.summaries().get(0).landmassCount());
        assertEquals(before, restarted.find("fixture").orElseThrow());
        assertEquals(2, repository.saveCount);
    }

    @Test
    void outdatedOrDamagedPreparationIsRebuiltFromOriginal() {
        for (String damage : List.of("version", "source", "map", "respawn", "invalid-json")) {
            MemoryLandscapeRepository repository = preparedRepository();
            var expected = new LandscapeModelService(repository).find("fixture").orElseThrow();
            var row = repository.findById("fixture").orElseThrow();
            var stored = com.google.gson.JsonParser.parseString(row.getWorldMapJson()).getAsJsonObject();
            switch (damage) {
                case "version" -> stored.getAsJsonObject("preparation").addProperty("version", -1);
                case "source" -> row.setOriginalJson(row.getOriginalJson().replace("120", "180"));
                case "map" -> stored.add("landmasses", new com.google.gson.JsonArray());
                case "respawn" -> row.setRespawnCandidatesJson("[]");
                default -> { }
            }
            row.setWorldMapJson(damage.equals("invalid-json") ? "broken" : stored.toString());
            var rebuilt = new LandscapeModelService(repository).find("fixture").orElseThrow();
            assertEquals(3, repository.saveCount, damage);
            if (damage.equals("source")) {
                assertEquals(180, rebuilt.worldMap().landmasses().get(0).heightPoints().get(0).h());
            } else {
                assertEquals(expected, rebuilt, damage);
            }
            assertEquals(rebuilt, new LandscapeModelService(repository).find("fixture").orElseThrow());
            assertEquals(3, repository.saveCount, damage);
        }
    }

    private MemoryLandscapeRepository preparedRepository() {
        MemoryLandscapeRepository repository = new MemoryLandscapeRepository();
        repository.save(new LandscapeModelEntity("fixture", "Fixture", "game-landscape-designer.v2", """
                {"format":"game-landscape-designer.v2","islands":[{
                  "id":"hill","polygon":[{"x":-100,"z":-100},{"x":100,"z":-100},
                    {"x":100,"z":100},{"x":-100,"z":100}],
                  "heights":[{"x":0,"z":0,"h":120}]
                }]}
                """, "{}", null, LocalDateTime.now()));
        return repository;
    }

    @Test
    void storedAdminSelectionIsRestoredWhenGameStateServiceStarts() {
        MemoryLandscapeRepository landscapeRepository = new MemoryLandscapeRepository();
        LandscapeModelService landscapeModelService = new LandscapeModelService(landscapeRepository);
        LandscapeModelService.LandscapeModelSummary summary = landscapeModelService.saveUpload(new UploadedFile(
                "landscapeFile",
                "persisted.json",
                "application/json",
                """
                        {
                          "format": "game-landscape-designer.v2",
                          "name": "Persisted Test",
                          "islands": [
                            {
                              "id": "persisted",
                              "name": "Persisted",
                              "seaFloorHeight": -80,
                              "polygon": [
                                { "x": -100, "z": -100 },
                                { "x": 100, "z": -100 },
                                { "x": 100, "z": 100 },
                                { "x": -100, "z": 100 }
                              ],
                              "heights": [
                                { "x": 0, "z": 0, "h": 220, "radius": 180, "falloff": "hill" }
                              ]
                            }
                          ]
                        }
                        """.getBytes(StandardCharsets.UTF_8)
        ));
        GameSelectionService selectionService = new GameSelectionService(new MemoryGamePropertyRepository());
        selectionService.rememberSetupId("two-ship-duel");
        selectionService.rememberLandscapeModelId(summary.id());

        GameStateService gameStateService = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                landscapeModelService,
                selectionService,
                new RadarService(),
                new NavigationService()
        );

        assertEquals("two-ship-duel", gameStateService.setupId());
        assertEquals(summary.id(), gameStateService.landscapeModelId());
        assertEquals("Persisted Test", gameStateService.landscapeModelName());
        assertEquals(1, gameStateService.landmassCount());
        assertEquals(2, gameStateService.snapshot().ships().size());
    }

    @Test
    void authoredUnderwaterTerrainDoesNotBlockNavigation() {
        Landmass landmass = new Landmass(
                "island",
                "submerged_bank",
                0,
                0,
                500,
                500,
                500,
                500,
                500,
                500,
                null,
                1,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new Point2(-500, -500),
                        new Point2(500, -500),
                        new Point2(500, 500),
                        new Point2(-500, 500)
                ),
                List.of(new HeightPoint(0, 0, -35, 420, "plateau")),
                -130
        );
        WorldMap worldMap = new WorldMap(1, List.of(landmass));

        assertEquals(0, LandGeometry.terrainHeightAt(new Vector2(0, 0), worldMap), 0.001);
        assertFalse(LandGeometry.isBlocked(new Vector2(0, 0), worldMap));
        assertFalse(LandGeometry.isBlockedAtOrAbove(new Vector2(0, 0), worldMap, -20));
        assertTrue(LandGeometry.isBlockedAtOrAbove(new Vector2(0, 0), worldMap, -40));
    }

    @Test
    void navigationCollisionUsesVehicleDepthForAuthoredTerrain() {
        Landmass landmass = new Landmass(
                "island",
                "shallow_underwater_ridge",
                0,
                0,
                500,
                500,
                500,
                500,
                500,
                500,
                null,
                1,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new Point2(-500, -500),
                        new Point2(500, -500),
                        new Point2(500, 500),
                        new Point2(-500, 500)
                ),
                List.of(new HeightPoint(0, 0, -2, 420, "plateau")),
                -130
        );
        WorldMap worldMap = new WorldMap(1, List.of(landmass));
        NavigationService navigationService = new NavigationService();

        assertFalse(navigationService.isShipBlocked(new Vector2(0, 0), 0, worldMap, "surface"));
        assertFalse(navigationService.isShipBlocked(new Vector2(0, 0), 0, worldMap, "periscope"));
        assertTrue(navigationService.isShipBlocked(new Vector2(0, 0), 0, worldMap, "submerged"));
    }

    @Test
    void multipleAuthoredPeaksKeepTheirOwnFootprintsWithoutInventingAnotherPeak() {
        Landmass landmass = new Landmass(
                "island",
                "two_peaks",
                0,
                0,
                600,
                600,
                600,
                600,
                600,
                600,
                null,
                1,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new Point2(-600, -600),
                        new Point2(600, -600),
                        new Point2(600, 600),
                        new Point2(-600, 600)
                ),
                List.of(
                        new HeightPoint(-220, 0, 120, 300, "hill"),
                        new HeightPoint(220, 0, 120, 300, "hill")
                ),
                -80
        );
        WorldMap worldMap = new WorldMap(1, List.of(landmass));

        assertEquals(120, LandGeometry.terrainHeightAt(new Vector2(-220, 0), worldMap), 0.001);
        assertEquals(120, LandGeometry.terrainHeightAt(new Vector2(220, 0), worldMap), 0.001);
        assertTrue(LandGeometry.terrainHeightAt(new Vector2(0, 0), worldMap) < 120);
        for (int x = -550; x <= 550; x += 25) {
            assertTrue(LandGeometry.terrainHeightAt(new Vector2(x, 0), worldMap) <= 120.001);
        }
    }

    @Test
    void authoredPeakOnPlateauUsesPlateauFootprintForCollisionHeight() {
        Landmass landmass = new Landmass(
                "island",
                "plateau_peak",
                0,
                0,
                500,
                500,
                500,
                500,
                500,
                500,
                null,
                1,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new Point2(-500, -500),
                        new Point2(500, -500),
                        new Point2(500, 500),
                        new Point2(-500, 500)
                ),
                List.of(
                        new HeightPoint(-260, -220, 10, 260, "plateau", List.of(), "beach-flat", null),
                        new HeightPoint(260, -220, 10, 260, "plateau", List.of(), "beach-flat", null),
                        new HeightPoint(260, 220, 10, 260, "plateau", List.of(), "beach-flat", null),
                        new HeightPoint(-260, 220, 10, 260, "plateau", List.of(), "beach-flat", null),
                        new HeightPoint(0, 0, 160, 40, "hill", List.of(), null, "beach-flat")
                ),
                -90
        );
        WorldMap worldMap = new WorldMap(1, List.of(landmass));

        assertEquals(160, LandGeometry.terrainHeightAt(new Vector2(0, 0), worldMap), 0.001);
        assertTrue(LandGeometry.terrainHeightAt(new Vector2(200, 0), worldMap) > 20);
        assertTrue(LandGeometry.isBlocked(new Vector2(200, 0), worldMap));
    }

    @Test
    void authoredTerrainOnlyBlocksWhereItRisesAboveSeaLevel() {
        Landmass landmass = new Landmass(
                "island",
                "emergent_bank",
                0,
                0,
                500,
                500,
                500,
                500,
                500,
                500,
                null,
                1,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new Point2(-500, -500),
                        new Point2(500, -500),
                        new Point2(500, 500),
                        new Point2(-500, 500)
                ),
                List.of(new HeightPoint(0, 0, 145, 220, "hill")),
                -90
        );
        WorldMap worldMap = new WorldMap(1, List.of(landmass));

        assertTrue(LandGeometry.terrainHeightAt(new Vector2(0, 0), worldMap) > 100);
        assertTrue(LandGeometry.isBlocked(new Vector2(0, 0), worldMap));
        assertEquals(0, LandGeometry.terrainHeightAt(new Vector2(420, 420), worldMap), 0.001);
        assertFalse(LandGeometry.isBlocked(new Vector2(420, 420), worldMap));
    }

    @Test
    void authoredCollisionUsesSmoothedCoastlineInsteadOfRawPolygonCorners() {
        Landmass landmass = new Landmass(
                "island",
                "smoothed_bank",
                0,
                0,
                500,
                500,
                500,
                500,
                500,
                500,
                null,
                1,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new Point2(-500, -500),
                        new Point2(500, -500),
                        new Point2(500, 500),
                        new Point2(-500, 500)
                ),
                List.of(new HeightPoint(0, 0, 145, 900, "hill")),
                -90
        );
        WorldMap worldMap = new WorldMap(1, List.of(landmass));

        assertTrue(LandGeometry.isBlocked(new Vector2(0, 0), worldMap));
        assertFalse(LandGeometry.isBlocked(new Vector2(485, 485), worldMap));
    }

    @Test
    void uploadedEditorLandscapeKeepsPlateauAsIslandBase() {
        LandscapeModelService service = new LandscapeModelService(new MemoryLandscapeRepository());
        LandscapeModelService.LandscapeModelSummary summary = service.saveUpload(new UploadedFile(
                "landscapeFile",
                "plateau-island.json",
                "application/json",
                """
                        {
                          "format": "game-landscape-designer.v2",
                          "name": "Plateau Island",
                          "islands": [
                            {
                              "id": "sandbank",
                              "name": "Sandbank",
                              "material": "sand",
                              "seaFloorHeight": -80,
                              "polygon": [
                                { "x": -400, "z": -360, "boundaryPointId": "a" },
                                { "x": 420, "z": -340, "boundaryPointId": "b" },
                                { "x": 430, "z": 360, "boundaryPointId": "c" },
                                { "x": -390, "z": 380, "boundaryPointId": "d" }
                              ],
                              "heights": [
                                { "x": -220, "z": -180, "h": 10, "radius": 260, "falloff": "plateau", "plateauGroupId": "beach-flat", "plateauOrder": 0, "plateauBoundaryPointId": "a" },
                                { "x": 240, "z": -170, "h": 10, "radius": 260, "falloff": "plateau", "plateauGroupId": "beach-flat", "plateauOrder": 1, "plateauBoundaryPointId": "b" },
                                { "x": 230, "z": 210, "h": 10, "radius": 260, "falloff": "plateau", "plateauGroupId": "beach-flat", "plateauOrder": 2, "plateauBoundaryPointId": "c" },
                                { "x": -230, "z": 190, "h": 10, "radius": 260, "falloff": "plateau", "plateauGroupId": "beach-flat", "plateauOrder": 3, "plateauBoundaryPointId": "d" }
                              ]
                            },
                            {
                              "id": "hill",
                              "name": "Hill",
                              "baseLevel": "plateau",
                              "baseLandmassId": "sandbank",
                              "basePlateauGroupId": "beach-flat",
                              "seaFloorHeight": -80,
                              "polygon": [
                                { "x": -120, "z": -110 },
                                { "x": 130, "z": -100 },
                                { "x": 140, "z": 120 },
                                { "x": -130, "z": 110 }
                              ],
                              "heights": [
                                { "x": 0, "z": 0, "h": 160, "radius": 180, "falloff": "hill" }
                              ]
                            }
                          ]
                        }
                        """.getBytes(StandardCharsets.UTF_8)
        ));

        WorldMap worldMap = service.find(summary.id()).orElseThrow().worldMap();
        Landmass sandbank = worldMap.landmasses().get(0);
        Landmass hill = worldMap.landmasses().get(1);

        assertEquals("sand", sandbank.material());
        assertEquals("beach-flat", sandbank.heightPoints().get(0).plateauGroupId());
        assertEquals(0, sandbank.heightPoints().get(0).plateauOrder());
        assertEquals(3, sandbank.heightPoints().get(3).plateauOrder());
        assertEquals("plateau", hill.baseLevel());
        assertEquals("sandbank", hill.baseLandmassId());
        assertEquals("beach-flat", hill.basePlateauGroupId());
        assertEquals(10, hill.baseHeight(), 0.001);
        assertTrue(LandGeometry.terrainHeightAt(new Vector2(0, 0), worldMap) >= 150);
        assertEquals(10, LandGeometry.terrainHeightAt(new Vector2(125, 105), worldMap), 1.5);
    }

    private static class MemoryLandscapeRepository implements LandscapeModelRepository {
        private final Map<String, LandscapeModelEntity> rows = new LinkedHashMap<>();
        private int saveCount;

        @Override
        public List<LandscapeModelEntity> findAllNewestFirst() {
            ArrayList<LandscapeModelEntity> values = new ArrayList<>(rows.values());
            Collections.reverse(values);
            return values;
        }

        @Override
        public Optional<LandscapeModelEntity> findById(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<LandscapeModelEntity> findAll() {
            return List.copyOf(rows.values());
        }

        @Override
        public LandscapeModelEntity save(LandscapeModelEntity entity) {
            saveCount++;
            rows.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public boolean delete(LandscapeModelEntity entity) {
            return entity != null && deleteById(entity.getId());
        }

        @Override
        public boolean deleteById(String id) {
            return rows.remove(id) != null;
        }

        @Override
        public long count() {
            return rows.size();
        }
    }
}
