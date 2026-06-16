package one.xis.seabattle;

import one.xis.validation.LabelKey;
import one.xis.validation.Mandatory;
import one.xis.validation.RegExpr;

public record SeaBattleStartForm(
        @Mandatory
        @RegExpr("[\\p{L}0-9 .'-]{2,40}")
        @LabelKey("seaBattle.name")
        String name,

        @Mandatory
        @RegExpr("[A-Za-z0-9]{1,5}")
        @LabelKey("seaBattle.alias")
        String initials,

        @Mandatory
        @RegExpr("light|dark|green|sand")
        @LabelKey("seaBattle.team")
        String team
) {
}
