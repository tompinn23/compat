package org.yonside.compat.stub;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class of static methods that implements an API interface for one Minecraft version. The
 * compat plugin links each mod against these at build time: the API type becomes {@link #target()}
 * everywhere in the mod, and each call to an API method {@code m(args)} becomes a call to this class's
 * {@code public static m(target, args)}. The stub classes a mod uses are copied into its jar under its
 * own package, so nothing of Compat is installed at runtime.
 *
 * <p>Only the plugin reads this, from class files.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface StubFor {

    /** The API interface implemented, e.g. {@code CompatWorld.class}. */
    Class<?> api();

    /** The Minecraft type that replaces it, e.g. {@code World.class}. */
    Class<?> target();
}
