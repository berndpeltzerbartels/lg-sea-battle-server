package one.xis.seabattle;

final class VesselTypes {

    static final String TORPEDO_BOAT = "torpedo_boat";
    static final String SUBMARINE = "submarine";
    static final String SURFACE = "surface";
    static final String PERISCOPE = "periscope";
    static final String SUBMERGED = "submerged";

    private VesselTypes() {
    }

    static String normalizeVesselType(String value) {
        return SUBMARINE.equals(value) ? SUBMARINE : TORPEDO_BOAT;
    }

    static String normalizeDepthState(String value, String vesselType) {
        if (!SUBMARINE.equals(normalizeVesselType(vesselType))) {
            return SURFACE;
        }
        if (PERISCOPE.equals(value) || SUBMERGED.equals(value)) {
            return value;
        }
        return SURFACE;
    }
}
