package one.xis.seabattle.webapp.gameconfig;

import one.xis.validation.Validate;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Validate(validatorClass = GameConfigFormValidator.class, globalMessageKey = "gameConfig.invalid")
@interface ValidGameConfig { }
