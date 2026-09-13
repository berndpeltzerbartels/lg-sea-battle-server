package one.xis.seabattle.game;

import one.xis.context.Service;

@Service
final class NavigationService {

    private static final double SURFACE_COLLISION_HEIGHT = 0;
    private static final double PERISCOPE_COLLISION_HEIGHT = -1.6;
    private static final double SUBMERGED_COLLISION_HEIGHT = -2.95;

    boolean isShipBlocked(Vector2 position, double heading, WorldMap worldMap) {
        return isShipBlocked(position, heading, worldMap, "surface");
    }

    boolean isShipBlocked(Vector2 position, double heading, WorldMap worldMap, String depthState) {
        return isShipBlocked(position, heading, LandGeometry.collisionModel(worldMap), depthState);
    }

    boolean isShipBlocked(Vector2 position, double heading, LandGeometry.CollisionModel collisionModel, String depthState) {
        return isShipBlocked(position, heading, collisionModel, ShipPointRange.ALL, collisionHeight(depthState));
    }

    boolean isShipMovementBlocked(Vector2 position, double heading, double speed, WorldMap worldMap) {
        return isShipMovementBlocked(position, heading, speed, worldMap, "surface");
    }

    boolean isShipMovementBlocked(Vector2 position, double heading, double speed, WorldMap worldMap, String depthState) {
        return isShipMovementBlocked(position, heading, speed, LandGeometry.collisionModel(worldMap), depthState);
    }

    boolean isShipMovementBlocked(Vector2 position, double heading, double speed,
                                  LandGeometry.CollisionModel collisionModel, String depthState) {
        double collisionHeight = collisionHeight(depthState);
        if (speed < 0) {
            return isShipBlocked(position, heading, collisionModel, ShipPointRange.CENTER_AND_STERN, collisionHeight);
        }
        return isShipBlocked(position, heading, collisionModel, ShipPointRange.CENTER_AND_BOW, collisionHeight);
    }

    boolean isTorpedoBlocked(Vector2 position, WorldMap worldMap) {
        return LandGeometry.isBlocked(position, worldMap);
    }

    private boolean isShipBlocked(Vector2 position, double heading, LandGeometry.CollisionModel collisionModel, ShipPointRange pointRange,
                                  double collisionHeight) {
        return shipPoint(position, heading, 4.9, 0, collisionModel, pointRange, collisionHeight)
                || shipPoint(position, heading, 3.2, -0.62, collisionModel, pointRange, collisionHeight)
                || shipPoint(position, heading, 3.2, 0.62, collisionModel, pointRange, collisionHeight)
                || shipPoint(position, heading, 1.0, -0.74, collisionModel, pointRange, collisionHeight)
                || shipPoint(position, heading, 1.0, 0.74, collisionModel, pointRange, collisionHeight)
                || shipPoint(position, heading, -1.0, -0.62, collisionModel, pointRange, collisionHeight)
                || shipPoint(position, heading, -1.0, 0.62, collisionModel, pointRange, collisionHeight)
                || shipPoint(position, heading, 0, 0, collisionModel, pointRange, collisionHeight);
    }

    private boolean shipPoint(Vector2 position, double heading, double forwardOffset, double sideOffset,
                              LandGeometry.CollisionModel collisionModel,
                              ShipPointRange pointRange, double collisionHeight) {
        if (!pointRange.includes(forwardOffset)) {
            return false;
        }
        Vector2 forward = Vector2.fromHeading(heading);
        Vector2 right = new Vector2(Math.cos(heading), -Math.sin(heading));
        Vector2 point = position.add(forward.scale(forwardOffset)).add(right.scale(sideOffset));
        return LandGeometry.isBlockedAtOrAbove(point, collisionModel, collisionHeight);
    }

    private static double collisionHeight(String depthState) {
        if ("periscope".equals(depthState)) {
            return PERISCOPE_COLLISION_HEIGHT;
        }
        if ("submerged".equals(depthState)) {
            return SUBMERGED_COLLISION_HEIGHT;
        }
        return SURFACE_COLLISION_HEIGHT;
    }

    private enum ShipPointRange {
        ALL {
            @Override
            boolean includes(double forwardOffset) {
                return true;
            }
        },
        CENTER_AND_BOW {
            @Override
            boolean includes(double forwardOffset) {
                return forwardOffset >= -0.05;
            }
        },
        CENTER_AND_STERN {
            @Override
            boolean includes(double forwardOffset) {
                return forwardOffset <= 0.05;
            }
        };

        abstract boolean includes(double forwardOffset);
    }

}
