package one.xis.seabattle.game;

import one.xis.UploadedFile;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
    void uploadedEditorLandscapeCanRestartGameSession() {
        LandscapeModelService service = new LandscapeModelService(new MemoryLandscapeRepository());
        LandscapeModelService.LandscapeModelSummary summary = service.saveUpload(new UploadedFile(
                "landscapeFile",
                "fjord.json",
                "application/json",
                """
                        {
                          "format": "game-landscape-designer.v1",
                          "name": "Test Fjord",
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
        assertTrue(LandGeometry.maxTerrainHeight(worldMap) >= 4500);
        assertTrue(LandGeometry.terrainHeightAt(new Vector2(20, 10), worldMap) >= 4500);
        assertNotNull(gameStateService.snapshot());
        assertTrue(service.delete(summary.id()));
        assertTrue(service.find(summary.id()).isEmpty());
    }

    @Test
    void storedLandscapeIsReconvertedFromOriginalJsonSoLandmarkYStaysAuthoritative() {
        MemoryLandscapeRepository repository = new MemoryLandscapeRepository();
        LandscapeModelService service = new LandscapeModelService(repository);
        repository.save(new LandscapeModelEntity(
                "stale-world-map",
                "Reconvert Me",
                "game-landscape-designer.v1",
                """
                        {
                          "format": "game-landscape-designer.v1",
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
                LocalDateTime.now()
        ));

        WorldMap worldMap = service.find("stale-world-map").orElseThrow().worldMap();

        assertEquals(1, worldMap.mapObjects().size());
        assertEquals(360, worldMap.mapObjects().get(0).y());
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
                          "format": "game-landscape-designer.v1",
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

    private static class MemoryLandscapeRepository implements LandscapeModelRepository {
        private final Map<String, LandscapeModelEntity> rows = new LinkedHashMap<>();

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
