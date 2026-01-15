package slimefinder;

import java.util.ArrayList;
import java.util.List;

public final class ChunkClassifier {
    private ChunkClassifier() {}

    public enum Category {
        FULL_COVER_FULL_BIOME,
        FULL_COVER_PARTIAL_BIOME,
        PARTIAL_COVER_FULL_BIOME,
        PARTIAL_COVER_PARTIAL_BIOME
    }

    public static final class ChunkCoord {
        public final int cx;
        public final int cz;
        public ChunkCoord(int cx, int cz) { this.cx = cx; this.cz = cz; }
        @Override public String toString() { return "(" + cx + "," + cz + ")"; }
    }

    public static final class Breakdown {
        public final List<ChunkCoord> fullCoverFullBiome = new ArrayList<>();
        public final List<ChunkCoord> fullCoverPartBiome = new ArrayList<>();
        public final List<ChunkCoord> partCoverFullBiome = new ArrayList<>();
        public final List<ChunkCoord> partCoverPartBiome = new ArrayList<>();
    }

    public static double biomeOkFrac(BiomeProvider biome, int farmY, int chunkX, int chunkZ, int samples) {
        if (biome == null) return 1.0;

        int ok = 0;
        int total = samples * samples;

        for (int i = 0; i < samples; i++) {
            for (int k = 0; k < samples; k++) {
                int x = 16 * chunkX + (int)((i + 0.5) * (16.0 / samples));
                int z = 16 * chunkZ + (int)((k + 0.5) * (16.0 / samples));
                if (!biome.isBlocked(x, farmY, z)) ok++;
            }
        }
        return ok / (double)total;
    }

    public static Breakdown classifyForCenter(
            long seed,
            int x0, int z0,
            int cr,           // chunk radius neighborhood
            int farmY,
            int samples,
            BiomeProvider biome
    ) {
        int cX = Math.floorDiv(x0, 16);
        int cZ = Math.floorDiv(z0, 16);

        Breakdown b = new Breakdown();

        for (int kz = cZ - cr; kz <= cZ + cr; kz++) {
            for (int kx = cX - cr; kx <= cX + cr; kx++) {
                if (!SlimeChunk.isSlimeChunk(seed, kx, kz)) continue;

                double cov = CircleOverlap.fractionInCircle(x0, z0, kx, kz, 128);
                if (cov <= 0.0) continue;

                double okf = biomeOkFrac(biome, farmY, kx, kz, samples);
                if (okf <= 0.0) continue; // fully blocked

                boolean fullCov = cov >= (1.0 - 1e-12);
                boolean fullBiome = okf >= (1.0 - 1e-12);

                ChunkCoord cc = new ChunkCoord(kx, kz);

                if (fullCov && fullBiome) b.fullCoverFullBiome.add(cc);
                else if (fullCov)         b.fullCoverPartBiome.add(cc);
                else if (fullBiome)       b.partCoverFullBiome.add(cc);
                else                      b.partCoverPartBiome.add(cc);
            }
        }
        return b;
    }
}