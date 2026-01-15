package slimefinder;

public final class CircleOverlap {
    private CircleOverlap() {}

    /**
     * Fraction of the 16x16 blocks in chunk (chunkX,chunkZ) whose block-centers
     * lie within radius R (blocks) of the center (x0,z0) in BLOCK coordinates.
     */
    public static double fractionInCircle(int x0, int z0, int chunkX, int chunkZ, int R) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;

        double cx = x0 + 0.5;
        double cz = z0 + 0.5;
        double r2 = (double)R * (double)R;

        int inside = 0;
        for (int dx = 0; dx < 16; dx++) {
            double bx = baseX + dx + 0.5;
            double dx2 = (bx - cx) * (bx - cx);
            for (int dz = 0; dz < 16; dz++) {
                double bz = baseZ + dz + 0.5;
                double d2 = dx2 + (bz - cz) * (bz - cz);
                if (d2 <= r2) inside++;
            }
        }
        return inside / 256.0;
    }
}