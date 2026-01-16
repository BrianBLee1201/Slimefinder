package slimefinder;
import java.util.Collections;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public final class SlimeFinder {

    private static final int RADIUS_BLOCKS = 128;
    private static final int CR = 8;              // chunk radius for R=128
    private static final int KSIZE = 17;          // 2*CR+1

    public static final class Args {
        long seed;
        int mChunks;
        int innerChunks = 0; // inner square radius in chunks to skip (ring search). 0 = full square
        double threshold = 6.0;
        int farmY = -64;
        int samples = 4;
        int topk = 50;
        boolean biomes = false;
        boolean biomeDebug = false;
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        String cubiomesLib = "./libcubiomeswrap.dylib";
        int cubiomesMc = 125;

        // performance knobs
        int tileRows = 512;      // z-tiling height in centers (chunks). 512 is a good default.
        int tileCols = 4096;    // x-tiling width in centers (chunks). Keeps stripe arrays small.
        int kzBlock = 64;        // each worker gets kz blocks of this height in contributing-chunk space.

        // Fixed output paths
        final String beforePath = "before_validation.csv";
        final String resultsPath = "results.csv";
    }

    private static void printUsage() {
    System.out.println("""
        Usage:
          ./gradlew run --args="--seed <long> --m-chunks <int> [options]"

        Required:
          --seed <long>          World seed (64-bit)
          --m-chunks <int>       Search square of centers in chunk coords: [-m,m] x [-m,m]

        Common options:
          --threshold <double>   Minimum score to keep (default 6.0)
          --threads <int>        Worker threads (default = CPU count)
          --topk <int>           Keep top K in before_validation.csv (default 50)
          --inner-chunks <int>  Skip centers inside [-inner,inner]^2 (default 0; ring search when >0)

        Biome validation (optional):
          --biomes               Validate Deep Dark + Mushroom Fields after fast search
          --farm-y <int>         Y level for biome checks (default -64)
          --samples <int>        Samples per axis per chunk (default 4)
          --cubiomes-lib <path>  Path to libcubiomeswrap.dylib (default ./libcubiomeswrap.dylib)
          --cubiomes-mc <int>    Cubiomes MC version id (default 125)

        Performance tuning (optional):
          --tile-rows <int>      (default 512)
          --tile-cols <int>      (default 4096)
          --kz-block <int>       (default 64)

        Examples:
          ./gradlew run --args="--seed 11868470311385 --m-chunks 10000 --threshold 50 --threads 8"
          ./gradlew run --args="--seed 11868470311385 --m-chunks 10000 --threshold 50 --threads 8 --biomes --farm-y -64 --samples 4 --cubiomes-lib ./libcubiomeswrap.dylib --cubiomes-mc 125"

        Tip:
          ./gradlew run --args="--help"
        """);
    }

    private static Args parseArgs(String[] argv) {
        Args a = new Args();
        for (int i = 0; i < argv.length; i++) {
            String k = argv[i];
            String v = (i + 1 < argv.length) ? argv[i + 1] : null;

            switch (k) {
                case "--seed" -> { a.seed = Long.parseLong(require(v, k)); i++; }
                case "--m-chunks" -> { a.mChunks = Integer.parseInt(require(v, k)); i++; }
                case "--inner-chunks" -> { a.innerChunks = Integer.parseInt(require(v, k)); i++; }
                case "--threshold" -> { a.threshold = Double.parseDouble(require(v, k)); i++; }
                case "--farm-y" -> { a.farmY = Integer.parseInt(require(v, k)); i++; }
                case "--samples" -> { a.samples = Integer.parseInt(require(v, k)); i++; }
                case "--topk" -> { a.topk = Integer.parseInt(require(v, k)); i++; }
                case "--biomes" -> { a.biomes = true; }
                case "--biome-debug" -> { a.biomeDebug = true; }
                case "--threads" -> { a.threads = Integer.parseInt(require(v, k)); i++; }
                case "--cubiomes-lib" -> { a.cubiomesLib = require(v, k); i++; }
                case "--cubiomes-mc" -> { a.cubiomesMc = Integer.parseInt(require(v, k)); i++; }

                // optional tuning
                case "--tile-rows" -> { a.tileRows = Integer.parseInt(require(v, k)); i++; }
                case "--tile-cols" -> { a.tileCols = Integer.parseInt(require(v, k)); i++; }
                case "--kz-block" -> { a.kzBlock = Integer.parseInt(require(v, k)); i++; }

                case "--help" -> {
                    System.out.println("""
                        SlimeFinder (Java, fast scatter)
                          --seed <long>
                          --m-chunks <int>
                          --threshold <double>
                          --farm-y <int>
                          --samples <int>
                          --topk <int>
                          --threads <int>
                          --inner-chunks <int>          (default 0; >0 searches only the outer ring)
                          --biomes                    (apply biome validation after fast search)
                          --biome-debug
                          --cubiomes-lib <path>
                          --cubiomes-mc <int>

                        Perf tuning:
                          --tile-rows <int>   (default 512)
                          --tile-cols <int>   (default 4096)
                          --kz-block <int>    (default 64)

                        Notes:
                          - Radius is fixed at 128 blocks (circle).
                          - Candidate centers are chunk-aligned (x=16*cx, z=16*cz).
                          - Default search is naive integer scoring (counts slime chunks intersecting circle).
                          - The program always writes all top-k candidates to before_validation.csv, then (if --biomes) validates and writes results.csv.
                        """);
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException("Unknown arg: " + k);
            }
        }
        if (a.mChunks == 0 && !contains(argv, "--m-chunks")) {
            throw new IllegalArgumentException("Missing required --m-chunks");
        }
        if (!contains(argv, "--seed")) {
            throw new IllegalArgumentException("Missing required --seed");
        }
        if (a.innerChunks < 0) {
            throw new IllegalArgumentException("--inner-chunks must be >= 0");
        }
        if (a.innerChunks > a.mChunks) {
            throw new IllegalArgumentException("--inner-chunks must be <= --m-chunks");
        }
        return a;
    }

    private static boolean contains(String[] argv, String key) {
        for (String s : argv) if (s.equals(key)) return true;
        return false;
    }

    private static String require(String v, String k) {
        if (v == null || v.startsWith("--")) throw new IllegalArgumentException("Missing value for " + k);
        return v;
    }

    private static final class CsvRow {
        final int x;
        final int z;
        final Double score; // may be null if not provided
        CsvRow(int x, int z, Double score) {
            this.x = x;
            this.z = z;
            this.score = score;
        }
    }

    private static List<CsvRow> readCsvXZ(String path) throws Exception {
        List<CsvRow> rows = new ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(path))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (first) { first = false; continue; }
                String[] parts = line.split(",");
                if (parts.length < 2) continue;
                int x = Integer.parseInt(parts[0].trim());
                int z = Integer.parseInt(parts[1].trim());
                Double score = null;
                if (parts.length >= 3) {
                    String s = parts[2].trim();
                    if (!s.isEmpty()) {
                        try { score = Double.parseDouble(s); } catch (NumberFormatException ignored) {}
                    }
                }
                rows.add(new CsvRow(x, z, score));
            }
        }
        return rows;
    }

        private record Tile(int cz0, int cz1, int cx0, int cx1) {}
    private record TileResult(Tile tile, TopK top) {}

    private static List<Tile> buildTiles(int m, int inner, int tileRows, int tileCols) {
        List<Tile> tiles = new ArrayList<>();

        for (int cz0 = -m; cz0 <= m; cz0 += tileRows) {
            int cz1 = Math.min(m, cz0 + tileRows - 1);

            for (int cx0 = -m; cx0 <= m; cx0 += tileCols) {
                int cx1 = Math.min(m, cx0 + tileCols - 1);

                // Ring search: skip tiles fully contained in the inner square.
                if (inner > 0
                        && cx0 >= -inner && cx1 <= inner
                        && cz0 >= -inner && cz1 <= inner) {
                    continue;
                }

                tiles.add(new Tile(cz0, cz1, cx0, cx1));
            }
        }

        return tiles;
    }

    private static TopK processTile(Tile t, Args args, KernelWeights kernel) {
        final int m = args.mChunks;
        final int inner = args.innerChunks;

        final int cz0 = t.cz0();
        final int cz1 = t.cz1();
        final int cx0 = t.cx0();
        final int cx1 = t.cx1();

        final int tileH = cz1 - cz0 + 1;
        final int tileW = cx1 - cx0 + 1;
        final int stripeSize = tileW * tileH;

        // Local stripe counts for this tile only.
        short[] stripe = new short[stripeSize];

        // Contributing slime chunks range.
        final int kzMin = cz0 - CR;
        final int kzMax = cz1 + CR;

        int kxMin = cx0 - CR;
        int kxMax = cx1 + CR;

        // Clamp to global contributing range.
        int globalKxMin = -m - CR;
        int globalKxMax =  m + CR;
        if (kxMin < globalKxMin) kxMin = globalKxMin;
        if (kxMax > globalKxMax) kxMax = globalKxMax;

        for (int kz = kzMin; kz <= kzMax; kz++) {
            for (int kx = kxMin; kx <= kxMax; kx++) {
                if (!SlimeChunk.isSlimeChunk(args.seed, kx, kz)) continue;

                // Scatter +1 to every center whose 128-block circle intersects this chunk.
                for (int dz = -CR; dz <= CR; dz++) {
                    int cz = kz - dz;
                    if (cz < cz0 || cz > cz1) continue;

                    int rowBase = (cz - cz0) * tileW;
                    for (int dx = -CR; dx <= CR; dx++) {
                        int cx = kx - dx;
                        if (cx < cx0 || cx > cx1) continue;
                        if (!kernel.intersects(dx, dz)) continue;

                        int idx = rowBase + (cx - cx0);
                        stripe[idx] = (short)(stripe[idx] + 1);
                    }
                }
            }
        }

        TopK localTop = new TopK(args.topk);
        final int thrInt = (int)Math.ceil(args.threshold);

        for (int r = 0; r < tileH; r++) {
            int cz = cz0 + r;
            int base = r * tileW;
            for (int c = 0; c < tileW; c++) {
                int s = stripe[base + c] & 0xFFFF;
                if (s < thrInt) continue;

                int cx = cx0 + c;

                // Ring search: skip centers inside the inner square.
                if (inner > 0 && Math.abs(cx) <= inner && Math.abs(cz) <= inner) {
                    continue;
                }

                int x0 = 16 * cx;
                int z0 = 16 * cz;
                localTop.offer(x0, z0, (double)s);
            }
        }

        return localTop;
    }

    public static void main(String[] argv) throws Exception {
        Args args;
        try {
            // If user runs ./gradlew run with no args, show usage instead of a stack trace.
            if (argv == null || argv.length == 0) {
                printUsage();
                return;
            }
            args = parseArgs(argv);
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println();
            printUsage();
            // Exit non-zero so Gradle still marks task as failed, but without a scary stack trace.
            System.exit(1);
            return;
        }

        int m = args.mChunks;
        int inner = args.innerChunks;
        final int innerFinal = inner;
        long outerCount = (long)(2 * m + 1) * (2L * m + 1);
        long innerCount = (inner > 0) ? (long)(2 * inner + 1) * (2L * inner + 1) : 0L;
        long candidates = outerCount - innerCount;

        System.out.println("Seed=" + args.seed);
        if (inner > 0) {
            System.out.println("Candidates: ring in chunks outer=[-" + m + "," + m + "] minus inner=[-" + inner + "," + inner + "] => " + candidates + " candidates");
        } else {
            System.out.println("Candidates: chunks in [-" + m + ", " + m + "] => " + candidates + " candidates");
        }
        System.out.println("Window: circle R=128 blocks => chunk radius cr=" + CR + " (kernel 17x17)");
        System.out.println("Biome: " + (args.biomes ? "ON" : "OFF") + " at y=" + args.farmY + ", samples=" + args.samples + "x" + args.samples);
        System.out.println("Threshold: " + args.threshold);
        System.out.println("Threads: " + args.threads);

        if (args.biomes) {
            System.out.println("[INFO] Fast search ignores biomes; validation happens after writing before_validation.csv.");
        }

        // Phase 1 (fast search) does NOT use biomes.
        BiomeProvider biome = new NoBiomeProvider();
        AutoCloseable biomeCloser = null;
        BiomeOkFracGrid okGrid = null;

        // If we are in verification mode, we will load cubiomes later.

        // --- Precompute kernel weights once ---
        KernelWeights kernel = KernelWeights.precompute(RADIUS_BLOCKS);

        // --- No longer support --verify-biomes or --in/--out; always run fast search, write before_validation.csv, then validate if requested ---

        // fixed thread pool (lower overhead than per-row fork/join futures here)
        ExecutorService exec = Executors.newFixedThreadPool(args.threads);

        TopK top = new TopK(args.topk);

        // --- Fast search (tile-parallel). One task per tile; much lower overhead than kzBlock futures. ---

        final int tileRows = Math.max(1, args.tileRows);
        final int tileCols = Math.max(1, args.tileCols);

        // Build tiles for full square or ring (skipping tiles fully inside inner square).
        List<Tile> tiles = buildTiles(m, innerFinal, tileRows, tileCols);
        // Shuffle to improve load-balance (tiles can vary slightly in cost).
        Collections.shuffle(tiles);

        // Create a single bounded Args instance for this search so we don't allocate per tile.
        final Args bounded = args;

        CompletionService<TileResult> cs = new ExecutorCompletionService<>(exec);
        for (Tile t : tiles) {
            cs.submit(() -> new TileResult(t, processTile(t, bounded, kernel)));
        }

        for (int i = 0; i < tiles.size(); i++) {
            Future<TileResult> f = cs.take();
            TileResult tr = f.get();

            // Merge local topK into global topK.
            for (TopK.Item it : tr.top().toSortedListDesc()) {
                top.offer(it.x, it.z, it.score);
            }

            Tile tt = tr.tile();
            System.out.println("Processed tile: z[" + tt.cz0() + "," + tt.cz1() + "] x[" + tt.cx0() + "," + tt.cx1() + "]");
        }

        exec.shutdown();

        // Write before_validation.csv (TopK only)
        List<TopK.Item> out = top.toSortedListDesc();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(args.beforePath))) {
            bw.write("x,z,score\n");
            for (TopK.Item it : out) {
                bw.write(it.x + "," + it.z + "," + it.score + "\n");
            }
        }
        System.out.println("Wrote " + args.beforePath + " (" + out.size() + " rows)");

        if (!args.biomes) {
            // If not validating biomes, just copy before_validation.csv to results.csv
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(args.resultsPath))) {
                bw.write("x,z,score\n");
                for (TopK.Item it : out) {
                    bw.write(it.x + "," + it.z + "," + it.score + "\n");
                }
            }
            System.out.println("Skipped biome validation (--biomes not set); copied before_validation.csv to results.csv");
        } else {
            // Biome validation: read before_validation.csv, validate, write results.csv
            List<CsvRow> rows = readCsvXZ(args.beforePath);
            if (rows.isEmpty()) {
                System.out.println("[WARN] No rows found in " + args.beforePath);
            } else {
                CubiomesBiomeProvider cb = null;
                try {
                    if (args.biomeDebug) {
                        System.out.println("[biome_debug] loading cubiomes biome backend for validation...");
                        System.out.println("[biome_debug] lib=" + args.cubiomesLib + " mc=" + args.cubiomesMc);
                    }
                    cb = new CubiomesBiomeProvider(args.seed, args.cubiomesMc, args.cubiomesLib);
                    biome = cb;
                    biomeCloser = cb;
                } catch (Throwable t) {
                    System.out.println("[ERROR] Biome validation requires cubiomes backend. Failed to load.");
                    if (args.biomeDebug) t.printStackTrace(System.out);
                    if (biomeCloser != null) { try { biomeCloser.close(); } catch (Exception ignored) {} }
                    return;
                }

                final int R2 = RADIUS_BLOCKS * RADIUS_BLOCKS;
                final int samples = Math.max(1, args.samples);
                final int thrInt = (int)Math.ceil(args.threshold);
                List<TopK.Item> kept = new ArrayList<>();

                for (CsvRow row : rows) {
                    int x0 = row.x;
                    int z0 = row.z;
                    int cX = Math.floorDiv(x0, 16);
                    int cZ = Math.floorDiv(z0, 16);

                    int count = 0;
                    int blocked = 0;

                    for (int dz = -CR; dz <= CR; dz++) {
                        for (int dx = -CR; dx <= CR; dx++) {
                            if (!kernel.intersects(dx, dz)) continue;

                            int kx = cX + dx;
                            int kz = cZ + dz;
                            if (!SlimeChunk.isSlimeChunk(args.seed, kx, kz)) continue;
                            count++;

                            // subtract 1 if the circle-covered portion of this chunk is fully blocked biome
                            if (isCirclePortionFullyBlocked(biome, x0, z0, kx, kz, dx, dz, kernel, args.farmY, samples, R2)) {
                                blocked++;
                            }
                        }
                    }

                    int updated = count - blocked;
                    if (updated >= thrInt) {
                        kept.add(new TopK.Item(x0, z0, (double)updated));
                    }
                }

                // Sort kept descending by score and truncate to args.topk
                kept.sort((a, b) -> Double.compare(b.score, a.score));
                if (args.topk > 0 && kept.size() > args.topk) {
                    kept = new ArrayList<>(kept.subList(0, args.topk));
                }

                try (BufferedWriter bw = new BufferedWriter(new FileWriter(args.resultsPath))) {
                    bw.write("x,z,score\n");
                    for (TopK.Item it : kept) {
                        bw.write(it.x + "," + it.z + "," + it.score + "\n");
                    }
                }
                System.out.println("Verified " + rows.size() + " rows; kept " + kept.size() + " -> wrote " + args.resultsPath);
                // Do NOT close biomeCloser here; leave open for breakdown/printing.
            }
        }
        // Choose what to print as "Top":
        TopK.Item bestToPrint = null;
        boolean validatedTop = false;

        if (args.biomes) {
            // After validation, results.csv contains the filtered/updated winners.
            List<CsvRow> vrows = readCsvXZ(args.resultsPath);
            if (!vrows.isEmpty()) {
                CsvRow r0 = vrows.get(0);
                double sc = (r0.score != null) ? r0.score : 0.0;
                bestToPrint = new TopK.Item(r0.x, r0.z, sc);
                validatedTop = true;
            }
        }

        if (bestToPrint == null && !out.isEmpty()) {
            bestToPrint = out.get(0);
        }

        if (bestToPrint != null) {
            if (validatedTop) {
                System.out.printf("Top (validated): x=%d z=%d score=%.6f%n", bestToPrint.x, bestToPrint.z, bestToPrint.score);
            } else {
                System.out.printf("Top: x=%d z=%d score=%.6f%n", bestToPrint.x, bestToPrint.z, bestToPrint.score);
            }

            ChunkClassifier.Breakdown bd = ChunkClassifier.classifyForCenter(
                    args.seed,
                    bestToPrint.x,
                    bestToPrint.z,
                    CR,
                    args.farmY,
                    args.samples,
                    biome
            );

            System.out.println("\nChunk breakdown for Top (chunk coords):");
            System.out.println("  Full chunks, no DeepDark/Mushroom (fully biome-ok): " + bd.fullCoverFullBiome.size());
            System.out.println("    " + bd.fullCoverFullBiome);
            System.out.println("  Full chunks, partial DeepDark/Mushroom (partially biome-ok): " + bd.fullCoverPartBiome.size());
            System.out.println("    " + bd.fullCoverPartBiome);
            System.out.println("  Partial chunks cut by radius, fully biome-ok: " + bd.partCoverFullBiome.size());
            System.out.println("    " + bd.partCoverFullBiome);
            System.out.println("  Partial chunks cut by radius, partial DeepDark/Mushroom: " + bd.partCoverPartBiome.size());
            System.out.println("    " + bd.partCoverPartBiome);
        }

        // Close biome backend if we opened one.
        if (biomeCloser != null) {
            try { biomeCloser.close(); } catch (Exception ignored) {}
        }
    }

    private static boolean isCirclePortionFullyBlocked(
            BiomeProvider biome,
            int x0, int z0,
            int chunkX, int chunkZ,
            int dx, int dz,
            KernelWeights kernel,
            int farmY,
            int samples,
            int R2
    ) {
        // Chunk bounds in block coords
        int xMin = chunkX * 16;
        int zMin = chunkZ * 16;

        // If fully covered, sample whole chunk
        boolean full = kernel.isFull(dx, dz);

        int insideSamples = 0;
        int blockedSamples = 0;

        // Sample points at cell centers within the chunk
        // Use at least 4x4; user can raise --samples for better accuracy.
        int s = Math.max(4, samples);

        for (int iz = 0; iz < s; iz++) {
            for (int ix = 0; ix < s; ix++) {
                int sx = xMin + (int)((ix + 0.5) * (16.0 / s));
                int sz = zMin + (int)((iz + 0.5) * (16.0 / s));

                if (!full) {
                    int dx0 = sx - x0;
                    int dz0 = sz - z0;
                    if (dx0 * dx0 + dz0 * dz0 > R2) {
                        continue; // outside circle portion
                    }
                }

                insideSamples++;
                if (biome.isBlocked(sx, farmY, sz)) {
                    blockedSamples++;
                }
            }
        }

        // If no samples fell inside due to coarse grid, fall back to checking chunk center if it lies inside circle
        if (insideSamples == 0) {
            int sx = xMin + 8;
            int sz = zMin + 8;
            int dx0 = sx - x0;
            int dz0 = sz - z0;
            if (dx0 * dx0 + dz0 * dz0 <= R2) {
                return biome.isBlocked(sx, farmY, sz);
            }
            return false;
        }

        return blockedSamples == insideSamples;
    }
}