package org.yonside.compat.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a mod's {@link CompatAddon} implementation. Read from the class files by the
 * {@code org.yonside.compat.addon} Gradle plugin, never at runtime; the mod's id, name and other
 * metadata live in the build script's {@code compatMod {}} block.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface CompatMod {
}
