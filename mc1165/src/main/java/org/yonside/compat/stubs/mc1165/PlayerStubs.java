package org.yonside.compat.stubs.mc1165;

import net.minecraft.Util;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.player.Player;
import org.yonside.compat.api.CompatPlayer;
import org.yonside.compat.stub.StubFor;

/** {@link CompatPlayer} on 1.16.5. */
@StubFor(CompatPlayer.class)
public record PlayerStubs(Player player) implements CompatPlayer {

    @Override
    public void sendMessage(String text) {
        player.sendMessage(new TextComponent(text), Util.NIL_UUID);
    }
}
