package one.xis.seabattle.webapp.playsession;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.xis.sql.Entity;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity("sessions")
class PlaySessionEntity {
    String id;
    String gameId;
    String accountId;
    String playerId;
    String alias;
    String team;
    LocalDateTime beginTime;
    LocalDateTime endTime;
    int score;
}
