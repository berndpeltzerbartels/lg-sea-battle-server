package one.xis.seabattle;

import one.xis.context.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
class ContactPreferenceService {

    private static final List<String> WEEKDAYS = List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    private final ContactPreferenceRepository repository;
    private final AccountService accountService;

    ContactPreferenceService(ContactPreferenceRepository repository, AccountService accountService) {
        this.repository = repository;
        this.accountService = accountService;
    }

    ContactPreferencesForm formForAccount(String accountId, Boolean localInviteToPlay) {
        Optional<Account> account = accountService.findAccountById(accountId);
        return repository.findById(accountId)
                .map(entity -> new ContactPreferencesForm(
                        firstNonBlank(entity.getEmail(), account.map(Account::email).orElse("")),
                        trueOrFalse(entity.getWeeklyUpdates()),
                        trueOrFalse(entity.getInviteToPlay()),
                        weekdaysFromStorage(entity.getInviteWeekdays())))
                .orElseGet(() -> new ContactPreferencesForm(
                        account.map(Account::email).orElse(""),
                        false,
                        trueOrFalse(localInviteToPlay),
                        List.of()));
    }

    ContactPreferencesForm save(String accountId, ContactPreferencesForm form) {
        if (accountId == null || accountId.isBlank() || accountService.findAccountById(accountId).isEmpty()) {
            return normalize(form);
        }
        ContactPreferencesForm normalized = normalize(form);
        repository.save(new ContactPreferenceEntity(
                accountId,
                normalized.email(),
                normalized.weeklyUpdates(),
                normalized.inviteToPlay(),
                weekdaysToStorage(normalized.inviteWeekdays())));
        return normalized;
    }

    boolean canSave(String accountId) {
        return accountId != null && !accountId.isBlank() && accountService.findAccountById(accountId).isPresent();
    }

    boolean inviteToPlay(String accountId, Boolean localInviteToPlay) {
        return formForAccount(accountId, localInviteToPlay).inviteToPlay();
    }

    private ContactPreferencesForm normalize(ContactPreferencesForm form) {
        boolean inviteToPlay = trueOrFalse(form.inviteToPlay());
        return new ContactPreferencesForm(
                normalizeEmail(form.email()),
                trueOrFalse(form.weeklyUpdates()),
                inviteToPlay,
                inviteToPlay ? normalizeWeekdays(form.inviteWeekdays()) : List.of());
    }

    private List<String> normalizeWeekdays(List<String> weekdays) {
        if (weekdays == null || weekdays.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String weekday : WEEKDAYS) {
            if (weekdays.stream().anyMatch(value -> weekday.equals(normalizeWeekday(value)))) {
                normalized.add(weekday);
            }
        }
        return normalized;
    }

    private String normalizeWeekday(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim();
    }

    private boolean trueOrFalse(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private String weekdaysToStorage(List<String> weekdays) {
        return String.join(",", weekdays == null ? List.of() : weekdays);
    }

    private List<String> weekdaysFromStorage(String weekdays) {
        if (weekdays == null || weekdays.isBlank()) {
            return List.of();
        }
        return normalizeWeekdays(List.of(weekdays.split(",")));
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }
}
