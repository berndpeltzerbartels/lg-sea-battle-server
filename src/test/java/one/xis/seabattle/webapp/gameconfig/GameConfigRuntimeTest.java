package one.xis.seabattle.webapp.gameconfig;

import one.xis.context.Scheduled;
import one.xis.ddl.DDL;
import one.xis.sql.SimpleDataSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GameConfigRuntimeTest {
    static List<GameConfigParameterEntity> defaults() throws Exception { return defaults(false); }

    static List<GameConfigParameterEntity> defaults(boolean includeFleet) throws Exception {
        var source = new SimpleDataSource();
        source.setUrl("jdbc:h2:mem:runtime-config-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        source.setUser("sa");
        source.setPassword("");
        source.setPoolEnabled(false);
        var schema = new GameConfigSchema();
        var ddl = new DDL(source);
        schema.createParameters(ddl);
        schema.seedTorpedoBoatBot(ddl);
        if (includeFleet) { schema.seedFleetSizes(ddl); schema.seedFleetVehicleTypes(ddl); }
        ddl.execute();
        var rows = new ArrayList<GameConfigParameterEntity>();
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("select * from game_config_parameters")) {
            while (result.next()) rows.add(new GameConfigParameterEntity(result.getString("id"),
                    GameConfigParameterGroup.valueOf(result.getString("parameter_group")),
                    GameConfigParameterType.valueOf(result.getString("parameter_type")), result.getString("name"),
                    result.getString("title"), result.getString("description"), result.getString("parameter_value")));
        }
        return List.copyOf(rows);
    }

    private List<GameConfigParameterEntity> withValue(List<GameConfigParameterEntity> rows, String name, String value) {
        return rows.stream().map(p -> p.getName().equals(name)
                ? new GameConfigParameterEntity(p.getId(), p.getGroup(), p.getType(), p.getName(),
                    p.getTitle(), p.getDescription(), value) : p).toList();
    }

    @Test void fleetSettingsAreLoadedFromTheirOwnGroup() throws Exception {
        var repository = new MemoryRepository(withValue(defaults(true), "LIGHT_TORPEDO_BOATS", "12"));
        var service = new GameConfigServiceImpl(repository);
        service.refresh();
        assertEquals(new FleetSizeConfig(12, 16, 3, 3, 1, 1), service.current().fleetSize());
    }

    @Test void startsWithDefaultsAndHotReadsNeverQueryTheDatabase() throws Exception {
        var repository = new MemoryRepository(defaults());
        var service = new GameConfigServiceImpl(repository);
        GameRuntimeConfig first = service.current();
        assertEquals(GameRuntimeConfig.defaults(), first);
        for (int i = 0; i < 10000; i++) assertSame(first, service.current());
        assertEquals(0, repository.queries.get());
        service.refresh();
        assertEquals(1, repository.queries.get(), "One query loads all groups");
        assertSame(first, service.current(), "Identical values need no new publication");
        assertEquals(first, GameRuntimeConfig.fromParameters(service.findAll()),
                "The runtime defaults must match the actual DDL seed data");
    }

    @Test void saveWaitsForRefreshAndOldCapturedRoundsKeepTheirValues() throws Exception {
        var repository = new MemoryRepository(defaults());
        var service = new GameConfigServiceImpl(repository);
        var captured = service.current();
        var p = service.findById("TORPEDO_BOAT_BOT.AIM_RESPONSE").orElseThrow();
        service.save(new GameConfigParameter(p.id(), p.group(), p.type(), p.name(), p.title(), p.description(), "0.70"));
        assertSame(captured, service.current(), "Only the scheduler publishes runtime changes");
        service.refresh();
        assertNotSame(captured, service.current());
        assertEquals(.70, service.current().torpedoBoatBot().aimResponse());
        assertEquals(.40, captured.torpedoBoatBot().aimResponse(), "An in-progress round keeps its immutable values");
    }

    @Test void invalidIncompleteOrFailedLoadsNeverReplaceTheLastValidStand() throws Exception {
        var rows = defaults();
        var repository = new MemoryRepository(withValue(rows, "AIM_RESPONSE", ".60"));
        var service = new GameConfigServiceImpl(repository);
        service.refresh();
        var valid = service.current();
        for (String bad : List.of("NaN", "Infinity", "not-a-number")) {
            repository.rows = withValue(rows, "AIM_RESPONSE", bad);
            service.refresh();
            assertSame(valid, service.current());
        }
        repository.rows = withValue(rows, "TARGET_PREFERRED_ATTACKERS", "2.5");
        service.refresh();
        assertSame(valid, service.current(), "Do not truncate fractional attacker counts");
        repository.rows = rows.subList(1, rows.size());
        service.refresh();
        assertSame(valid, service.current(), "Missing rows must not silently reset a value");
        repository.rows = new ArrayList<>(rows);
        repository.rows.add(rows.get(0));
        service.refresh();
        assertSame(valid, service.current(), "Duplicate parameter names are ambiguous");
        var wrongType = withValue(rows, "AIM_RESPONSE", "0.80");
        wrongType.stream().filter(p -> p.getName().equals("AIM_RESPONSE")).findFirst().orElseThrow()
                .setType(GameConfigParameterType.STRING);
        repository.rows = wrongType;
        service.refresh();
        assertSame(valid, service.current());
        repository.failure = true;
        service.refresh();
        assertSame(valid, service.current(), "Database failure retains the last good snapshot");
    }

    @Test void publicationNeverMixesValuesAcrossConcurrentRounds() throws Exception {
        var rows = defaults();
        var a = withValue(withValue(rows, "AIM_RESPONSE", ".4"), "TORPEDO_COOLDOWN_MIN", "10.5");
        var b = withValue(withValue(rows, "AIM_RESPONSE", ".8"), "TORPEDO_COOLDOWN_MIN", "20");
        var repository = new MemoryRepository(a);
        var service = new GameConfigServiceImpl(repository);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var writer = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 2000; i++) {
                    repository.rows = i % 2 == 0 ? a : b;
                    service.refresh();
                }
                return null;
            });
            var reader = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 50000; i++) {
                    var config = service.current().torpedoBoatBot();
                    assertTrue(config.aimResponse() == .4 && config.torpedoCooldownMin() == 10.5
                            || config.aimResponse() == .8 && config.torpedoCooldownMin() == 20);
                }
                return null;
            });
            start.countDown();
            writer.get(10, TimeUnit.SECONDS);
            reader.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void xisSchedulesReloadEveryThirtySecondsWithAnImmediateFirstLoad() throws Exception {
        Scheduled schedule = GameConfigServiceImpl.class.getMethod("refresh").getAnnotation(Scheduled.class);
        assertNotNull(schedule);
        assertEquals(30, schedule.fixedDelay());
        assertEquals(TimeUnit.SECONDS, schedule.timeUnit());
        assertEquals(0, schedule.initialDelay());
    }

    static final class MemoryRepository implements GameConfigParameterRepository {
        private volatile List<GameConfigParameterEntity> rows;
        private final AtomicInteger queries = new AtomicInteger();
        private boolean failure;

        MemoryRepository(List<GameConfigParameterEntity> rows) { this.rows = rows; }

        @Override public List<GameConfigParameterEntity> findAll() {
            queries.incrementAndGet();
            if (failure) throw new IllegalStateException("Database unavailable");
            return List.copyOf(rows);
        }
        @Override public Optional<GameConfigParameterEntity> findById(String id) {
            return rows.stream().filter(p -> p.getId().equals(id)).findFirst();
        }
        @Override public GameConfigParameterEntity save(GameConfigParameterEntity entity) {
            rows = rows.stream().map(p -> p.getId().equals(entity.getId()) ? entity : p).toList();
            return entity;
        }
        @Override public boolean delete(GameConfigParameterEntity entity) { return deleteById(entity.getId()); }
        @Override public boolean deleteById(String id) {
            int before = rows.size();
            rows = rows.stream().filter(p -> !p.getId().equals(id)).toList();
            return rows.size() != before;
        }
        @Override public long count() { return rows.size(); }
    }
}
