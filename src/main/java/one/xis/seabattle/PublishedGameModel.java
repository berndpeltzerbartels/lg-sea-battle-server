package one.xis.seabattle;

import java.util.Map;

record PublishedGameModel(GameSnapshot state, Map<RadarKey, RadarSnapshot> radars) {

    RadarSnapshot radar(RadarRequest request) {
        return radars.get(RadarKey.from(request));
    }
}
