# Landscape checkpoint, 2026-09-23

Checkpoint before shared JavaScript landscape rendering extraction.
Includes the existing terrain/base/plateau import, respawn preparation,
versioned persistent world-map cache and administration changes.

`./gradlew test`: 228 tests, 5 failed, 2 skipped before extraction:
- playerStateUsesClientPositionWithoutServerAdvancingHumanShip
- flakProjectileCanSinkFriendlyShip
- cannonCloseSideShotHitGridCoversVisibleHullProfile
- botScoutPlaneAvoidsBackToBackHumanAttacksWhenBotTargetsExist
- playerCanJoinAsSubmarineWithBoatMovementRules

Do not treat this checkpoint as a green gameplay baseline. Landscape import
tests pass. E2E gameplay performance has not been certified.

The preparation cache stores derived world-map and respawn data, not Babylon
meshes. Its version must change when conversion/preparation semantics change.
Manual editor boundaryPointId / plateauBoundaryPointId links currently remain
in originalJson only; conversion does not yet expose them in WorldMap.
The cache is not a substitute for this missing model integration.
