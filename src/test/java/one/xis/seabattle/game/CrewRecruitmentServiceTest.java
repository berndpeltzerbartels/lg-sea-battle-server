package one.xis.seabattle.game;

import one.xis.seabattle.webapp.account.Account;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrewRecruitmentServiceTest {
    private final GameStateService game = new GameStateService(new DefaultGameSetupFactory(new WorldMapService()),
            new RadarService(), new NavigationService());
    private final SeaBattlePlayerRegistry players = new SeaBattlePlayerRegistry();
    private final java.util.List<one.xis.RefreshEvent> events = new java.util.ArrayList<>();
    private final TestClock clock = new TestClock();
    private final CrewService crew = new CrewService(game, players);
    private final java.util.Map<String, Account> savedAccounts = new java.util.HashMap<>();
    private final one.xis.seabattle.webapp.account.AccountService accounts = new one.xis.seabattle.webapp.account.AccountService() {
        public Account saveAccount(Account account) {
            savedAccounts.put(account.id(), account);
            return account;
        }
        public java.util.Optional<Account> findAccountById(String id) {
            return java.util.Optional.ofNullable(savedAccounts.get(id));
        }
    };
    private final CrewRecruitmentService service = new CrewRecruitmentService(game, players, events::add, crew, accounts) {
        @Override long nowMillis() { return clock.millis(); }
    };
    private final Account applicant = new Account("applicant", "Applicant", "APP", "light", null);

    private static class TestClock extends java.time.Clock {
        long now = 1_000_000;
        public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        public java.time.Instant instant() { return java.time.Instant.ofEpochMilli(now); }
        public long millis() { return now; }
    }

    @Test
    void declineBlocksForFifteenMinutesAndExpiryPublishesSse() {
        String ship = captain("CAP", "light", "torpedo-boat");
        var recipient = new Account("CAP", "Captain", "CAP", "light", null);
        service.request(applicant, ship);
        String oldId = service.requests(applicant).get(0).id();
        service.decide(recipient, oldId, false);
        service.publishChanges();
        int count = events.size();
        clock.now += 899_999;
        service.publishChanges();
        assertEquals(count, events.size());
        assertThrows(IllegalArgumentException.class, () -> service.request(applicant, ship));
        clock.now++;
        service.publishChanges();
        assertEquals(count + 1, events.size());
        assertTrue(service.ships(applicant).get(0).available());
        service.request(applicant, ship);
        assertThrows(IllegalArgumentException.class, () -> service.decide(recipient, oldId, true));
    }

    @Test
    void unansweredRequestIsDeclinedAndBlockedForFifteenMinutesFromDeadline() {
        String ship = captain("CAP", "light", "torpedo-boat");
        var recipient = new Account("CAP", "Captain", "CAP", "light", null);
        service.request(applicant, ship);
        String id = service.requests(applicant).get(0).id();
        clock.now += 119_999;
        assertEquals("Offen", service.requests(applicant).get(0).status());
        service.publishChanges();
        int eventCount = events.size();
        clock.now++;
        service.publishChanges();
        assertEquals(eventCount + 1, events.size());
        assertEquals("Abgelehnt", service.requests(applicant).get(0).status());
        assertTrue(service.inbox(recipient).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.decide(recipient, id, true));
        clock.now += 899_999;
        assertThrows(IllegalArgumentException.class, () -> service.request(applicant, ship));
        clock.now++;
        assertDoesNotThrow(() -> service.request(applicant, ship));
    }

    @Test
    void cooldownFollowsControllerToAnotherShipButNotANewHumanSession() {
        String ship = captain("CAP", "light", "torpedo-boat");
        var recipient = new Account("CAP", "Captain", "CAP", "light", null);
        service.request(applicant, ship);
        service.decide(recipient, service.requests(applicant).get(0).id(), false);
        game.releasePlayer("player-CAP-test");
        service.publishChanges();
        String respawn = captain("CAP", "light", "torpedo-boat");
        assertThrows(IllegalArgumentException.class, () -> service.request(applicant, respawn));
        players.unregisterPlayer("player-CAP-test");
        crew.leave("player-CAP-test");
        game.releasePlayer("player-CAP-test");
        service.publishChanges();
        String next = captain("NEW", "light", "torpedo-boat");
        assertDoesNotThrow(() -> service.request(applicant, next));
    }

    private String captain(String alias, String team, String type) {
        String id = "player-" + alias + "-test";
        players.register(id, alias, alias, team, alias);
        game.activateTeam(team);
        game.assignPlayerVehicle(id, team, type);
        return game.snapshot().ships().stream().filter(s -> id.equals(s.controlledBy())).findFirst().orElseThrow().id();
    }

    @Test
    void humanTorpedoBoatsOfBothTeamsAreOfferedAndApplyingDoesNotAssignShip() {
        String other = captain("ENEMY", "dark", "torpedo-boat");
        String ship = captain("CAP", "light", "torpedo-boat");
        captain("SUB", "light", "submarine");
        assertEquals(java.util.Set.of(ship, other), new java.util.HashSet<>(service.ships(applicant).stream().map(CrewRecruitmentService.ShipOption::id).toList()));
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
    void crewNamesIncludeEveryoneAndJoiningAndLeavingPublishUpdates() {
        captain("CAP", "light", "torpedo-boat");
        assertEquals("CAP", service.ships(applicant).get(0).crew());
        service.publishChanges();
        var gunner = crew.join("player-CAP-test", new Account("gunner", "Gunner", "GUN", "light", null));
        service.publishChanges();
        assertEquals(2, events.size());
        assertEquals("CAP, Gunner", service.ships(applicant).get(0).crew());
        crew.join("player-CAP-test", new Account("third", "Third", "THI", "light", null));
        service.publishChanges();
        assertEquals(3, events.size());
        assertEquals("CAP, Gunner, Third", service.ships(applicant).get(0).crew());
        service.publishChanges();
        assertEquals(3, events.size());
        String gunnerId = gunner.members().stream().filter(m -> m.name().equals("Gunner")).findFirst().orElseThrow().playerId();
        crew.leave(gunnerId);
        service.publishChanges();
        assertEquals(4, events.size());
        assertEquals("CAP, Third", service.ships(applicant).get(0).crew());
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
    void joiningOtherTeamPersistsThatTeamAndExistingControllersAreRejected() {
        String enemy = captain("ENEMY", "dark", "torpedo-boat");
        service.request(applicant, enemy);
        service.decide(new Account("ENEMY", "Enemy", "ENEMY", "dark", null),
                service.requests(applicant).get(0).id(), true);
        assertEquals(new Account(applicant.id(), applicant.nickname(), applicant.alias(), "dark", null),
                savedAccounts.get(applicant.id()));
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

    @Test
    void onlyRecipientCanDecideAndRepeatedDecisionIsIdempotent() {
        String ship = captain("CAP", "light", "torpedo-boat");
        var recipient = new Account("CAP", "Captain", "CAP", "light", null);
        service.request(applicant, ship);
        var request = service.inbox(recipient).get(0);
        assertThrows(IllegalArgumentException.class, () -> service.decide(applicant, request.id(), true));
        service.decide(recipient, request.id(), true);
        assertEquals("Angenommen", service.requests(applicant).get(0).status());
        assertTrue(service.inbox(recipient).isEmpty());
        assertDoesNotThrow(() -> service.decide(recipient, request.id(), true));
        assertThrows(IllegalArgumentException.class, () -> service.decide(recipient, request.id(), false));
        assertThrows(IllegalArgumentException.class, () -> service.request(applicant, ship));
    }

    @Test
    void declineAndCaptainDepartureCannotBecomeAcceptance() {
        String ship = captain("CAP", "light", "torpedo-boat");
        var recipient = new Account("CAP", "Captain", "CAP", "light", null);
        service.request(applicant, ship);
        String id = service.inbox(recipient).get(0).id();
        service.decide(recipient, id, false);
        assertEquals("Abgelehnt", service.requests(applicant).get(0).status());
        assertThrows(IllegalArgumentException.class, () -> service.decide(recipient, id, true));
        var another = new Account("another", "Another", "TWO", "light", null);
        service.request(another, ship);
        String nextId = service.inbox(recipient).get(0).id();
        game.releasePlayer("player-CAP-test");
        assertThrows(IllegalArgumentException.class, () -> service.decide(recipient, nextId, true));
        assertEquals("Abgelaufen", service.requests(another).get(0).status());
    }

    @Test
    void acceptBoardsAtMostTwoAdditionalPeople() {
        String ship = captain("CAP", "light", "torpedo-boat");
        var recipient = new Account("CAP", "Captain", "CAP", "light", null);
        for (int i = 0; i < 3; i++) {
            var account = new Account("crew" + i, "Crew", "C" + i, "light", null);
            service.request(account, ship);
            String id = service.requests(account).get(0).id();
            if (i < 2) service.decide(recipient, id, true);
            else assertThrows(IllegalArgumentException.class, () -> service.decide(recipient, id, true));
        }
    }

    @Test
    void thirdPersonCanApplyWhenBridgeIsEmptyAndBothGunsAreOccupied() {
        String ship = captain("CAP", "light", "torpedo-boat");
        var recipient = new Account("CAP", "Captain", "CAP", "light", null);
        service.request(applicant, ship);
        service.decide(recipient, service.requests(applicant).get(0).id(), true);
        var captainView = crew.view("player-CAP-test");
        crew.switchStation(new CrewService.Command("player-CAP-test", ship,
                captainView.revision(), "cannon", null, null, null));
        var third = new Account("third", "Third", "THIRD", null, null);
        assertTrue(service.ships(third).stream().anyMatch(s -> s.id().equals(ship) && s.available()));
        service.request(third, ship);
        service.decide(recipient, service.requests(third).get(0).id(), true);
        var members = crew.view("player-CAP-test").members();
        assertEquals(3, members.size());
        assertTrue(members.stream().anyMatch(m -> m.name().equals("Third") && m.station().equals("bridge")));
        assertEquals("light", savedAccounts.get("third").team());
    }
}
