package org.yonside.compat.example;

import java.util.ArrayList;
import java.util.List;

import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.api.Pos;
import org.yonside.compat.mte.MachineEvent;

/**
 * The example's behaviour, written once against the Compat Core API. Nothing here imports Minecraft,
 * so the same class file runs on 1.7.10, 1.12.2 and 1.16.5 -- that is the whole point of the framework.
 *
 * <p>Java 21 throughout, like Compat Core itself: the addon plugin downgrades each version's finished jar
 * to Java 8, so the switch over MachineEvent's sealed records below runs on 1.7.10's Java 8 too.
 */
public final class ExampleMachine {

    private final long pos;
    private int redstonePower;
    private long lastTick = -1L;
    private long tick;

    public ExampleMachine(int x, int y, int z) {
        this.pos = Pos.pack(x, y, z);
    }

    public String position() {
        return "(" + Pos.x(pos) + ", " + Pos.y(pos) + ", " + Pos.z(pos) + ")";
    }

    public int redstonePower() {
        return redstonePower;
    }

    public long lastTick() {
        return lastTick;
    }

    /** The whole smoke test: report the world's side, then push a few MachineEvents through. */
    public List<String> report(CompatWorld world) {
        var lines = new ArrayList<String>();
        lines.add("world is " + describeWorld(world));
        lines.add(accept(new MachineEvent.Tick(++tick)));
        lines.add(accept(new MachineEvent.RedstoneChanged(redstonePower, 15)));
        lines.add(accept(new MachineEvent.NeighborChanged(Pos.offset(pos, 0, 1, 0))));
        lines.add("redstone power now " + redstonePower);
        return lines;
    }

    /** Returns the line to log. */
    public String accept(MachineEvent event) {
        switch (event) {
            case MachineEvent.Tick(long worldTime) -> lastTick = worldTime;
            case MachineEvent.RedstoneChanged(int oldPower, int newPower) -> redstonePower = newPower;
            case MachineEvent.NeighborChanged ignored -> { }
        }
        return position() + " " + MachineEvent.describe(event);
    }

    /**
     * Compat Core's mixin injects CompatWorld onto 1.7.10 and 1.12.2's {@code World} and onto 1.16.5's
     * {@code Level}, and hands addons the world already typed as one: no wrapper, no per-version branch.
     */
    public static String describeWorld(CompatWorld world) {
        return world.compat$isClientSide() ? "client" : "server";
    }
}
