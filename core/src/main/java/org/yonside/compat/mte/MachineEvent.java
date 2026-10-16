package org.yonside.compat.mte;

/**
 * What a host tile entity tells its MetaTileEntity. Sealed records plus pattern-matching switches are
 * Java 21 features: they compile here and JvmDowngrader rewrites them into Java 8 bytecode.
 */
public sealed interface MachineEvent {

    record Tick(long worldTime) implements MachineEvent {}

    record NeighborChanged(long pos) implements MachineEvent {}

    record RedstoneChanged(int oldPower, int newPower) implements MachineEvent {}

    static String describe(MachineEvent event) {
        return switch (event) {
            case Tick t -> "tick @" + t.worldTime();
            case NeighborChanged n -> "neighbor changed";
            case RedstoneChanged(int oldPower, int newPower) when newPower > oldPower -> "redstone rising";
            case RedstoneChanged r -> "redstone falling or steady";
        };
    }
}
