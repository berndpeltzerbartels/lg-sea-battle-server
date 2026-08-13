package one.xis.seabattle.webapp.contact;

import one.xis.validation.LabelKey;
import one.xis.validation.Mandatory;

import java.util.List;

record ContactPreferencesForm(

        @Mandatory
        @LabelKey("seaBattle.email")
        String email,
        Boolean weeklyUpdates,
        Boolean inviteToPlay,
        List<String> inviteWeekdays
) {
}
