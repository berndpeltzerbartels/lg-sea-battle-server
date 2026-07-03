package one.xis.seabattle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.xis.sql.Entity;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity("accounts")
class AccountEntity {
    String id;
    String nickname;
    String alias;
    String team;
    String email;
}
