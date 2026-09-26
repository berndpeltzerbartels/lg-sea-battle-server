package one.xis.seabattle.game;

import one.xis.seabattle.webapp.account.Account;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrewRecruitmentServiceTest {
    private final GameStateService game = new GameStateService(new DefaultGameSetupFactory(new WorldMapService()),
            new RadarService(), new NavigationService());
    private final SeaBattlePlayerRegistry players = new SeaBattlePlayerRegistry();
    private final java.util.List<one.xis.RefreshEvent> events = new java.util.ArrayList<>();
    private final CrewRecruitmentService service = new CrewRecruitmentService(game, players, events::add);
    private final Account applicant = new Account("applicant", "Applicant", "APP", "light", null);

    private String captain(String alias, String team, String type) {
        String id = "player-" + alias + "-test";
        players.register(id, alias, alias, team, alias);
        game.activateTeam(team);
        game.assignPlayerVehicle(id, team, type);
        return game.snapshot().ships().stream().filter(s -> id.equals(s.controlledBy())).findFirst().orElseThrow().id();
    }

    @Test
    void onlyFriendlyHumanTorpedoBoatsAreOfferedAndApplyingDoesNotAssignShip() {
        captain("ENEMY", "dark", "torpedo-boat");
        String ship = captain("CAP", "light", "torpedo-boat");
        captain("SUB", "light", "submarine");
        assertEquals(java.util.List.of(ship), service.ships(applicant).stream().map(CrewRecruitmentService.ShipOption::id).toList());
        var before = game.snapshot().ships();
        service.request(applicant, ship);
        assertEquals(before, game.snapshot().ships());
        assertEquals("Offen", service.requests(applicant).get(0).status());
        assertFalse(service.ships(applicant).get(0).available());
    }

    @Test
    void xisRefreshIsPublishedForRosterAndRequestChangesButNotUnchangedViews() {
        String ship = captain("CAP", "light", "torpedo-boat");
        service.publishChanges();
        assertEquals(1, events.size());
        service.publishChanges();
        assertEquals(1, events.size());
        service.request(applicant, ship);
        service.publishChanges();
        assertEquals(2, events.size());
        captain("TWO", "light", "torpedo-boat");
        service.publishChanges();
        assertEquals(3, events.size());
        game.releasePlayer("player-TWO-test");
        service.publishChanges();
        assertEquals(4, events.size());
    }

    @Test
    void duplicateAndParallelRequestsAreRejectedEvenAfterPageReload() {
        String first = captain("CAP", "light", "torpedo-boat");
        String second = captain("TWO", "light", "torpedo-boat");
        service.request(applicant, first);
        service.ships(applicant);
        assertThrows(IllegalArgumentException.class, () -> service.request(applicant, first));
        assertThrows(IllegalArgumentException.class, () -> service.request(applicant, second));
        game.releasePlayer("player-CAP-test");
        assertEquals("Abgelaufen", service.requests(applicant).get(0).status());
        assertThrows(IllegalArgumentException.class, () -> service.request(applicant, first));
        assertDoesNotThrow(() -> service.request(applicant, second));
    }

    @Test
    void forgedEnemyTargetAndExistingControllerAreRejected() {
        String enemy = captain("ENEMY", "dark", "torpedo-boat");
        assertThrows(IllegalArgumentException.class, () -> service.request(applicant, enemy));
        String friendly = captain("CAP", "light", "torpedo-boat");
        captain("APP", "light", "torpedo-boat");
        players.register("player-APP-test", "APP", "Applicant", "light", applicant.id());
        assertThrows(IllegalArgumentException.class, () -> service.request(applicant, friendly));
    }

    @Test
    void newGameClearsRequestHistory() {
        service.request(applicant, captain("CAP", "light", "torpedo-boat"));
        game.resetCurrentSetup();
        assertTrue(service.requests(applicant).isEmpty());
    }
}
