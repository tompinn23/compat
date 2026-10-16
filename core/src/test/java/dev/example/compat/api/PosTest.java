package dev.example.compat.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PosTest {

    @Test
    void roundTripsNegativeAndExtremeCoordinates() {
        int[][] cases = {{0, 0, 0}, {-1, -64, -1}, {30_000_000, 2047, -30_000_000}, {-33_554_432, -2048, 33_554_431}};
        for (int[] c : cases) {
            long p = Pos.pack(c[0], c[1], c[2]);
            assertEquals(c[0], Pos.x(p));
            assertEquals(c[1], Pos.y(p));
            assertEquals(c[2], Pos.z(p));
        }
    }
}
