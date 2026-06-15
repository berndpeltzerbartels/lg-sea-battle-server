package one.xis.seabattle;

import one.xis.context.Service;

@Service
final class NavigationService {

    boolean isShipBlocked(Vector2 position, double heading, WorldMap worldMap) {
        return isShipBlocked(position, heading, worldMap, ShipPointRange.ALL);
    }

    boolean isShipMovementBlocked(Vector2 position, double heading, double speed, WorldMap worldMap) {
        if (speed < 0) {
            return isShipBlocked(position, heading, worldMap, ShipPointRange.CENTER_AND_STERN);
        }
        return isShipBlocked(position, heading, worldMap, ShipPointRange.CENTER_AND_BOW);
    }

    boolean isTorpedoBlocked(Vector2 position, WorldMap worldMap) {
        return LandGeometry.isBlocked(position, worldMap);
    }

    private boolean isShipBlocked(Vector2 position, double heading, WorldMap worldMap, ShipPointRange pointRange) {
        return shipPoint(position, heading, 4.9, 0, worldMap, pointRange)
                || shipPoint(position, heading, 3.2, -0.62, worldMap, pointRange)
                || shipPoint(position, heading, 3.2, 0.62, worldMap, pointRange)
                || shipPoint(position, heading, 1.0, -0.74, worldMap, pointRange)
                || shipPoint(position, heading, 1.0, 0.74, worldMap, pointRange)
                || shipPoint(position, heading, -1.0, -0.62, worldMap, pointRange)
                || shipPoint(position, heading, -1.0, 0.62, worldMap, pointRange)
                || shipPoint(position, heading, 0, 0, worldMap, pointRange);
    }

    private boolean shipPoint(Vector2 position, double heading, double forwardOffset, double sideOffset, WorldMap worldMap,
                              ShipPointRange pointRange) {
        if (!pointRange.includes(forwardOffset)) {
            return false;
        }
        Vector2 forward = Vector2.fromHeading(heading);
        Vector2 right = new Vector2(Math.cos(heading), -Math.sin(heading));
        Vector2 point = position.add(forward.scale(forwardOffset)).add(right.scale(sideOffset));
        return LandGeometry.isBlocked(point, worldMap);
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
