package one.xis.seabattle.webapp;

import one.xis.*;
import one.xis.seabattle.game.CrewRecruitmentService;
import one.xis.seabattle.webapp.account.AccountService;
import one.xis.validation.ValidationFailedException;
import java.util.List;

@Page("/crew-inbox.html")
@RefreshOnUpdateEvents(CrewRecruitmentService.UPDATE_EVENT)
public class SeaBattleCrewInboxPage {
    private final AccountService accounts;
    private final CrewRecruitmentService recruitment;

    public SeaBattleCrewInboxPage(AccountService accounts, CrewRecruitmentService recruitment) {
        this.accounts = accounts;
        this.recruitment = recruitment;
    }

    @ModelData("inbox")
    List<CrewRecruitmentService.Request> inbox(@NullAllowed @LocalStorage("accountId") String id) {
        return accounts.findAccountById(id).map(recruitment::inbox).orElse(List.of()).stream().limit(1).toList();
    }

    @FormData("decision")
    DecisionForm form() { return new DecisionForm(""); }

    @Action
    void accept(@ActionParameter("requestId") String requestId, @LocalStorage("accountId") String id) {
        decide(id, requestId, true);
    }

    @Action
    void decline(@ActionParameter("requestId") String requestId, @LocalStorage("accountId") String id) {
        decide(id, requestId, false);
    }

    private void decide(String id, String requestId, boolean accept) {
        var account = accounts.findAccountById(id).orElseThrow(() -> new IllegalArgumentException("Bitte anmelden."));
        try { recruitment.decide(account, requestId, accept); }
        catch (IllegalArgumentException exception) {
            throw new ValidationFailedException("/decision/requestId", "seaBattle.crewDecisionUnavailable");
        }
    }
    public record DecisionForm(String requestId) {}
}
