package slimefinder;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.io.File;
import java.util.Objects;

public final class CubiomesBiomeProvider implements BiomeProvider, AutoCloseable {

    private interface CubiomesWrap extends Library {
        Pointer cbi_new(long seed, int mc);
        void cbi_free(Pointer ctx);

        int cbi_is_blocked(Pointer ctx, int x, int y, int z);

        int cbi_gen_quart_plane(Pointer ctx, int qx, int qz, int sx, int sz, int yq, int[] out);

        int cbi_biome_id_deep_dark();
        int cbi_biome_id_mushroom_fields();

        // NEW:
        void cbi_set_block_rules(Pointer ctx, int blockDeepDark, int blockMushroomFields);
    }

    private final CubiomesWrap lib;
    private final Pointer ctx;

    private final int DEEP_DARK_ID;
    private final int MUSHROOM_FIELDS_ID;
    private final boolean HAS_BIOME_ID_EXPORTS;

    private final boolean blockDeepDark;
    private final boolean blockMushroomFields;

    public int[] genQuartPlane(int qx, int qz, int sx, int sz, int yQuart) {
        int[] out = new int[sx * sz];
        int rc = lib.cbi_gen_quart_plane(ctx, qx, qz, sx, sz, yQuart, out);
        if (rc != 0) throw new RuntimeException("cbi_gen_quart_plane failed rc=" + rc);
        return out;
    }

    // NEW constructor with rule selection
    public CubiomesBiomeProvider(long seed, int mc, String libPath, boolean blockDeepDark, boolean blockMushroomFields) {
        Objects.requireNonNull(libPath, "libPath");

        String toLoad = libPath;
        File f = new File(libPath);
        if (f.exists()) toLoad = f.getAbsolutePath();

        this.lib = Native.load(toLoad, CubiomesWrap.class);

        this.ctx = lib.cbi_new(seed, mc);
        if (this.ctx == null || Pointer.nativeValue(this.ctx) == 0) {
            throw new RuntimeException("cbi_new returned NULL (check seed/mc/libPath)");
        }

        this.blockDeepDark = blockDeepDark;
        this.blockMushroomFields = blockMushroomFields;

        // Apply rules in native ctx (optional export; keep backwards-compatible):
        try {
            lib.cbi_set_block_rules(ctx, blockDeepDark ? 1 : 0, blockMushroomFields ? 1 : 0);
        } catch (UnsatisfiedLinkError e) {
            // Older libcubiomeswrap may not export this symbol. In that case,
            // native defaults apply (typically blocking both).
        }

        int dd = -1;
        int mf = -1;
        boolean has = false;
        try {
            dd = lib.cbi_biome_id_deep_dark();
            mf = lib.cbi_biome_id_mushroom_fields();
            has = true;
        } catch (UnsatisfiedLinkError e) {
            has = false;
        }
        this.DEEP_DARK_ID = dd;
        this.MUSHROOM_FIELDS_ID = mf;
        this.HAS_BIOME_ID_EXPORTS = has;
    }

    @Override
    public boolean isBlocked(int x, int y, int z) {
        return lib.cbi_is_blocked(ctx, x, y, z) == 1;
    }

    @Override
    public void close() {
        try { lib.cbi_free(ctx); } catch (Throwable ignored) {}
    }

    public int deepDarkId() { return DEEP_DARK_ID; }
    public int mushroomFieldsId() { return MUSHROOM_FIELDS_ID; }
    public boolean hasBiomeIdExports() { return HAS_BIOME_ID_EXPORTS; }

    // NEW getters so the grid matches the exact same rule
    public boolean blocksDeepDark() { return blockDeepDark; }
    public boolean blocksMushroomFields() { return blockMushroomFields; }
}