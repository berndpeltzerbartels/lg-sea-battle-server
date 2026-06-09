package one.xis.seabattle;

import one.xis.context.Service;

import java.util.List;

@Service
final class RadarService {

    static final double RADAR_RANGE = 945.0;
    private static final double RADAR_OCCLUSION_SCALE = 0.72;

    boolean isVisible(Ship observer, Ship contact, WorldMap worldMap) {
        if (observer.id().equals(contact.id())) {
            return false;
        }
        if (!"active".equals(observer.state()) || !"active".equals(contact.state())) {
            return false;
        }
        if (observer.position().distanceTo(contact.position()) > RADAR_RANGE) {
            return false;
        }
        return !isLineBlockedByLand(observer.position(), contact.position(), worldMap.landmasses());
    }

    double range() {
        return RADAR_RANGE;
    }

    private boolean isLineBlockedByLand(Vector2 from, Vector2 to, List<Landmass> landmasses) {
        return landmasses.stream().anyMatch(landmass -> lineIntersectsEllipse(from, to, landmass));
    }

    private boolean lineIntersectsEllipse(Vector2 from, Vector2 to, Landmass landmass) {
        double rx = visualRx(landmass) * RADAR_OCCLUSION_SCALE;
        double rz = visualRz(landmass) * RADAR_OCCLUSION_SCALE;
        if (rx <= 0 || rz <= 0) {
            return false;
        }

        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        double ox = from.x() - landmass.x();
        double oz = from.z() - landmass.z();
        double startInsideOccluder = (ox * ox) / (rx * rx) + (oz * oz) / (rz * rz);
        double endOx = to.x() - landmass.x();
        double endOz = to.z() - landmass.z();
        double endInsideOccluder = (endOx * endOx) / (rx * rx) + (endOz * endOz) / (rz * rz);
        if (startInsideOccluder <= 1 || endInsideOccluder <= 1) {
            return false;
        }

        double a = (dx * dx) / (rx * rx) + (dz * dz) / (rz * rz);
        double b = 2 * ((ox * dx) / (rx * rx) + (oz * dz) / (rz * rz));
        double c = (ox * ox) / (rx * rx) + (oz * oz) / (rz * rz) - 1;
        double discriminant = b * b - 4 * a * c;
        if (discriminant <= 0) {
            return false;
        }

        double root = Math.sqrt(discriminant);
        double t1 = (-b - root) / (2 * a);
        double t2 = (-b + root) / (2 * a);
        return (t1 > 0.02 && t1 < 0.98) || (t2 > 0.02 && t2 < 0.98);
    }

    private double visualRx(Landmass landmass) {
        return landmass.rx();
    }

    private double visualRz(Landmass landmass) {
        return landmass.rz();
    }
}
