package one.xis.seabattle.game;

import com.google.gson.Gson;
import one.xis.seabattle.webapp.gameconfig.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SubmarineLiveConfigurationTest {
    private SubmarineBotConfig submarineConfig(String field,double value) {
        var gson=new Gson();var json=gson.toJsonTree(SubmarineBotConfig.defaults()).getAsJsonObject();
        json.addProperty(field,value);return gson.fromJson(json,SubmarineBotConfig.class);
    }
    private GameSession session(java.util.function.Supplier<GameRuntimeConfig> config) {
        var setup=new GameSetup("sub-live",new WorldMap(1,List.of()),List.of(
                new FleetSetup("light",List.of(new VehicleSetup("sub","light",new Vector2(0,0),0,"bot",2,0,9999,"submarine",0))),
                new FleetSetup("dark",List.of(new VehicleSetup("target","dark",new Vector2(0,550),0,"human",2,0,9999,"torpedo-boat",0)))),List.of());
        return new GameSession(setup,config);
    }
    private Vehicle sub(GameSession s) { return s.calculationShips().stream().filter(v->v.id().equals("sub")).findFirst().orElseThrow(); }

    @Test void liveDiveRangeReachesSubmarineDecisionsWithoutChangingTorpedoBoatValues() {
        var boat=BotCombatConfig.defaults();
        var current=new AtomicReference<>(new GameRuntimeConfig(boat,SubmarineBotConfig.defaults(),null));
        var session=session(current::get);var radar=new RadarService();var nav=new NavigationService();
        session.update(.1,radar,nav,session.worldMap());
        assertEquals("surface",sub(session).depthState());
        current.set(new GameRuntimeConfig(boat,submarineConfig("diveRange",600),null));
        session.update(.1,radar,nav,session.worldMap());
        assertEquals("periscope",sub(session).depthState());
        assertSame(boat,session.runtimeConfig().torpedoBoatBot());
    }

    @Test void bothBotTypesHaveIndependentCombatValuesWhileHumanWeaponsKeepTheirDefaults() {
        var submarine=SubmarineBotConfig.defaults();
        var gson=new Gson();var json=gson.toJsonTree(submarine).getAsJsonObject();
        json.getAsJsonObject("combat").addProperty("aimResponse",1.2);
        json.getAsJsonObject("combat").addProperty("aimErrorAngle",Math.toRadians(2));
        submarine=gson.fromJson(json,SubmarineBotConfig.class);
        var config=new GameRuntimeConfig(BotCombatConfig.defaults().withAimErrorAngle(Math.toRadians(.5)),submarine,null);
        var session=session(()->config);session.update(0,new RadarService(),new NavigationService(),session.worldMap());
        assertSame(submarine.combat(),session.botCombatConfig(sub(session)));
        var boat=new TorpedoBoat("boat","light",new Vector2(0,0),0,"bot");
        assertSame(config.torpedoBoatBot(),session.botCombatConfig(boat));
        var human=new Submarine("human-sub","light",new Vector2(0,0),0,"player-H");
        assertSame(GameSession.DEFAULT_RUNTIME_CONFIG.torpedoBoatBot(),session.botCombatConfig(human));
        assertEquals(0,session.botAimErrorAngle(human,sub(session)));
    }
}
