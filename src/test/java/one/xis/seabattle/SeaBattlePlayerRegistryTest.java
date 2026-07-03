package one.xis.seabattle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeaBattlePlayerRegistryTest {

    @Test
    void unregisterPlayerReleasesAliasReservation() {
        SeaBattlePlayerRegistry registry = new SeaBattlePlayerRegistry();

        registry.register("player-BP-mandatory-random-part", "BP", "Bernd", "light", "account-1");

        assertTrue(registry.isAliasRegistered("bp"));
        assertEquals("Bernd", registry.playerName("BP"));
        assertEquals("light", registry.playerTeam("BP"));
        assertTrue(registry.isRegisteredPlayer("player-BP-mandatory-random-part"));
        assertEquals(1, registry.players().size());
        assertEquals("BP", registry.players().get(0).initials());
        assertEquals("light", registry.players().get(0).teamId());

        registry.unregisterPlayer("player-BP-mandatory-random-part");

        assertFalse(registry.isAliasRegistered("BP"));
        assertEquals("-", registry.playerName("BP"));
    }

    @Test
    void unknownPlayerDoesNotClaimAliasReservation() {
        SeaBattlePlayerRegistry registry = new SeaBattlePlayerRegistry();

        registry.register("player-BP-first", "BP", "Bernd", "light", "account-1");
        assertEquals(null, registry.registerPlayer("player-BP-first", "Bernd", "light"));
        assertEquals(null, registry.registerPlayer("player-BP-second", "Bernd", "dark"));

        assertTrue(registry.isRegisteredPlayer("player-BP-first"));
        assertFalse(registry.isRegisteredPlayer("player-BP-second"));
        assertEquals("Bernd", registry.playerName("BP"));
        assertEquals("light", registry.playerTeam("BP"));

        registry.unregisterPlayer("player-BP-first");

        assertFalse(registry.isAliasRegistered("BP"));
    }

    @Test
    void accountMayChangeAliasButOtherAccountsCannotClaimActiveAlias() {
        SeaBattlePlayerRegistry registry = new SeaBattlePlayerRegistry();

        registry.register("player-BP-first", "BP", "Bernd", "light", "account-1");

        assertFalse(registry.isAliasRegisteredForOtherAccount("BP", "account-1"));
        assertTrue(registry.isAliasRegisteredForOtherAccount("BP", "account-2"));

        registry.register("player-BPB-second", "BPB", "Bernd", "dark", "account-1");

        assertFalse(registry.isAliasRegistered("BP"));
        assertTrue(registry.isAliasRegistered("BPB"));
        assertEquals("player-BPB-second", registry.activePlayerIdForAccountAlias("account-1", "BPB"));
    }
}
