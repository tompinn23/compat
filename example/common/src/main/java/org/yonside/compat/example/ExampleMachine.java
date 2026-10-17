package org.yonside.compat.example;

import java.util.ArrayList;
import java.util.List;

import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.api.Pos;
import org.yonside.compat.mte.MachineEvent;

/**
 * The example's behaviour, written once against Compat's API. Nothing here imports Minecraft; the
 * compat plugin links this class into each version's jar, with Compat's types and calls replaced by
 * that version's.
 *
 * <p>Java 21 throughout: the plugin downgrades each version's finished jar to Java 8, so the switch over
 * MachineEvent's sealed records below runs on 1.7.10's Java 8 too.
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
        lines.add("world is " + describeWorld(world) + ", " + world.totalTime() + " ticks old");
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
     * After linking, {@code world} is 1.7.10 and 1.12.2's {@code World} or 1.16.5's {@code Level} itself,
     * and {@code isClientSide()} a static call to that version's stub: no wrapper, no per-version branch.
     */
    public static String describeWorld(CompatWorld world) {
        return world.isClientSide() ? "client" : "server";
    }
}
