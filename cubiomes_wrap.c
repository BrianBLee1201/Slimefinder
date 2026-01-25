// cubiomes_wrap.c
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include "util.h"
#include "generator.h"
#include "biomes.h"

typedef struct {
    Generator g;
    int block_deep_dark;
    int block_mushroom_fields;
} Ctx;

int cbi_gen_quart_plane(void* p, int qx, int qz, int sx, int sz, int yq, int* out)
{
    if (!p || !out || sx <= 0 || sz <= 0) return 1;
    Ctx* ctx = (Ctx*)p;

    Range r;
    r.scale = 4;
    r.x = qx;
    r.z = qz;
    r.sx = sx;
    r.sz = sz;

    r.y = yq;
    r.sy = 1;

    int* cache = allocCache(&ctx->g, r);
    if (!cache) return 2;

    genBiomes(&ctx->g, cache, r);

    for (int i = 0; i < sx*sz; i++) out[i] = cache[i];

    free(cache);
    return 0;
}

void* cbi_new(uint64_t seed, int mc)
{
    Ctx* ctx = (Ctx*)malloc(sizeof(Ctx));
    if (!ctx) return NULL;

    setupGenerator(&ctx->g, mc, 0);
    applySeed(&ctx->g, DIM_OVERWORLD, seed);

    // Default behavior (backwards compatible):
    ctx->block_deep_dark = (mc >= MC_1_19) ? 1 : 0;
    ctx->block_mushroom_fields = 1;

    return (void*)ctx;
}

void cbi_free(void* p)
{
    if (p) free(p);
}

/** Configure which biomes are treated as blocked (1=true, 0=false). */
void cbi_set_block_rules(void* p, int blockDeepDark, int blockMushroomFields)
{
    if (!p) return;
    Ctx* ctx = (Ctx*)p;
    ctx->block_deep_dark = (blockDeepDark != 0);
    ctx->block_mushroom_fields = (blockMushroomFields != 0);

    printf("cbi_set_block_rules: deep_dark=%d mushroom_fields=%d\n",
           ctx->block_deep_dark, ctx->block_mushroom_fields);
}

// Returns 1 if biome is blocked at (x,y,z), else 0
int cbi_is_blocked(void* p, int x, int y, int z)
{
    if (!p) return 0;
    Ctx* ctx = (Ctx*)p;

    int id = getBiomeAt(&ctx->g, 1, x, y, z);

    if (ctx->block_deep_dark && id == deep_dark) return 1;
    if (ctx->block_mushroom_fields && id == mushroom_fields) return 1;
    return 0;
}

int cbi_biome_id_deep_dark(void) {
    return deep_dark;
}

// Returns 1 if the given Cubiomes MC id supports Deep Dark, else 0.
int cbi_supports_deep_dark(int mc)
{
  return (mc >= MC_1_19) ? 1 : 0;
}

int cbi_biome_id_mushroom_fields(void) {
    return mushroom_fields;
}