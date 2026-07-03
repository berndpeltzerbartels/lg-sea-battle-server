package one.xis.seabattle;

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
    String accountId;
    String playerId;
    LocalDateTime beginTime;
    LocalDateTime endTime;
    int score;
}
