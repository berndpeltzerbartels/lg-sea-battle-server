package one.xis.seabattle.webapp;

import one.xis.*;
import one.xis.seabattle.game.CrewRecruitmentService;
import one.xis.seabattle.game.CrewService;
import one.xis.seabattle.webapp.account.Account;
import one.xis.seabattle.webapp.account.AccountService;
import one.xis.validation.ValidationFailedException;
import java.util.List;

@Page("/crew.html")
@RefreshOnUpdateEvents(CrewRecruitmentService.UPDATE_EVENT)
public class SeaBattleCrewPage {
    private final AccountService accounts;
    private final CrewRecruitmentService recruitment;
    private final CrewService crew;

    public SeaBattleCrewPage(AccountService accounts, CrewRecruitmentService recruitment, CrewService crew) {
        this.accounts = accounts;
        this.recruitment = recruitment;
        this.crew = crew;
    }

    @ModelData("ready")
    boolean ready(@NullAllowed @LocalStorage("accountId") String id) { return id != null && crew.hasAccount(id); }

    @ModelData("boarding")
    PageUrlResponse boarding(@NullAllowed @LocalStorage("accountId") String id) {
        return ready(id) ? new PageUrlResponse("/app?vehicle=torpedo-boat")
                .localStorage("vehicleType", "torpedo-boat") : null;
    }

    @FormData("crew")
    CrewForm form() { return new CrewForm(""); }

    @ModelData("ships")
    List<CrewRecruitmentService.ShipOption> ships(@NullAllowed @LocalStorage("accountId") String id) {
        return accounts.findAccountById(id).map(recruitment::ships).orElse(List.of());
    }

    @ModelData("requests")
    List<CrewRecruitmentService.Request> requests(@NullAllowed @LocalStorage("accountId") String id) {
        return accounts.findAccountById(id).map(recruitment::requests).orElse(List.of());
    }

    @ModelData("availableShips")
    List<CrewRecruitmentService.ShipOption> availableShips(@NullAllowed @LocalStorage("accountId") String id) {
        return ships(id).stream().filter(CrewRecruitmentService.ShipOption::available).toList();
    }

    @ModelData("decisionNotice")
    String decisionNotice(@NullAllowed @LocalStorage("accountId") String id) {
        var history = requests(id);
        if (history.isEmpty()) return "";
        return switch (history.get(history.size() - 1).status()) {
            case "Abgelehnt" -> "Deine Anfrage wurde abgelehnt. Bei dieser Besatzung kannst du nach 15 Minuten erneut anfragen, bei anderen Schiffen sofort.";
            case "Angenommen" -> "Deine Anfrage wurde angenommen. Du gehst jetzt an Bord.";
            case "Abgelaufen" -> "Deine Anfrage ist abgelaufen. Bei derselben Besatzung kannst du zwei Minuten nach Ablauf erneut anfragen, bei anderen Schiffen sofort.";
            default -> "";
        };
    }

    @Action
    void request(@FormData("crew") CrewForm form, @NullAllowed @LocalStorage("accountId") String id) {
        Account account = accounts.findAccountById(id).orElse(null);
        if (account == null) throw new ValidationFailedException("/crew/shipId", "seaBattle.crewLoginRequired");
        try { recruitment.request(account, form.shipId()); }
        catch (IllegalArgumentException exception) {
            throw new ValidationFailedException("/crew/shipId", "seaBattle.crewUnavailable");
        }
    }

    public record CrewForm(String shipId) {}
}
