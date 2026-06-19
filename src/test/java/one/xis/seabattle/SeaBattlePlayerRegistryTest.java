package one.xis.seabattle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeaBattlePlayerRegistryTest {

    @Test
    void unregisterPlayerReleasesAliasReservation() {
        SeaBattlePlayerRegistry registry = new SeaBattlePlayerRegistry();

        registry.register("BP", "Bernd");

        assertTrue(registry.isAliasRegistered("bp"));
        assertEquals("Bernd", registry.playerName("BP"));

        registry.unregisterPlayer("player-BP-mandatory-random-part");

        assertFalse(registry.isAliasRegistered("BP"));
        assertEquals("-", registry.playerName("BP"));
    }

    @Test
    void replacingAliasOwnerKeepsReservationForNewPlayer() {
        SeaBattlePlayerRegistry registry = new SeaBattlePlayerRegistry();

        registry.register("BP", "Bernd");
        assertEquals(null, registry.registerPlayer("player-BP-first", "Bernd"));
        assertEquals("player-BP-first", registry.registerPlayer("player-BP-second", "Bernd"));

        registry.unregisterPlayer("player-BP-first");

        assertTrue(registry.isAliasRegistered("BP"));
        assertEquals("Bernd", registry.playerName("BP"));

        registry.unregisterPlayer("player-BP-second");

        assertFalse(registry.isAliasRegistered("BP"));
    }
}
