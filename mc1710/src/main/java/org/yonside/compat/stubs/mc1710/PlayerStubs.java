package org.yonside.compat.stubs.mc1710;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import org.yonside.compat.api.CompatPlayer;
import org.yonside.compat.stub.StubFor;

/** {@link CompatPlayer} on 1.7.10. */
@StubFor(CompatPlayer.class)
public record PlayerStubs(EntityPlayer player) implements CompatPlayer {

    @Override
    public void sendMessage(String text) {
        player.addChatMessage(new ChatComponentText(text));
    }
}
