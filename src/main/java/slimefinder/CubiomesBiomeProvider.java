package slimefinder;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.io.File;
import java.util.Objects;

/**
 * BiomeProvider backed by a small C wrapper over Cubitect/cubiomes.
 *
 * Expected exported symbols in libcubiomeswrap.dylib:
 *   void* cbi_new(uint64_t seed, int mc);
 *   void  cbi_free(void* ctx);
 *   int   cbi_is_blocked(void* ctx, int x, int y, int z);
 *
 * isBlocked(x,y,z) returns true for Deep Dark or Mushroom Fields.
 */
public final class CubiomesBiomeProvider implements BiomeProvider, AutoCloseable {

    private interface CubiomesWrap extends Library {
        Pointer cbi_new(long seed, int mc);
        void cbi_free(Pointer ctx);
        int cbi_is_blocked(Pointer ctx, int x, int y, int z);
        int cbi_gen_quart_plane(Pointer ctx, int qx, int qz, int sx, int sz, int yq, int[] out);
        int cbi_biome_id_deep_dark();
        int cbi_biome_id_mushroom_fields();
    }

    private final CubiomesWrap lib;
    private final Pointer ctx;
    private final int DEEP_DARK_ID;
    private final int MUSHROOM_FIELDS_ID;
    private final boolean HAS_BIOME_ID_EXPORTS;

    public int[] genQuartPlane(int qx, int qz, int sx, int sz, int yQuart) {
        int[] out = new int[sx * sz];
        int rc = lib.cbi_gen_quart_plane(ctx, qx, qz, sx, sz, yQuart, out);
        if (rc != 0) throw new RuntimeException("cbi_gen_quart_plane failed rc=" + rc);
        return out;
    }

    public CubiomesBiomeProvider(long seed, int mc, String libPath) {
        Objects.requireNonNull(libPath, "libPath");

        String toLoad = libPath;
        File f = new File(libPath);
        if (f.exists()) toLoad = f.getAbsolutePath();

        this.lib = Native.load(toLoad, CubiomesWrap.class);

        this.ctx = lib.cbi_new(seed, mc);
        if (this.ctx == null || Pointer.nativeValue(this.ctx) == 0) {
            throw new RuntimeException("cbi_new returned NULL (check seed/mc/libPath)");
        }

        int dd = -1;
        int mf = -1;
        boolean has = false;
        try {
            dd = lib.cbi_biome_id_deep_dark();
            mf = lib.cbi_biome_id_mushroom_fields();
            has = true;
        } catch (UnsatisfiedLinkError e) {
            // Optional exports may be missing if the dylib wasn't rebuilt.
            // We can still function using cbi_is_blocked() and/or regenerate the dylib.
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
        try {
            lib.cbi_free(ctx);
        } catch (Throwable ignored) {}
    }

    public int deepDarkId() {
        return DEEP_DARK_ID;
    }

    public int mushroomFieldsId() {
        return MUSHROOM_FIELDS_ID;
    }

    public boolean hasBiomeIdExports() {
        return HAS_BIOME_ID_EXPORTS;
    }
}