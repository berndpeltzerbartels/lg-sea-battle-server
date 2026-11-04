package one.xis.seabattle.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** A sighting shares a fixed search area, never the submerged submarine's live position. */
public final class BotSubmarineHunt {
  static final double DURATION = 60;
  static final double DETECTION_NOTICE_SECONDS = 3;
  // Three quarters of the normal on-screen combat radar, not the strategic human-target range.
  static final double RECRUIT_RADIUS = RadarService.RADAR_RANGE * 2.5 * .62 * .75;
  static final double SEARCH_RADIUS = 6 * BotNavigation.SHIP_LENGTH;
  static final double WAYPOINT_REACHED = 4 * BotNavigation.SHIP_LENGTH;

  private record Contact(String team, String target, long life) {}

  public record Area(Vector2 center, double until) {}

  private final Map<Contact, Area> sightings = new HashMap<>();
  private double nextExpiry = Double.POSITIVE_INFINITY;

  void expire(double now) {
    if (now < nextExpiry) return;
    sightings.values().removeIf(area -> now >= area.until());
    nextExpiry =
        sightings.values().stream().mapToDouble(Area::until).min().orElse(Double.POSITIVE_INFINITY);
  }

  static boolean canReport(Vehicle observer) {
    return observer.isBotControlled()
        && !observer.isScoutPlane()
        && (!observer.isSubmarine()
            || observer.y() + SubmarinePeriscopeGeometry.topAboveRoot(observer.y()) > 0);
  }

  public record Detection(long lifeId, double until) {}

  Map<String, Detection> detections(double now) {
    expire(now);
    Map<String, Detection> result = new HashMap<>();
    sightings.forEach(
        (contact, area) -> {
          double until = area.until() - DURATION + DETECTION_NOTICE_SECONDS;
          if (until > now)
            result.merge(
                contact.target(),
                new Detection(contact.life(), until),
                (a, b) ->
                    a.lifeId() > b.lifeId() || a.lifeId() == b.lifeId() && a.until() >= b.until()
                        ? a
                        : b);
        });
    return Map.copyOf(result);
  }

  void sighting(Vehicle observer, Vehicle target, double now, Supplier<List<Vehicle>> neighbors) {
    if (!canReport(observer)) return;
    if (!target.isSubmarine() || observer.teamId().equals(target.teamId())) return;
    expire(now);
    Contact contact = new Contact(observer.teamId(), target.id(), target.lifeId());
    // Continued visibility does not move the area or restart its minute.
    if (sightings.containsKey(contact)) return;
    Area area = new Area(target.position(), now + DURATION);
    sightings.put(contact, area);
    nextExpiry = Math.min(nextExpiry, area.until());
    for (Vehicle vehicle : neighbors.get()) {
      if (!(vehicle instanceof TorpedoBoat boat)
          || !boat.isBotControlled()
          || !"active".equals(boat.state())
          || !observer.teamId().equals(boat.teamId())
          || boat.position().distanceTo(area.center()) > RECRUIT_RADIUS) continue;
      Search current = boat.submarineSearch();
      if (current == null || !current.active(now))
        boat.submarineSearch(new Search(area, boat.position()));
    }
  }

  public static final class Search {
    private final Area area;
    private final double entryAngle;
    private int waypoint;
    private Vector2 destination;

    Search(Area area, Vector2 approachFrom) {
      this.area = area;
      Vector2 offset = approachFrom.subtract(area.center());
      entryAngle = Math.atan2(offset.x(), offset.z());
      destination = point();
    }

    public boolean active(double now) {
      return now < area.until();
    }

    public Area area() {
      return area;
    }

    public int waypoint() {
      return waypoint;
    }

    public Vector2 currentDestination() {
      return destination;
    }

    Vector2 destination(Vehicle boat) {
      if (boat.position().distanceTo(destination) <= WAYPOINT_REACHED) {
        waypoint++;
        destination = point();
      }
      return destination;
    }

    private Vector2 point() {
      // Enter at the near side, then cross four points around the last sighting.
      return area.center()
          .add(Vector2.fromHeading(entryAngle + waypoint * Math.PI / 2).scale(SEARCH_RADIUS));
    }
  }
}
