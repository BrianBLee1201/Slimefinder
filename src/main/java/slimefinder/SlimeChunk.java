package slimefinder;

/**
 * Fast, allocation-free slime chunk test that matches Minecraft Java's Random-based formula.
 *
 * This implements the same result as:
 *   Random rnd = new Random(seedExpr);
 *   return rnd.nextInt(10) == 0;
 *
 * where seedExpr is:
 *   worldSeed
 *   + (int)(x*x*0x4c1906)
 *   + (int)(x*0x5ac0db)
 *   + (int)(z*z)*0x4307a7L
 *   + (int)(z*0x5f24f)
 *   ^ 0x3ad8025fL
 */
public final class SlimeChunk {

    private SlimeChunk() {}

    // java.util.Random constants
    private static final long MULT = 0x5DEECE66DL;
    private static final long ADD  = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    /**
     * Returns true if (chunkX, chunkZ) is a slime chunk for the given worldSeed.
     * chunkX/chunkZ are chunk coordinates, NOT block coordinates.
     */
    public static boolean isSlimeChunk(long worldSeed, int chunkX, int chunkZ) {
        // IMPORTANT: replicate Java int overflow behavior exactly.
        // Use long intermediates, then cast to int where the original does.
        long seed = worldSeed;
        seed += (int)((long)chunkX * (long)chunkX * 0x4c1906L);
        seed += (int)((long)chunkX * 0x5ac0dbL);
        seed += (int)((long)chunkZ * (long)chunkZ) * 0x4307a7L;
        seed += (int)((long)chunkZ * 0x5f24fL);
        seed ^= 0x3ad8025fL;

        long rnd = initialScramble(seed);
        // nextInt(10)
        int v = nextIntBounded10(rnd);
        return v == 0;
    }

    /**
     * Matches new Random(seed) initial scrambling:
     *   this.seed = (seed ^ MULT) & MASK
     */
    private static long initialScramble(long seed) {
        return (seed ^ MULT) & MASK;
    }

    /**
     * Produce next(bits) from java.util.Random given current 48-bit state.
     * Returns (newState<<something) but here we return pair via long packing.
     */
    private static long nextSeed(long state) {
        return (state * MULT + ADD) & MASK;
    }

    /**
     * Equivalent to Random.next(31) for the given state.
     * Returns packed long: (newState<<32) | (value & 0xffffffff)
     */
    private static long next31(long state) {
        long ns = nextSeed(state);
        int val = (int)(ns >>> (48 - 31));
        return (ns << 32) | (val & 0xffffffffL);
    }

    /**
     * Specialized exact nextInt(10) implementation.
     * Uses the same rejection loop as java.util.Random.nextInt(bound).
     */
    private static int nextIntBounded10(long state) {
        final int bound = 10;
        long st = state;
        while (true) {
            long packed = next31(st);
            st = packed >>> 32;
            int bits = (int) packed;

            int val = bits % bound;
            // Rejection condition from Random.nextInt:
            // if (bits - val + (bound - 1) < 0) continue;
            if (bits - val + (bound - 1) >= 0) {
                return val;
            }
        }
    }
}