package one.xis.seabattle.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.xis.sql.Entity;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity("game_properties")
class GamePropertyEntity {
    String id;
    String propertyValue;
}
