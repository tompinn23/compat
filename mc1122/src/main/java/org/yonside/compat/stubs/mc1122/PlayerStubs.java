package org.yonside.compat.stubs.mc1122;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import org.yonside.compat.api.CompatPlayer;
import org.yonside.compat.stub.StubFor;

/** {@link CompatPlayer} on 1.12.2. */
@StubFor(CompatPlayer.class)
public record PlayerStubs(EntityPlayer player) implements CompatPlayer {

    @Override
    public void sendMessage(String text) {
        player.sendMessage(new TextComponentString(text));
    }
}
