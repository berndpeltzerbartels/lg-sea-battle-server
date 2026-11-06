package one.xis.seabattle.webapp.gameconfig;

import one.xis.UseFormatter;
import one.xis.validation.Mandatory;
import one.xis.validation.LabelKey;
import one.xis.seabattle.webapp.validation.Range;
import one.xis.seabattle.webapp.validation.WholeNumberFormatter;
import java.util.List;

/** Single editor and single source of field validation for all profile settings. */
@ValidGameConfig
record GameSettingsForm(String profileId, String name, @Mandatory String setupId, @Mandatory String mode,
        @Mandatory @Range(min = 0, minInclusive = false) @LabelKey("gameConfig.aimResponse") Double aimResponse,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.aimTurnDamping") Double aimTurnDamping,
        @Mandatory @Range(min = 0, max = 180) @LabelKey("gameConfig.aimMinCorrectionAngle") Double aimMinCorrectionAngle,
        @Mandatory @Range(min = 0, minInclusive = false, max = 180) @LabelKey("gameConfig.fireArc") Double fireArc,
        @Mandatory @Range(min = 0, max = 180) @LabelKey("gameConfig.closeFireArc") Double closeFireArc,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.fireMinRange") Double fireMinRange,
        @Mandatory @Range(min = 0, minInclusive = false) @LabelKey("gameConfig.fireMaxRange") Double fireMaxRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.closeFireRange") Double closeFireRange,
        @Mandatory @Range(min = 0, minInclusive = false) @LabelKey("gameConfig.torpedoCooldownMin") Double torpedoCooldownMin,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.torpedoCooldownVariation") Double torpedoCooldownVariation,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.approachSlowRange") Double approachSlowRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.closeManeuverRange") Double closeManeuverRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.radarInterceptRange") Double radarInterceptRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.ramRange") Double ramRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.humanTargetPriorityClearance") Double humanTargetPriorityClearance,
        @Mandatory @Range(min = 1) @UseFormatter(WholeNumberFormatter.class) @LabelKey("gameConfig.targetPreferredAttackers") Integer targetPreferredAttackers,
        @Mandatory @Range(min = 1) @LabelKey("gameConfig.targetAlternativeDistanceFactor") Double targetAlternativeDistanceFactor,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.lightTorpedoBoats") Integer lightTorpedoBoats,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.darkTorpedoBoats") Integer darkTorpedoBoats,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.lightSubmarines") Integer lightSubmarines,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.darkSubmarines") Integer darkSubmarines,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.lightPlanes") Integer lightPlanes,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.darkPlanes") Integer darkPlanes) {
    static GameSettingsForm from(String id,String name,String setup,String mode,List<GameConfigParameter> values) {
        var config=GameConfigForm.fromParameters(values);
        var fleet=FleetSizeForm.fromParameters(values);
        return new GameSettingsForm(id,name,setup,mode,
                config.aimResponse(), config.aimTurnDamping(), config.aimMinCorrectionAngle(), config.fireArc(), config.closeFireArc(), config.fireMinRange(), config.fireMaxRange(), config.closeFireRange(), config.torpedoCooldownMin(), config.torpedoCooldownVariation(), config.approachSlowRange(), config.closeManeuverRange(), config.radarInterceptRange(), config.ramRange(), config.humanTargetPriorityClearance(), config.targetPreferredAttackers(), config.targetAlternativeDistanceFactor(),
                fleet.lightTorpedoBoats(), fleet.darkTorpedoBoats(), fleet.lightSubmarines(), fleet.darkSubmarines(), fleet.lightPlanes(), fleet.darkPlanes());
    }
    GameConfigForm config() { return new GameConfigForm(aimResponse, aimTurnDamping, aimMinCorrectionAngle, fireArc, closeFireArc, fireMinRange, fireMaxRange, closeFireRange, torpedoCooldownMin, torpedoCooldownVariation, approachSlowRange, closeManeuverRange, radarInterceptRange, ramRange, humanTargetPriorityClearance, targetPreferredAttackers, targetAlternativeDistanceFactor); }
    FleetSizeForm fleetSize() { return new FleetSizeForm(lightTorpedoBoats, darkTorpedoBoats, lightSubmarines, darkSubmarines, lightPlanes, darkPlanes); }
    List<GameConfigParameter> parameters(List<GameConfigParameter> current) {
        return fleetSize().applyTo(config().applyTo(current));
    }
}
