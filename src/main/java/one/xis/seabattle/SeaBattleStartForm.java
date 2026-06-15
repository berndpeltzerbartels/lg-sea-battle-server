package one.xis.seabattle;

import one.xis.validation.LabelKey;
import one.xis.validation.Mandatory;
import one.xis.validation.RegExpr;

public record SeaBattleStartForm(
        @Mandatory
        @RegExpr("[A-Za-z0-9]{1,5}")
        @LabelKey("seaBattle.playerName")
        String initials,

        @Mandatory
        @RegExpr("light|dark|green|sand")
        @LabelKey("seaBattle.team")
        String team
) {
}
