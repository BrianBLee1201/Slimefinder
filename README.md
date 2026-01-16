# SlimeFinder  (Biome-Aware, High-Performance Slime Chunk Searcher for Minecraft Java Edition)

SlimeFinder is a **high-performance Minecraft Java Edition slime chunk finder** designed for **very large search ranges** (10k–100k+ chunks) with **optional biome validation** (Deep Dark & Mushroom Fields).

This tool is optimized for **speed, parallelism, and correctness**, and is capable of **outperforming existing tools such as _slimy_** for large-scale searches.

---

## ✨ What This Tool Does

SlimeFinder helps you find **optimal AFK positions** for large slime farms by:

1. **Scanning a large square of chunk-aligned candidate centers**
2. Counting how many slime chunks intersect a **128-block despawn sphere**
3. (Optional) **Validating biomes** to exclude:
   - Deep Dark
   - Mushroom Fields
4. Producing:
   - `before_validation.csv` → fast, biome-agnostic results
   - `results.csv` → final biome-validated results
5. Printing a **detailed breakdown** of contributing slime chunks:
   - full vs partial coverage
   - biome-safe vs biome-blocked

This makes SlimeFinder suitable for:
- Large perimeter slime farms (comes with either full portals from y level 40 to -64 or sliced portals)
- Bedrock-cleared mega farms

---

## 🧠 Key Design Principles

- **Exact Java slime chunk formula** (matches Minecraft source code)
- **Integer-based counting** (no floating-point noise)
- **Two-phase pipeline**:
  - Fast search first
  - Biome validation only on top candidates
- **Heavy parallelism**
- **Memory-safe tiling** (no giant grids stored in RAM)

---

## 🚀 Performance Notes

Tested on:

- **Apple M3 Max**
- **36 GB unified memory**
- Java 17 (you must include Java 17. Any other versions are not supported.)
- macOS

On this machine:
- 10,000 × 10,000 chunk searches complete in 2-3 seconds
- Biome validation adds only incremental overhead

Your results may vary depending on CPU, memory, and thread count.

---

## 📦 Requirements

- Java **17+**
- Gradle (wrapper included)
- macOS / Linux (Windows may work with minor adjustments)

Optional (for biome validation):
- `libcubiomeswrap.dylib`
- Cubiomes-compatible Minecraft version ID

⚠️ **Warning:** I had not fully tested this in Windows and Linux. If you have issues running in Windows or Linux, then let me know.

---

## ▶️ How to Run

### Basic (No Biome Validation)

```bash
./gradlew run --args="--seed <SEED> --m-chunks <M>"
```
Example:
```bash
./gradlew run --args="--seed 11868470311385 --m-chunks 10000 --threshold 50 --threads 8"
```
This produces:
- `before_validation.csv`
- `results.csv` (same as `before_validation.csv`)

### With Biome Validation (Deep Dark & Mushroom Fields)

```bash
./gradlew run --args="
  --seed <SEED>
  --m-chunks <M>
  --inner-chunks <O>
  --threshold <T>
  --threads <N>
  --biomes
  --farm-y -64
  --samples 4
  --cubiomes-lib ./libcubiomeswrap.dylib
  --cubiomes-mc 125
"
```
Example:
```bash
./gradlew run --args="
  --seed 11868470311385
  --m-chunks 10000
  --inner-chunks 5000
  --threshold 50
  --threads 8
  --biomes
  --farm-y -64
  --samples 4
  --cubiomes-lib ./libcubiomeswrap.dylib
  --cubiomes-mc 125
"
```
Here is the workflow:
1. Fast search ignoring biomes → `before_validation.csv`
2. Validate each candidate against biomes
3. Write final filtered results → `results.csv`
4. Print the best validated location and chunk breakdown

### 🧾 Output Files

**`before_validation.csv`**

Fast, biome-agnostic results:
```bash
x,z,score
1383104,-64304,51
-323632,-187824,50
...
```
**`results.csv`**

Final biome-validated results (filtered + updated scores).

## ⚙️ Parameters Explained

### Required

| Flag | Description |
| --- | ---|
| `--seed` | Minecraft world seed (64-bit signed integer)|
| `--m-chunks` | Search square within 4 endpoint chunks: (-m, -m), (-m, m), (m, -m), and (m, m) |

### Common Options

| Flag | Description |
| --- | ---|
| `--inner-chunks` | Exclude square within 4 endpoint chunks: (-o, -o), (-o, o), (o, -o), and (o, o), saving performance (default: 0, which is the full square search)|
| `--threshold` | Minimum slime-chunk count to keep (default: 6) |
| `--threads` | Number of worker threads (default: CPU count) |
| `--topk` | Number of candidates kept for validation (default: 50) |

### Biome Validation Options

| Flag | Description |
| --- | ---|
| `--biomes` | Enable biome validation |
| `--farm-y` | Y-level used for biome sampling (default: -64)|
| `--samples` | Samples per axis per chunk (default: 4, total 16)|
| `--cubiomes-lib` | Path to `libcubiomeswrap.dylib`|
| `--cubiomes-mc` | Cubiomes Minecraft version ID (e.g. 125 for 1.21.x)|

### Performance / Advanced Tuning

| Flag | Description |
| --- | ---|
| `--tile-rows` | Z-rows per tile (default: 512) |
| `--tile-cols` | X-columns per tile (default: 4096)|
| `--kz-block` | Z-block size for kernel reuse (default: 64)|

These options help control memory usage for **very large searches** (e.g. 100k chunks).

## 📊 Chunk Breakdown Explained

For the printed **Top** result, SlimeFinder reports:
- **Full chunks**: chunk fully inside the 128-block sphere
- **Partial chunks**: chunk partially intersecting the sphere
- **Biome-ok**: no Deep Dark / Mushroom Fields
- **Partial biome**: some samples blocked
- **Fully blocked**: excluded entirely from score

The sum of contributing chunks **exactly matches the printed score.**

## 🔍 FAQs and Troubleshooting

1. **Does this work on Bedrock Edition:** Unfortunately, no. The code that finds slime chunks is completely different from Java.
2. **Why do Deep Dark and Mushroom Fields matter:** Because hostile mobs never spawn, so we cannot have slime chunks in that area.
3. **Why does SlimeFinder ignores biomes first:** 
   * **Short answer:** to improve performance.
   * **Long answer:** Biome checks are expensive. Because of this, SlimeFinder finds strong candidates fast, then validates biomes only where it matters. This is why it can scale to massive ranges.
4. **Why is my score lower after validation:** Some slime chunks intersect Deep Dark and Mushroom Fields biomes, so fully blocked chunks are removed from the score.
5. **My results differ from Chunkbase. Why is this:** While the code that calculates the slime chunks is correct, when you toggle the `Biome Height` and set the option to `Bottom (Y -51)`, some of the biomes that are adjacent to the deep dark biomes have deep dark at Y-level -64. This is misleading, so SlimeFinder samples exactly at your _chosen_ Y-level and matches Minecraft spawning logic more closely.
6. **I ran `./gradlew run` with no arguments:** Make sure to run this:
```bash
./gradlew run --args="--help"
```
to see usage and examples.

7. **Why do we pick one point of a chunk instead of sampling all points within a square:** to improve performance. A chunk is a 16x320x16 area. There are 256 squares in a chunk at a fixed y level, and sampling all points and calculating would sharply increase computational time.
8. **Why is the cubiomes folder empty:** you need to git clone _recursively_: `git clone --recursive https://github.com/BrianBLee1201/Slimefinder.git`

## 🔮 Long-Term Plans & Version Support

SlimeFinder is designed with **long-term extensibility** in mind. While the current focus is on modern Minecraft Java Edition versions (tested primarily around 1.21.x), future updates aim to broaden compatibility and functionality.

Planned and potential improvements include:

- **Support for additional Minecraft Java versions**
  - Older versions (e.g. 1.18–1.20) with different biome layouts
  - Version-specific presets to reduce user configuration errors

- **Expanded biome veto rules**
  - Optional exclusion of other biomes if future mechanics require it
  - More fine-grained biome classification (e.g. partially safe biomes)

- **Improved Cubiomes integration**
  - Clearer mapping between `--cubiomes-mc` values and Minecraft versions
  - Predefined aliases (e.g. `--mc-version 1.21.1`)
  - Optional automatic version detection

- **Additional output formats**
  - JSON output for programmatic use
  - Visualization-friendly exports

- **Further performance tuning**
  - Better work-stealing strategies
  - Adaptive tiling based on memory pressure
  - Optional GPU-assisted exploration experiments (research-only)
- **Windows and Linux Support**
  - Currently, I am trying to fix the `[ERROR] Biome validation requires cubiomes backend. Failed to load.` message in Windows, even if `external/cubiomes` folder is not empty. So far I speculate it might be a hardware incompatibility. **This means you are not able to run the biome check, so you will want to verify each and every (x, z) coordinates manually**.
- **Searching from a specific margin**
  - Computing at the borders without needing to recalculate the whole square that you know do not have enough slime chunks coverage
  - Given a small square of length `m` and a large square of length `o`, it searches from the square ring with size `o-m` instead of searching the entire square with size `o`, which saves performance.
  - The world border occurs 30M blocks from (0, 0), so if you normally run the square with 1875000 chunks, it will take really long time. This is why I plan on searching throught the border instead of the entire square.
- **Calculating the chunk statistics at one single point**
  - Giving overview of how many chunks covered, including partial deep dark and mushroom fields biomes.

Suggestions and contributions are welcome. This project is intended to evolve alongside the Minecraft technical community.

## 🙏 Credits & Acknowledgements

SlimeFinder builds on well-established community knowledge and prior tools in the Minecraft technical community. While **all code in this repository is original**, several projects and references strongly influenced its design and implementation.

### 🧱 Minecraft Slime Chunk Formula

The slime chunk detection logic is based on the official Java Edition algorithm, as documented by the Minecraft community:

- **Minecraft Wiki – Slime**
  - https://minecraft.fandom.com/wiki/Slime

This reference documents the exact `java.util.Random`-based formula used by Minecraft Java Edition, which SlimeFinder re-implements exactly for correctness and performance.

### 🧬 Cubiomes (Biome Generation)

Biome validation (Deep Dark & Mushroom Fields) is implemented using a custom C wrapper around the Cubiomes library.

- **Cubiomes / Cubitect**
  - Repository: https://github.com/Cubitect/cubiomes-viewer

Cubiomes was invaluable in enabling accurate biome detection at a specific Y-level, which is critical for validating slime farm locations in modern Minecraft versions.

SlimeFinder does **not** bundle Cubiomes directly. Users must provide their own Cubiomes-compatible dynamic library (`libcubiomeswrap.dylib`).

### ⚡ slimy (Performance Inspiration)

- **slimy**
  - Repository: https://github.com/silversquirl/slimy

The idea (not code) of sampling a representative point per chunk instead of all 256 block positions was inspired by slimy. SlimeFinder independently implements this optimization in Java with its own architecture, parallelism model, and validation pipeline.

### ❤️ Community Knowledge

This project would not be possible without the Minecraft technical community, modders, and researchers who documented and reverse-engineered game mechanics over many years.