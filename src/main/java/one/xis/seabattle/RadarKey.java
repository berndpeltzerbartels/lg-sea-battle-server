package one.xis.seabattle;

record RadarKey(String playerId, String teamId) {

    static RadarKey from(RadarRequest request) {
        return new RadarKey(request.playerId(), request.teamId());
    }
}
