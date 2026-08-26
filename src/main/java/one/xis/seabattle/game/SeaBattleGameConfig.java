package one.xis.seabattle.game;

record SeaBattleGameConfig(VehicleScale vehicleScale) {

    static final double TORPEDO_BOAT_SCALE = 3.0;
    static final double SCOUT_PLANE_SCALE = 1.5;

    static SeaBattleGameConfig current() {
        return new SeaBattleGameConfig(new VehicleScale(TORPEDO_BOAT_SCALE, SCOUT_PLANE_SCALE));
    }

    record VehicleScale(double torpedoBoat, double scoutPlane) {
    }
}
