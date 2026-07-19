package one.xis.seabattle;

import one.xis.validation.LabelKey;

import java.util.List;

record ContactPreferencesForm(
        @LabelKey("seaBattle.email")
        String email,
        Boolean weeklyUpdates,
        Boolean inviteToPlay,
        List<String> inviteWeekdays
) {
}
