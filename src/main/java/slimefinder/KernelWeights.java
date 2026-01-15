package slimefinder;

/**
 * Precomputed 17x17 kernel for cr=8 (R=128 blocks).
 * weights[dz+8][dx+8] gives the fraction of chunk (dx,dz) covered by the circle.
 */
public final class KernelWeights {
    public static final int CR = 8;
    public static final int SIZE = 2 * CR + 1;

    private final double[] w; // row-major [dz][dx]

    private KernelWeights(double[] w) {
        this.w = w;
    }

    public double get(int dx, int dz) {
        // dx,dz in [-8,8]
        return w[(dz + CR) * SIZE + (dx + CR)];
    }

    public static KernelWeights precompute(int radiusBlocks) {
        double[] w = new double[SIZE * SIZE];

        // center at (0,0) block coords
        int x0 = 0, z0 = 0;
        // candidate center chunk coords are (0,0)
        int cX = 0, cZ = 0;

        for (int dz = -CR; dz <= CR; dz++) {
            for (int dx = -CR; dx <= CR; dx++) {
                int kx = cX + dx;
                int kz = cZ + dz;
                double cov = CircleOverlap.fractionInCircle(x0, z0, kx, kz, radiusBlocks);
                w[(dz + CR) * SIZE + (dx + CR)] = cov;
            }
        }
        return new KernelWeights(w);
    }
        /** True if the chunk square at offset (dx,dz) has any intersection with the 128-block circle. */
    public boolean intersects(int dx, int dz) {
        return get(dx, dz) > 0.0;
    }

    /** True if the chunk square at offset (dx,dz) is fully covered by the 128-block circle. */
    public boolean isFull(int dx, int dz) {
        return get(dx, dz) >= 1.0 - 1e-12;
    }
}