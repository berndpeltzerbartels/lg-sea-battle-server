package one.xis.seabattle.webapp.contact;

import one.xis.*;
import one.xis.seabattle.webapp.SeaBattleStartPage;

import java.util.List;

@Page("/contact.html")
class ContactPage {

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

    ContactPage(ContactPreferenceService contactPreferenceService) {
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
    Class<?> save(@NullAllowed @LocalStorage("accountId") String accountId,
                  @FormData("preferences") ContactPreferencesForm form,
                  ToastMessages toastMessages) {
        contactPreferenceService.save(accountId, form);
        if (contactPreferenceService.canSave(accountId)) {
            toastMessages.show("Gespeichert.", ToastLevel.SUCCESS);
        } else {
            toastMessages.show("Zum dauerhaften Speichern bitte erst in Sea Battle einsteigen.", ToastLevel.WARNING);
        }
        return SeaBattleStartPage.class;
    }

    record WeekdayOption(String id, String label) {
    }
}
