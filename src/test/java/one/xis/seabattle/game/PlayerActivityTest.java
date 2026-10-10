package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerActivityTest {
    @Test void openConnectionAndReconnectDoNotPreventExpiry() {
        var activity = new PlayerActivity();
        activity.connected("gunner", 0);
        activity.connected("gunner", 299_999);
        assertTrue(activity.expired(299_999).isEmpty());
        assertEquals(java.util.List.of("gunner"), activity.expired(300_000));
        assertFalse(activity.input("gunner", 300_000));
    }
    @Test void InputRenewsOnlyItsOwnSessionAndCannotReviveRemovedPlayers() {
        var activity = new PlayerActivity();
        activity.connected("captain", 0);
        activity.connected("gunner", 0);
        assertTrue(activity.input("captain", 290_000));
        assertEquals(java.util.List.of("gunner"), activity.expired(300_000));
        activity.remove("gunner");
        assertFalse(activity.input("gunner", 310_000));
        assertTrue(activity.expired(310_000).isEmpty());
    }
}
