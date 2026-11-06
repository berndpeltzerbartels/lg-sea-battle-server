package one.xis.seabattle.webapp.gameconfig;

import one.xis.ddl.DDL;
import one.xis.sql.SimpleDataSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameConfigSchemaTest {
    @Test void configurationChangesFollowTheExistingApplicationSchema() {
        assertEquals(one.xis.seabattle.game.SeaBattleSchema.class,
                GameConfigSchema.class.getAnnotation(one.xis.ddl.ChangeSet.class).previous());
    }

    private SimpleDataSource database() {
        var source = new SimpleDataSource();
        source.setUrl("jdbc:h2:mem:game-config-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        source.setUser("sa");
        source.setPassword("");
        source.setPoolEnabled(false);
        return source;
    }

    private void create(SimpleDataSource source) {
        var ddl = new DDL(source);
        new GameConfigSchema().createParameters(ddl);
        ddl.execute();
    }

    private void seed(SimpleDataSource source) {
        var ddl = new DDL(source);
        new GameConfigSchema().seedTorpedoBoatBot(ddl);
        ddl.execute();
    }

    @Test void createsAnEmptyTableThenInsertsTypedAndDescribedCurrentDefaults() throws Exception {
        var source = database();
        create(source);
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("select count(*) from game_config_parameters")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1), "Table creation must not insert rows implicitly");
            }
            seed(source);
            int count = 0;
            try (var result = statement.executeQuery("select * from game_config_parameters")) {
                while (result.next()) {
                    count++;
                    assertEquals(GameConfigParameterGroup.TORPEDO_BOAT_BOT,
                            GameConfigParameterGroup.valueOf(result.getString("parameter_group")));
                    assertDoesNotThrow(() -> GameConfigParameterType.valueOf(result.getString("parameter_type")));
                    assertEquals("TORPEDO_BOAT_BOT." + result.getString("name"), result.getString("id"));
                    assertFalse(result.getString("title").isBlank());
                    assertFalse(result.getString("description").isBlank());
                    assertDoesNotThrow(() -> Double.parseDouble(result.getString("parameter_value")));
                }
            }
            assertEquals(17, count);
            try (var result = statement.executeQuery("""
                    select parameter_value, parameter_type from game_config_parameters
                    where id = 'TORPEDO_BOAT_BOT.TORPEDO_COOLDOWN_MIN'
                    """)) {
                assertTrue(result.next());
                assertEquals("10.5", result.getString(1));
                assertEquals("SECONDS", result.getString(2));
            }
        }
    }

    @Test void repeatedMigrationPreservesExistingEditsAndFillsOnlyMissingRows() throws Exception {
        var source = database();
        create(source);
        seed(source);
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    update game_config_parameters set parameter_value = '0.72', title = 'Eigene Einstellung'
                    where id = 'TORPEDO_BOAT_BOT.AIM_RESPONSE'
                    """);
            statement.executeUpdate("delete from game_config_parameters where id = 'TORPEDO_BOAT_BOT.FIRE_MAX_RANGE'");
            create(source);
            seed(source);
            seed(source);
            try (var result = statement.executeQuery("select count(*) from game_config_parameters")) {
                assertTrue(result.next());
                assertEquals(17, result.getInt(1));
            }
            try (var result = statement.executeQuery("""
                    select parameter_value, title from game_config_parameters
                    where id = 'TORPEDO_BOAT_BOT.AIM_RESPONSE'
                    """)) {
                assertTrue(result.next());
                assertEquals("0.72", result.getString(1), "Do not overwrite an edited value");
                assertEquals("Eigene Einstellung", result.getString(2));
            }
            try (var result = statement.executeQuery("""
                    select parameter_value from game_config_parameters
                    where id = 'TORPEDO_BOAT_BOT.FIRE_MAX_RANGE'
                    """)) {
                assertTrue(result.next());
                assertEquals("690", result.getString(1));
            }
        }
    }
    @Test void upgradingSeedsBothFleetSizesWithoutOverwritingChanges() throws Exception {
        var source = database(); create(source); seed(source);
        var ddl = new DDL(source); new GameConfigSchema().seedFleetSizes(ddl); ddl.execute();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("update game_config_parameters set parameter_value = '12' where id = 'FLEET.LIGHT_VEHICLES'");
        }
        ddl = new DDL(source); new GameConfigSchema().seedFleetSizes(ddl); ddl.execute();
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("select parameter_value from game_config_parameters where id='FLEET.LIGHT_VEHICLES'")) {
            assertTrue(result.next()); assertEquals("12",result.getString(1));
        }
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("select count(*) from game_config_parameters")) {
            assertTrue(result.next()); assertEquals(19,result.getInt(1));
        }
    }

    @Test void upgradingSplitsTheFormerTotalAndPreservesEditedTypeCounts() throws Exception {
        var source = database(); create(source);
        var ddl = new DDL(source); new GameConfigSchema().seedFleetSizes(ddl); ddl.execute();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("update game_config_parameters set parameter_value='12' where id='FLEET.LIGHT_VEHICLES'");
        }
        ddl = new DDL(source); new GameConfigSchema().seedFleetVehicleTypes(ddl); ddl.execute();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("select sum(cast(parameter_value as integer)) from game_config_parameters where id in ('FLEET.LIGHT_TORPEDO_BOATS','FLEET.LIGHT_SUBMARINES','FLEET.LIGHT_PLANES')")) {
                assertTrue(result.next()); assertEquals(12,result.getInt(1));
            }
            statement.executeUpdate("update game_config_parameters set parameter_value='5' where id='FLEET.LIGHT_SUBMARINES'");
        }
        ddl = new DDL(source); new GameConfigSchema().seedFleetVehicleTypes(ddl); ddl.execute();
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("select parameter_value from game_config_parameters where id='FLEET.LIGHT_SUBMARINES'")) {
            assertTrue(result.next()); assertEquals("5",result.getString(1));
        }
    }

    @Test void profileTableCanBeUpgradedRepeatedlyWithoutLosingStoredProfiles() throws Exception {
        var source=database();
        var ddl=new DDL(source);new GameConfigSchema().createProfiles(ddl);ddl.execute();
        try(var connection=source.getConnection();var statement=connection.createStatement()) {
            statement.executeUpdate("insert into game_config_profiles(id,name,parameter_values,setup_id,game_mode) values('test','Test','{}','islands','ENDLESS')");
        }
        ddl=new DDL(source);new GameConfigSchema().createProfiles(ddl);ddl.execute();
        try(var connection=source.getConnection();var statement=connection.createStatement();
            var result=statement.executeQuery("select name,parameter_values,setup_id,game_mode from game_config_profiles where id='test'")) {
            assertTrue(result.next());assertEquals("Test",result.getString(1));assertEquals("{}",result.getString(2));
            assertEquals("islands",result.getString(3));assertEquals("ENDLESS",result.getString(4));
        }
    }

    @Test void uniqueProfileNamesAreBackfilledAndEnforcedByTheDatabase() throws Exception {
        var source=database();var ddl=new DDL(source);new GameConfigSchema().createProfiles(ddl);ddl.execute();
        try(var connection=source.getConnection();var statement=connection.createStatement()) {
            statement.executeUpdate("insert into game_config_profiles values('stable-id','First','{}','dense-land','ENDLESS')");
        }
        ddl=new DDL(source);new GameConfigSchema().uniqueProfileNames(ddl);ddl.execute();
        try(var connection=source.getConnection();var statement=connection.createStatement()) {
            assertThrows(java.sql.SQLException.class,()->statement.executeUpdate("insert into game_config_profiles(id,name,name_key,parameter_values,setup_id,game_mode) values('another-id','FIRST','first','{}','dense-land','ENDLESS')"));
            statement.executeUpdate("update game_config_profiles set name='Renamed',name_key='renamed' where id='stable-id'");
            try(var result=statement.executeQuery("select name from game_config_profiles where id='stable-id'")) {
                assertTrue(result.next());assertEquals("Renamed",result.getString(1));
            }
        }
    }

}
