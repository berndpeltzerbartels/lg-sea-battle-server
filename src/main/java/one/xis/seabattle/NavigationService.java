package one.xis.seabattle;

import one.xis.context.Service;

@Service
final class NavigationService {

    private static final double TORPEDO_MINIMUM_DEPTH_METERS = 1.0;
    private static final double MAX_DEPTH_METERS = 110.0;

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
        return waterDepthMeters(position, worldMap) < TORPEDO_MINIMUM_DEPTH_METERS;
    }

    double waterDepthMeters(Vector2 position, WorldMap worldMap) {
        double nearestCoastDistance = worldMap.landmasses().stream()
                .mapToDouble(landmass -> {
                    double distance = shapeDistance(position, landmass, landmass.rx(), landmass.rz());
                    double coastDistance = isInLandWater(position, landmass)
                            ? Math.max(0.08, Math.abs(distance - 0.72))
                            : distance - blockDistance(landmass);
                    return coastDistance;
                })
                .min()
                .orElse(Double.POSITIVE_INFINITY);

        if (nearestCoastDistance <= 0) {
            return 0;
        }

        double ratio = MathSupport.clamp(1 - Math.exp(-nearestCoastDistance / 1.25), 0, 1);
        return 2 + ratio * (MAX_DEPTH_METERS - 2);
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
        return isBlocked(point, worldMap);
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

    private boolean isBlocked(Vector2 position, WorldMap worldMap) {
        return worldMap.landmasses().stream()
                .anyMatch(landmass -> isBlockedByLandmass(position, landmass));
    }

    private boolean isBlockedByLandmass(Vector2 position, Landmass landmass) {
        double distance = shapeDistance(position, landmass, landmass.navigationRx(), landmass.navigationRz());
        return distance < blockDistance(landmass) && !isInLandWater(position, landmass);
    }

    private double shapeDistance(Vector2 position, Landmass landmass, double rx, double rz) {
        double localX = position.x() - landmass.x();
        double localZ = position.z() - landmass.z();
        double nx = localX / rx;
        double nz = localZ / rz;
        double distance = Math.sqrt(nx * nx + nz * nz);
        if (!"coastline".equals(landmass.kind())) {
            return distance;
        }
        double angle = Math.atan2(nz, nx);
        return distance / coastRadiusFactor(angle, landmass);
    }

    private double blockDistance(Landmass landmass) {
        return "coastline".equals(landmass.kind()) ? 1.055 : 1;
    }

    private boolean isInLandWater(Vector2 position, Landmass landmass) {
        double localX = position.x() - landmass.x();
        double localZ = position.z() - landmass.z();
        return isInFjordWater(localX, localZ, landmass)
                || isInWaterway(localX, localZ, landmass)
                || isInLake(localX, localZ, landmass);
    }

    private boolean isInFjordWater(double localX, double localZ, Landmass landmass) {
        return fjordCarve(localX, localZ, landmass.rx(), landmass.rz(), landmass) > 0.58;
    }

    private double fjordCarve(double localX, double localZ, double rx, double rz, Landmass landmass) {
        double carve = 0;
        for (Fjord fjord : landmass.fjords()) {
            double dirX = Math.sin(fjord.angle());
            double dirZ = Math.cos(fjord.angle());
            double along = (localX * dirX) / rx + (localZ * dirZ) / rz;
            double across = Math.abs((localX * dirZ) / rx - (localZ * dirX) / rz);
            double reach = fjord.reach();
            double width = fjord.width();
            double mouthToCenter = MathSupport.smoothstep(1.03, 0.12, along);
            double fromCoast = MathSupport.smoothstep(-1.02, -0.1, along);
            double channel = 1 - MathSupport.smoothstep(width * 0.45, width, across);
            double depth = MathSupport.smoothstep(reach, 0.08, Math.abs(along));
            carve = Math.max(carve, channel * mouthToCenter * fromCoast * depth);
        }
        return carve;
    }

    private boolean isInWaterway(double localX, double localZ, Landmass landmass) {
        return landmass.waterways().stream().anyMatch(waterway ->
                distanceToSegment(localX, localZ, waterway.from().x(), waterway.from().z(),
                        waterway.to().x(), waterway.to().z()) <= waterway.width() * 0.58
        );
    }

    private boolean isInLake(double localX, double localZ, Landmass landmass) {
        return landmass.lakes().stream().anyMatch(lake -> {
            double nx = (localX - lake.x()) / lake.rx();
            double nz = (localZ - lake.z()) / lake.rz();
            return nx * nx + nz * nz <= 1;
        });
    }

    private double distanceToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSquared = dx * dx + dz * dz;
        double t = lengthSquared == 0
                ? 0
                : MathSupport.clamp(((px - ax) * dx + (pz - az) * dz) / lengthSquared, 0, 1);
        double nearestX = ax + dx * t;
        double nearestZ = az + dz * t;
        double ox = px - nearestX;
        double oz = pz - nearestZ;
        return Math.sqrt(ox * ox + oz * oz);
    }

    private double coastRadiusFactor(double angle, Landmass landmass) {
        double roughness = landmass.coastRoughness() == null ? 0.16 : landmass.coastRoughness();
        double seed = stableNameSeed(landmass.name()) * 0.013;
        double broad = Math.sin(angle * 2 + seed) * 0.62;
        double bays = Math.sin(angle * 4 - seed * 0.7) * 0.42;
        double small = Math.sin(angle * 7 + seed * 1.4) * 0.2;
        double fjordBite = 0;
        for (Fjord fjord : landmass.fjords()) {
            double width = Math.max(0.08, fjord.width());
            double angleDistance = Math.abs(MathSupport.normalizeAngle(angle - fjord.angle()));
            double mouth = 1 - MathSupport.smoothstep(width * 0.45, width * 1.9, angleDistance);
            fjordBite = Math.max(fjordBite, mouth * (0.18 + width * 0.9));
        }
        return MathSupport.clamp(1 + (broad + bays + small) * roughness - fjordBite, 0.56, 1.42);
    }

    private int stableNameSeed(String name) {
        int seed = 0;
        for (int i = 0; i < name.length(); i += 1) {
            seed = (seed * 31 + name.charAt(i)) % 9973;
        }
        return seed;
    }
}
