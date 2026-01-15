// cubiomes_wrap.c
#include <stdint.h>
#include <stdlib.h>

#include "util.h"     // for allocCache
#include "generator.h"
#include "biomes.h"

typedef struct {
    Generator g;
} Ctx;

/**
 * Fill biome ids for a horizontal rectangle at quart scale (scale=4).
 * Inputs are in quart coordinates (x,z) and y is in quart coordinates.
 *
 * out must have length sx*sz.
 * Returns 0 on success, nonzero on failure.
 */
int cbi_gen_quart_plane(void* p, int qx, int qz, int sx, int sz, int yq, int* out)
{
    if (!p || !out || sx <= 0 || sz <= 0) return 1;
    Ctx* ctx = (Ctx*)p;

    Range r;
    r.scale = 4;       // quart scale
    r.x = qx;
    r.z = qz;
    r.sx = sx;
    r.sz = sz;

    // vertical range: a 1-layer plane at quart y
    r.y = yq;
    r.sy = 1;

    // allocate cache for this range
    int* cache = allocCache(&ctx->g, r);
    if (!cache) return 2;

    genBiomes(&ctx->g, cache, r);

    // cache is indexed y-major; since sy=1 we can just copy
    // layout: cache[iy*sx*sz + iz*sx + ix]
    for (int i = 0; i < sx*sz; i++) out[i] = cache[i];

    free(cache);
    return 0;
}

void* cbi_new(uint64_t seed, int mc)
{
    Ctx* ctx = (Ctx*)malloc(sizeof(Ctx));
    if (!ctx) return NULL;

    // Setup overworld generator
    setupGenerator(&ctx->g, mc, 0);
    applySeed(&ctx->g, DIM_OVERWORLD, seed);
    return (void*)ctx;
}

void cbi_free(void* p)
{
    if (p) free(p);
}

// Returns 1 if biome is deep_dark or mushroom_fields at (x,y,z), else 0
int cbi_is_blocked(void* p, int x, int y, int z)
{
    if (!p) return 0;
    Ctx* ctx = (Ctx*)p;

    // scale=1 means block coordinates
    int id = getBiomeAt(&ctx->g, 1, x, y, z);

    return (id == deep_dark || id == mushroom_fields) ? 1 : 0;
}

// Expose biome numeric IDs so Java can compare efficiently
int cbi_biome_id_deep_dark(void) {
    return deep_dark;
}

int cbi_biome_id_mushroom_fields(void) {
    return mushroom_fields;
}