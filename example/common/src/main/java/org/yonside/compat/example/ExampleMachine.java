package org.yonside.compat.example;

import java.util.ArrayList;
import java.util.List;

import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.api.Pos;
import org.yonside.compat.mte.MachineEvent;

/**
 * Every bit of this example's behaviour, written once against the Compat Core API and shared by the
 * 1.7.10, 1.12.2 and 1.16.5 modules. Nothing here imports Minecraft, so it compiles unchanged on all
 * three -- that is the whole point of the framework.
 *
 * <p>Plain Java 8 source on purpose: MachineEvent's records and sealed interface come through
 * JvmDowngrader as ordinary classes and methods, so a consumer on a Java 8 toolchain can still use
 * them, just with {@code instanceof} instead of a pattern-matching switch.
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

    /**
     * The whole smoke test, shared by all three versions: prove the mixin applied, then push a few
     * MachineEvents through. Returns the lines to log so each platform module only has to decide
     * where logging goes and how to get hold of its world object.
     */
    public List<String> report(Object world) {
        List<String> lines = new ArrayList<String>();
        lines.add("world is " + describeWorld(world));
        lines.add(accept(new MachineEvent.Tick(++tick)));
        lines.add(accept(new MachineEvent.RedstoneChanged(redstonePower, 15)));
        lines.add(accept(new MachineEvent.NeighborChanged(Pos.offset(pos, 0, 1, 0))));
        lines.add("redstone power now " + redstonePower);
        return lines;
    }

    /** Returns the line to log, so each platform module only has to decide where logging goes. */
    public String accept(MachineEvent event) {
        if (event instanceof MachineEvent.Tick) {
            lastTick = ((MachineEvent.Tick) event).worldTime();
        } else if (event instanceof MachineEvent.RedstoneChanged) {
            redstonePower = ((MachineEvent.RedstoneChanged) event).newPower();
        }
        return position() + " " + MachineEvent.describe(event);
    }

    /**
     * The cast every version shares. Compat Core's mixin injects CompatWorld onto 1.7.10 and 1.12.2's
     * {@code World} and onto 1.16.5's {@code Level}, so this needs no wrapper object and no
     * per-version branch. Takes Object because the declared type is the one thing that does differ.
     *
     * @throws ClassCastException if Compat Core's mixin did not apply -- which makes this a useful
     *         smoke test that the coremod loaded.
     */
    public static String describeWorld(Object world) {
        return ((CompatWorld) world).compat$isClientSide() ? "client" : "server";
    }
}
