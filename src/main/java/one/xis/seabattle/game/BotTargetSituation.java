package one.xis.seabattle.game;

import java.util.List;
import java.util.function.Supplier;

/** Selection context for one bot. Use only during select(). Snapshots are produced lazily.
 * The list contains eligible enemies, not the whole fleet. It uses the game's perception rules,
 * which include strategic surface-human contacts beyond ordinary visual range.
 */
public final class BotTargetSituation {
    private final Vehicle observer;
    private final List<Vehicle> candidates;
    private final String currentTargetId;
    private final double timeSeconds;
    private final Supplier<Vehicle> standard;
    private VehicleSnapshot observerSnapshot;
    private List<VehicleSnapshot> snapshots;
    private boolean standardComputed;
    private String standardId;

    BotTargetSituation(Vehicle observer, List<Vehicle> candidates, String currentTargetId, double timeSeconds,
                       Supplier<Vehicle> standard) {
        this.observer = observer;
        this.candidates = List.copyOf(candidates);
        this.currentTargetId = currentTargetId;
        this.timeSeconds = timeSeconds;
        this.standard = standard;
    }

    public String observerId() { return observer.id(); }
    public VehicleSnapshot observer() {
        if (observerSnapshot == null) observerSnapshot = observer.snapshot();
        return observerSnapshot;
    }
    public List<VehicleSnapshot> candidates() {
        if (snapshots == null) snapshots = candidates.stream().map(Vehicle::snapshot).toList();
        return snapshots;
    }
    public int candidateCount() { return candidates.size(); }
    /** Previously retained navigation target for boats/submarines; null for aircraft. */
    public String currentTargetId() { return currentTargetId; }
    public double timeSeconds() { return timeSeconds; }

    /** Existing ranking including human preference (boats) or reservations/attack history (aircraft). */
    public String standardTargetId() {
        if (!standardComputed) {
            Vehicle target = standard.get();
            standardId = target == null ? null : target.id();
            standardComputed = true;
        }
        return standardId;
    }

    Vehicle resolve(String targetId) {
        if (targetId == null) return null;
        return candidates.stream().filter(ship -> ship.id().equals(targetId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Target is not an eligible candidate for "
                        + observerId() + ": " + targetId));
    }
}
