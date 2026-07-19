package one.xis.seabattle;

import one.xis.Action;
import one.xis.FormData;
import one.xis.LocalStorage;
import one.xis.ModelData;
import one.xis.NullAllowed;
import one.xis.Page;

import java.util.List;

@Page("/contact.html")
class SeaBattleContactPage {

    private static final List<WeekdayOption> WEEKDAYS = List.of(
            new WeekdayOption("monday", "Montag"),
            new WeekdayOption("tuesday", "Dienstag"),
            new WeekdayOption("wednesday", "Mittwoch"),
            new WeekdayOption("thursday", "Donnerstag"),
            new WeekdayOption("friday", "Freitag"),
            new WeekdayOption("saturday", "Samstag"),
            new WeekdayOption("sunday", "Sonntag")
    );

    private final ContactPreferenceService contactPreferenceService;

    SeaBattleContactPage(ContactPreferenceService contactPreferenceService) {
        this.contactPreferenceService = contactPreferenceService;
    }

    @FormData("preferences")
    ContactPreferencesForm preferences(@NullAllowed @LocalStorage("accountId") String accountId,
                                       @NullAllowed @LocalStorage("contactInviteToPlay") Boolean localInviteToPlay) {
        return contactPreferenceService.formForAccount(accountId, localInviteToPlay);
    }

    @ModelData("weekdays")
    List<WeekdayOption> weekdays() {
        return WEEKDAYS;
    }

    @ModelData("inviteToPlay")
    boolean inviteToPlay(@NullAllowed @LocalStorage("accountId") String accountId,
                         @NullAllowed @LocalStorage("contactInviteToPlay") Boolean localInviteToPlay) {
        return contactPreferenceService.inviteToPlay(accountId, localInviteToPlay);
    }

    @Action("refresh")
    @ModelData("inviteToPlay")
    @LocalStorage("contactInviteToPlay")
    boolean refresh(@NullAllowed @LocalStorage("accountId") String accountId,
                    @FormData("preferences") ContactPreferencesForm form) {
        return contactPreferenceService.save(accountId, form).inviteToPlay();
    }

    @Action("save")
    @FormData("preferences")
    ContactPreferencesForm save(@NullAllowed @LocalStorage("accountId") String accountId,
                                @FormData("preferences") ContactPreferencesForm form) {
        return contactPreferenceService.save(accountId, form);
    }

    record WeekdayOption(String id, String label) {
    }
}
