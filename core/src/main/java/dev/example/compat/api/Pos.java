package dev.example.compat.api;

/**
 * Block positions as a packed long, so the API never allocates and works on 1.7.10 (which has no BlockPos).
 * Layout: x 26 bits | z 26 bits | y 12 bits (signed), the same ranges vanilla supports.
 */
public final class Pos {

    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;
    private static final int Z_SHIFT = Y_BITS;
    private static final int X_SHIFT = Y_BITS + XZ_BITS;

    private Pos() {}

    public static long pack(int x, int y, int z) {
        return ((x & XZ_MASK) << X_SHIFT) | ((z & XZ_MASK) << Z_SHIFT) | (y & Y_MASK);
    }

    public static int x(long pos) { return (int) (pos << (64 - X_SHIFT - XZ_BITS) >> (64 - XZ_BITS)); }
    public static int y(long pos) { return (int) (pos << (64 - Y_BITS) >> (64 - Y_BITS)); }
    public static int z(long pos) { return (int) (pos << (64 - Z_SHIFT - XZ_BITS) >> (64 - XZ_BITS)); }

    public static long offset(long pos, int dx, int dy, int dz) {
        return pack(x(pos) + dx, y(pos) + dy, z(pos) + dz);
    }
}
