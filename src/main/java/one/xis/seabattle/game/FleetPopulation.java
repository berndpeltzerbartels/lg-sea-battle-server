package one.xis.seabattle.game;

import one.xis.seabattle.webapp.gameconfig.FleetSizeConfig;
import java.util.*;
import java.util.function.BiPredicate;

/** Capacity and slow reinforcements. Ordinary replacements keep their existing spawn mechanism. */
final class FleetPopulation {
    static final double INCREASE_INTERVAL_SECONDS = 3;
    private FleetSizeConfig sizes;
    private final Set<String> withheld = new HashSet<>();
    private double nextIncrease;
    private int nextTeam;

    void configure(FleetSizeConfig configured, double now) {
        if (!Objects.equals(sizes, configured)) {
            sizes = configured;
            nextIncrease = now + INCREASE_INTERVAL_SECONDS;
        }
    }

    boolean manages(Fleet fleet) {
        return sizes != null && (fleet.teamId().equals("light") || fleet.teamId().equals("dark"));
    }

    private int target(Fleet fleet) { return fleet.teamId().equals("light") ? sizes.light() : sizes.dark(); }

    boolean mayReplace(Vehicle vehicle, Fleet fleet) {
        if (!manages(fleet)) return true;
        if (withheld.contains(vehicle.id())) return false;
        if (fleet.activeShips().size() >= target(fleet)) withheld.add(vehicle.id());
        return !withheld.contains(vehicle.id());
    }

    void increase(Map<String, Fleet> fleets, double now, BiPredicate<Fleet, Vehicle> place) {
        if (sizes == null || now < nextIncrease) return;
        nextIncrease = now + INCREASE_INTERVAL_SECONDS; // No catch-up burst after a pause or blocked point.
        for (int offset = 0; offset < 2; offset++) {
            int team = (nextTeam + offset) % 2;
            Fleet fleet = fleets.get(team == 0 ? "light" : "dark");
            if (fleet == null) continue;
            // A regular loss already waiting for replacement reserves its slot.
            long capacity = fleet.ships().stream().filter(v -> "active".equals(v.state())
                    || "sunk".equals(v.state()) && !withheld.contains(v.id())).count();
            if (capacity >= target(fleet)) continue;
            Vehicle slot = fleet.ships().stream().filter(v -> withheld.contains(v.id())
                    && "sunk".equals(v.state())).findFirst().orElse(null);
            Vehicle candidate = slot == null ? fleet.reinforcementCandidate()
                    : fleet.changeVehicleType(slot, fleet.intendedVehicleType(slot));
            if (!place.test(fleet, candidate)) continue;
            if (slot == null) fleet.addReinforcement(candidate);
            else withheld.remove(slot.id());
            nextTeam = 1 - team;
            return; // One vehicle in total, across all colours and types.
        }
    }
}
