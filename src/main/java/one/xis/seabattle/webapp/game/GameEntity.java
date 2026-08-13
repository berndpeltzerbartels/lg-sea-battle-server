package one.xis.seabattle.webapp.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.xis.sql.Entity;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity("games")
class GameEntity {
    String id;
    String status;
    LocalDateTime beginTime;
    LocalDateTime endTime;
}
