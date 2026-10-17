package org.yonside.compat.gradle;

import org.gradle.api.provider.Property;

/** {@code compat { mixins { } }}: the names default from the mod id, see {@link CompatExtension#mixins()}. */
public abstract class MixinOptions {

    /** The hand-written Mixin config in this project's resources. Registered with Forge on 1.16.5. */
    public abstract Property<String> getConfig();

    public abstract Property<String> getRefmap();

    /** 1.7.10 / 1.12.2: the Mixin library's dev artifact. Unused where Forge ships Mixin. */
    public abstract Property<String> getProvider();
}
