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

## Test repairs after checkpoint

The five failures above were traced to obsolete expectations or invalid test
setups, not repaired by changing production gameplay:
- Commit 6ebde58 intentionally continues human ship motion between client
  updates and preserves reported submarine depth. Tests now also check that
  later client positions correct that motion and depth survives a tick.
- Friendly flak fire now follows the existing critical-hit trajectory and
  expects `ship-critical-hit`; the old trajectory only hit the nonfatal hull.
- The cannon grid now covers the above-water hull inside the visible bow
  (model z=3.68). Old low samples started below water and never advanced.
  Its trajectory helper now uses the cannon's ballistic equation and asserts
  the actual endpoint rather than silently sampling a different segment.
- Scout-plane target selection is checked directly after a human attack.
  Rudder direction is not a target identity: close lateral targets can require
  the separately tested straight fly-through maneuver.
