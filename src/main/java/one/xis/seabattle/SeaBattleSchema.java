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
}
