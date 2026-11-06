package one.xis.seabattle.webapp.gameconfig;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.xis.sql.Column;
import one.xis.sql.Entity;

@Data @NoArgsConstructor @AllArgsConstructor
@Entity("game_config_profiles")
class GameConfigProfileEntity {
    String id;
    String name;
    @Column("parameter_values") String parameterValues;
    @Column("setup_id") String setupId;
    @Column("game_mode") String mode;
}
