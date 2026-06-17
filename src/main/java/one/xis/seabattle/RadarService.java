package one.xis.seabattle;

import one.xis.context.Service;

@Service
final class RadarService {

    static final double RADAR_RANGE = 945.0;

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
        return !LandGeometry.lineIntersectsRadarBlockingLand(observer.position(), contact.position(), worldMap);
    }

    boolean isVisible(ShipSnapshot observer, ShipSnapshot contact, WorldMap worldMap) {
        if (observer.id().equals(contact.id())) {
            return false;
        }
        if (!"active".equals(observer.state()) || !"active".equals(contact.state())) {
            return false;
        }
        Vector2 observerPosition = new Vector2(observer.x(), observer.z());
        Vector2 contactPosition = new Vector2(contact.x(), contact.z());
        if (observerPosition.distanceTo(contactPosition) > RADAR_RANGE) {
            return false;
        }
        return !LandGeometry.lineIntersectsRadarBlockingLand(observerPosition, contactPosition, worldMap);
    }

    double range() {
        return RADAR_RANGE;
    }

}
