package slimefinder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides per-chunk biome-ok fraction (0..1) for Deep Dark / Mushroom Fields veto.
 *
 * Cached mode uses tiles (e.g., 512x512 chunks) generated from quart-plane calls,
 * then served through an LRU to keep memory bounded (works for huge scans).
 */
public final class BiomeOkFracGrid {

    private final CubiomesBiomeProvider cb;
    private final int yQuart;
    private final int tileSizeChunks;
    private final int deepDarkId;
    private final int mushroomFieldsId;

    // NEW:
    private final boolean blockDeepDark;
    private final boolean blockMushroomFields;

    private final LinkedHashMap<Long, Tile> lru;
    private final int maxTiles;

    private static final class Tile {
        final int baseCx;
        final int baseCz;
        final byte[] ok; // tileSizeChunks * tileSizeChunks, value 0..255
        Tile(int baseCx, int baseCz, byte[] ok) {
            this.baseCx = baseCx;
            this.baseCz = baseCz;
            this.ok = ok;
        }
    }

    private BiomeOkFracGrid(CubiomesBiomeProvider cb, int farmY, int tileSizeChunks, int maxTiles) {
        this.cb = cb;
        this.yQuart = Math.floorDiv(farmY, 4);
        this.tileSizeChunks = tileSizeChunks;
        this.deepDarkId = cb.deepDarkId();
        this.mushroomFieldsId = cb.mushroomFieldsId();

        // NEW:
        this.blockDeepDark = cb.blocksDeepDark();
        this.blockMushroomFields = cb.blocksMushroomFields();


        this.maxTiles = maxTiles;

        this.lru = new LinkedHashMap<>(64, 0.75f, true);
    }

    /** Create a cached/tiled okFrac provider. */
    public static BiomeOkFracGrid createCached(CubiomesBiomeProvider cb, int farmY, int tileSizeChunks, int maxTiles) {
        return new BiomeOkFracGrid(cb, farmY, tileSizeChunks, maxTiles);
    }

    /** okFrac in [0,1] for chunk (cx,cz). */
    public float okFrac(int cx, int cz) {
        Tile t = getOrLoadTile(cx, cz);
        int lx = cx - t.baseCx;
        int lz = cz - t.baseCz;
        if (lx < 0 || lz < 0 || lx >= tileSizeChunks || lz >= tileSizeChunks) return 1.0f;

        int v = t.ok[lz * tileSizeChunks + lx] & 0xFF;
        return v / 255.0f;
    }

    private Tile getOrLoadTile(int cx, int cz) {
        int baseCx = Math.floorDiv(cx, tileSizeChunks) * tileSizeChunks;
        int baseCz = Math.floorDiv(cz, tileSizeChunks) * tileSizeChunks;
        long key = tileKey(baseCx, baseCz);

        synchronized (lru) {
            Tile existing = lru.get(key);
            if (existing != null) return existing;
        }

        // Compute outside lock
        Tile loaded = loadTile(baseCx, baseCz);

        synchronized (lru) {
            Tile existing = lru.get(key);
            if (existing != null) return existing;

            lru.put(key, loaded);
            if (lru.size() > maxTiles) {
                Map.Entry<Long, Tile> eldest = lru.entrySet().iterator().next();
                lru.remove(eldest.getKey());
            }
            return loaded;
        }
    }

    private Tile loadTile(int baseCx, int baseCz) {
        int quartW = tileSizeChunks * 4;
        int qx0 = baseCx * 4;
        int qz0 = baseCz * 4;

        int[] plane = cb.genQuartPlane(qx0, qz0, quartW, quartW, yQuart);
        byte[] ok = new byte[tileSizeChunks * tileSizeChunks];

        for (int dz = 0; dz < tileSizeChunks; dz++) {
            for (int dx = 0; dx < tileSizeChunks; dx++) {
                int qx = dx * 4;
                int qz = dz * 4;

                int blocked = 0;
                for (int oz = 0; oz < 4; oz++) {
                    int row = (qz + oz) * quartW;
                    for (int ox = 0; ox < 4; ox++) {
                        int id = plane[row + (qx + ox)];

                        boolean isBlocked = (blockDeepDark && id == deepDarkId)
                                || (blockMushroomFields && id == mushroomFieldsId);
                        if (isBlocked) blocked++;
                    }
                }

                int okCount = 16 - blocked;
                int v = (okCount * 255 + 8) / 16;
                ok[dz * tileSizeChunks + dx] = (byte) v;
            }
        }

        return new Tile(baseCx, baseCz, ok);
    }

    private static long tileKey(int baseCx, int baseCz) {
        return (((long) baseCx) << 32) ^ (baseCz & 0xffffffffL);
    }
}