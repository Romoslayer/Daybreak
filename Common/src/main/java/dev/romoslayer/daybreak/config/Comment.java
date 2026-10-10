package dev.romoslayer.daybreak.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The explanation written above a setting in config/daybreak.json. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Comment {
	String value();
}
