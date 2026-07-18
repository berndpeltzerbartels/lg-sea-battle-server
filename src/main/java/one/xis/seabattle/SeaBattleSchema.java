package one.xis.seabattle;

import one.xis.ddl.Change;
import one.xis.ddl.ChangeSet;
import one.xis.ddl.DDL;

@ChangeSet("sea-battle-schema")
class SeaBattleSchema {

    @Change("001-create-accounts")
    void createAccounts(DDL ddl) {
        var accounts = ddl.createTableIfNotExists("accounts");
        accounts.addColumn("id").varchar(50).notNull().primaryKey();
        accounts.addColumn("nickname").varchar(255).notNull();
        accounts.addColumn("alias").varchar(5).notNull().unique();
        accounts.addColumn("team").varchar(20).notNull();
        accounts.addColumn("email").varchar(255).unique();
    }

    @Change("002-create-sessions")
    void createSessions(DDL ddl) {
        var accounts = ddl.createTableIfNotExists("accounts");
        accounts.addColumn("id").varchar(50).notNull().primaryKey();

        var sessions = ddl.createTableIfNotExists("sessions");
        sessions.addColumn("id").varchar(50).notNull().primaryKey();
        sessions.addColumn("account_id").varchar(50).notNull().foreignKey(accounts.getColumn("id"));
        sessions.addColumn("player_id").varchar(80).notNull();
        sessions.addColumn("begin_time").timestamp().notNull();
        sessions.addColumn("end_time").timestamp();
        sessions.addColumn("score").integer().notNull();
    }

    @Change("003-create-games")
    void createGames(DDL ddl) {
        var games = ddl.createTableIfNotExists("games");
        games.addColumn("id").varchar(50).notNull().primaryKey();
        games.addColumn("status").varchar(20).notNull();
        games.addColumn("begin_time").timestamp().notNull();
        games.addColumn("end_time").timestamp();
    }

    @Change("004-remove-global-account-alias-unique")
    void removeGlobalAccountAliasUnique(DDL ddl) {
        ddl.alterTable("accounts").dropUniqueConstraint("alias");
    }

    @Change("005-add-game-data-to-sessions")
    void addGameDataToSessions(DDL ddl) {
        var sessions = ddl.alterTable("sessions");
        sessions.addColumn("game_id").varchar(50);
        sessions.addColumn("alias").varchar(5);
        sessions.addColumn("team").varchar(20);
        ddl.sql("alter table sessions add constraint fk_sessions_games_game_id foreign key (game_id) references games (id)");
        ddl.sql("create unique index uq_sessions_game_id_alias on sessions (game_id, alias)");
    }

    @Change("006-limit-session-alias-unique-to-active-sessions")
    void limitSessionAliasUniqueToActiveSessions(DDL ddl) {
        ddl.alterTable("sessions")
                .dropIndexIfExists("uq_sessions_game_id_alias")
                .addUniqueIndexWhereNull("uq_sessions_game_id_alias", "end_time", "game_id", "alias");
    }

    @Change("007-close-sessions-for-ended-games")
    void closeSessionsForEndedGames(DDL ddl) {
        ddl.sql("""
                update sessions
                set end_time = (select games.end_time from games where games.id = sessions.game_id)
                where sessions.end_time is null
                  and exists (
                      select 1
                      from games
                      where games.id = sessions.game_id
                        and games.end_time is not null
                  )
                """);
    }
}
