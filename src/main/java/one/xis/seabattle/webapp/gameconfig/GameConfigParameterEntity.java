package one.xis.seabattle.webapp.gameconfig;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.xis.sql.Column;
import one.xis.sql.Entity;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity("game_config_parameters")
class GameConfigParameterEntity {
    String id;
    @Column("parameter_group")
    GameConfigParameterGroup group;
    @Column("parameter_type")
    GameConfigParameterType type;
    String name;
    String title;
    String description;
    @Column("parameter_value")
    String value;
}
