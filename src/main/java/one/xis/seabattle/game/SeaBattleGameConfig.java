package one.xis.seabattle.game;

record SeaBattleGameConfig(VehicleScale vehicleScale, double[] engineSpeeds) {

    static final double TORPEDO_BOAT_SCALE = 3.0;
    static final double SCOUT_PLANE_SCALE = 1.5;
    static final double SCOUT_PLANE_ALTITUDE_SCALE = 1.75;

    static SeaBattleGameConfig current() {
        return new SeaBattleGameConfig(new VehicleScale(TORPEDO_BOAT_SCALE, SCOUT_PLANE_SCALE), EngineOrders.speeds());
    }

    record VehicleScale(double torpedoBoat, double scoutPlane) {
    }
}
