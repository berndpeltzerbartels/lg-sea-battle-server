package one.xis.seabattle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.xis.sql.Entity;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity("contact_preferences")
class ContactPreferenceEntity {
    String accountId;
    String email;
    Boolean weeklyUpdates;
    Boolean inviteToPlay;
    String inviteWeekdays;
}
