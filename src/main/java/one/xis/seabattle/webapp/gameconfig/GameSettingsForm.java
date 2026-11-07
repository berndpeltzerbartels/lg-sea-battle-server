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
        @Mandatory @Range(min = 0, max = 5) @LabelKey("gameConfig.aimErrorAngle") Double aimErrorAngle,
        @Mandatory @Range(min = 0, minInclusive = false) @LabelKey("gameConfig.subAimResponse") Double subAimResponse,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subAimTurnDamping") Double subAimTurnDamping,
        @Mandatory @Range(min = 0, max = 180) @LabelKey("gameConfig.subAimMinCorrectionAngle") Double subAimMinCorrectionAngle,
        @Mandatory @Range(min = 0, minInclusive = false, max = 180) @LabelKey("gameConfig.subFireArc") Double subFireArc,
        @Mandatory @Range(min = 0, max = 180) @LabelKey("gameConfig.subCloseFireArc") Double subCloseFireArc,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subFireMinRange") Double subFireMinRange,
        @Mandatory @Range(min = 0, minInclusive = false) @LabelKey("gameConfig.subFireMaxRange") Double subFireMaxRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subCloseFireRange") Double subCloseFireRange,
        @Mandatory @Range(min = 0, minInclusive = false) @LabelKey("gameConfig.subTorpedoCooldownMin") Double subTorpedoCooldownMin,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subTorpedoCooldownVariation") Double subTorpedoCooldownVariation,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subApproachSlowRange") Double subApproachSlowRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subCloseManeuverRange") Double subCloseManeuverRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subRadarInterceptRange") Double subRadarInterceptRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subRamRange") Double subRamRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subHumanTargetPriorityClearance") Double subHumanTargetPriorityClearance,
        @Mandatory @Range(min = 1) @UseFormatter(WholeNumberFormatter.class) @LabelKey("gameConfig.subTargetPreferredAttackers") Integer subTargetPreferredAttackers,
        @Mandatory @Range(min = 1) @LabelKey("gameConfig.subTargetAlternativeDistanceFactor") Double subTargetAlternativeDistanceFactor,
        @Mandatory @Range(min = 0, max = 5) @LabelKey("gameConfig.subAimErrorAngle") Double subAimErrorAngle,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subDiveRange") Double subDiveRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subDeepRange") Double subDeepRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subSafeRange") Double subSafeRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subShotAscentRange") Double subShotAscentRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subSurfaceRange") Double subSurfaceRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subAttackMinRange") Double subAttackMinRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subAttackMaxRange") Double subAttackMaxRange,
        @Mandatory @Range(min = 0, max = 180) @LabelKey("gameConfig.subAimTolerance") Double subAimTolerance,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.subAircraftMemorySeconds") Double subAircraftMemorySeconds,
        @Mandatory @Range(min = 0, minInclusive = false) @LabelKey("gameConfig.subUnderwaterContactRange") Double subUnderwaterContactRange,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.lightTorpedoBoats") Integer lightTorpedoBoats,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.darkTorpedoBoats") Integer darkTorpedoBoats,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.lightSubmarines") Integer lightSubmarines,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.darkSubmarines") Integer darkSubmarines,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.lightPlanes") Integer lightPlanes,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.darkPlanes") Integer darkPlanes) {
    static GameSettingsForm from(String id,String name,String setup,String mode,List<GameConfigParameter> values) {
        var config=GameConfigForm.fromParameters(values);
        var sub=GameConfigForm.fromParameters(values, GameConfigParameterGroup.SUBMARINE_BOT);
        var tactics=SubmarineTacticsForm.fromParameters(values);
        var fleet=FleetSizeForm.fromParameters(values);
        return new GameSettingsForm(id,name,setup,mode,
                config.aimResponse(), config.aimTurnDamping(), config.aimMinCorrectionAngle(), config.fireArc(), config.closeFireArc(), config.fireMinRange(), config.fireMaxRange(), config.closeFireRange(), config.torpedoCooldownMin(), config.torpedoCooldownVariation(), config.approachSlowRange(), config.closeManeuverRange(), config.radarInterceptRange(), config.ramRange(), config.humanTargetPriorityClearance(), config.targetPreferredAttackers(), config.targetAlternativeDistanceFactor(), config.aimErrorAngle(),
                sub.aimResponse(), sub.aimTurnDamping(), sub.aimMinCorrectionAngle(), sub.fireArc(), sub.closeFireArc(), sub.fireMinRange(), sub.fireMaxRange(), sub.closeFireRange(), sub.torpedoCooldownMin(), sub.torpedoCooldownVariation(), sub.approachSlowRange(), sub.closeManeuverRange(), sub.radarInterceptRange(), sub.ramRange(), sub.humanTargetPriorityClearance(), sub.targetPreferredAttackers(), sub.targetAlternativeDistanceFactor(), sub.aimErrorAngle(),
                tactics.diveRange(), tactics.deepRange(), tactics.safeRange(), tactics.shotAscentRange(), tactics.surfaceRange(), tactics.attackMinRange(), tactics.attackMaxRange(), tactics.aimTolerance(), tactics.aircraftMemorySeconds(), tactics.underwaterContactRange(),
                fleet.lightTorpedoBoats(), fleet.darkTorpedoBoats(), fleet.lightSubmarines(), fleet.darkSubmarines(), fleet.lightPlanes(), fleet.darkPlanes());
    }
    GameConfigForm config() { return new GameConfigForm(aimResponse, aimTurnDamping, aimMinCorrectionAngle, fireArc, closeFireArc, fireMinRange, fireMaxRange, closeFireRange, torpedoCooldownMin, torpedoCooldownVariation, approachSlowRange, closeManeuverRange, radarInterceptRange, ramRange, humanTargetPriorityClearance, targetPreferredAttackers, targetAlternativeDistanceFactor, aimErrorAngle); }
    GameConfigForm submarineCombat() { return new GameConfigForm(subAimResponse, subAimTurnDamping, subAimMinCorrectionAngle, subFireArc, subCloseFireArc, subFireMinRange, subFireMaxRange, subCloseFireRange, subTorpedoCooldownMin, subTorpedoCooldownVariation, subApproachSlowRange, subCloseManeuverRange, subRadarInterceptRange, subRamRange, subHumanTargetPriorityClearance, subTargetPreferredAttackers, subTargetAlternativeDistanceFactor, subAimErrorAngle); }
    SubmarineTacticsForm submarineTactics() { return new SubmarineTacticsForm(subDiveRange, subDeepRange, subSafeRange, subShotAscentRange, subSurfaceRange, subAttackMinRange, subAttackMaxRange, subAimTolerance, subAircraftMemorySeconds, subUnderwaterContactRange); }
    FleetSizeForm fleetSize() { return new FleetSizeForm(lightTorpedoBoats, darkTorpedoBoats, lightSubmarines, darkSubmarines, lightPlanes, darkPlanes); }
    List<GameConfigParameter> parameters(List<GameConfigParameter> current) {
        var values = config().applyTo(current);
        values = submarineCombat().applyTo(values, GameConfigParameterGroup.SUBMARINE_BOT);
        return fleetSize().applyTo(submarineTactics().applyTo(values));
    }
}
