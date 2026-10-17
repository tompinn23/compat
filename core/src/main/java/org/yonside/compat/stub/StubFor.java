package org.yonside.compat.stub;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one Minecraft version's implementation of an API interface: a record wrapping the version's
 * type and implementing the interface, so the compiler checks every API method is there with the right
 * signature.
 *
 * <pre>
 * &#64;StubFor(CompatWorld.class)
 * public record WorldStubs(World world) implements CompatWorld {
 *     &#64;Override public boolean isClientSide() { return world.isRemote; }
 * }
 * </pre>
 *
 * The wrapper never exists at runtime. The compat plugin unwraps it when linking a mod: each method
 * becomes a static one taking the wrapped type first ({@code isClientSide(World)}), the API type becomes
 * the wrapped type throughout the mod, and each API call becomes a call to the static method. The stubs a
 * mod uses are copied into its jar under its own package.
 *
 * <p>A wrapper has exactly one component. Its methods may use {@code this} to read that component, call
 * each other, or pass it where the API type is expected; they may not construct a wrapper or use the
 * wrapper's own type. Only the plugin reads this annotation, from class files.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface StubFor {

    /** The API interface implemented, e.g. {@code CompatWorld.class}. */
    Class<?> value();
}
