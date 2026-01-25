# SlimeFinder  (Biome-Aware, High-Performance Slime Chunk Searcher for Minecraft Java Edition)

SlimeFinder is a **high-performance Minecraft Java Edition slime chunk finder** designed for **very large search ranges** (10k–100k+ chunks) with **optional biome validation** (Deep Dark & Mushroom Fields).

This tool is optimized for **speed, parallelism, and correctness**, and is capable of **outperforming existing tools such as _slimy_** for large-scale searches.

**Demonstration Video in Mac:** https://youtu.be/e105Z5gf0tI
**Demonstration Video in Windows:** https://youtu.be/Vq8w5XUUHtg?si=1GbspSBUKcyf6NBW
**Demonstration Video in Linux coming soon!**

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
- Java 17
- macOS

On this machine:
- 10,000 × 10,000 chunk searches complete in 2-3 seconds
- Biome validation adds only incremental overhead

Your results may vary depending on CPU, memory, and thread count.

---

## 📦 Requirements

- Java **17** (required; other versions are not supported)
- **Git** (required for cloning the repository and submodules)
- **CMake** (required for building the Cubiomes native wrapper when using biome validation)
- Gradle (wrapper included; no separate installation required)

⚠️ **Warning:** I had not fully tested this in Linux. If you have issues running in Linux, then let me know.

### Installing Prerequisites

You must install **Git** and **CMake** manually before running SlimeFinder.  
If these tools are missing, your terminal may report errors such as `command not found` or fail during the native build step.

- **Git**
  - Download: https://git-scm.com/install
  - Windows users: Ensure Git is added to your PATH during installation.

- **CMake**
  - Download: https://cmake.org/download/
  - Windows users: Make sure to select **"Add CMake to system PATH"** during installation.

After installation, verify both tools are available:

```bash
git --version
cmake --version
```

If either command fails, restart your terminal and re-check your system PATH.

---

## ▶️ How to Run

### 0) Clone the repository (with submodules)

```bash
git clone --recursive https://github.com/BrianBLee1201/Slimefinder.git
cd Slimefinder
```

If `external/cubiomes/cubiomes` is empty, run:
```bash
git submodule update --init --recursive
```

### 1) (Optional) Build the Cubiomes wrapper for biome validation

If you want `--biomes` validation, build the native wrapper first (see the **🧩 Building the Cubiomes Native Wrapper** section below).  
If you do not need biome validation, you can skip this and run the basic mode.

### 2) Basic (No Biome Validation)

macOS / Linux:
```bash
./gradlew run --args="--seed <SEED> --m-chunks <M>"
```

Windows (CMD / PowerShell):
```bat
gradlew run --args="--seed <SEED> --m-chunks <M>"
```

Example:
```bash
./gradlew run --args="--seed 11868470311385 --m-chunks 10000 --threshold 50 --threads 8"
```
The command above:
- sets seed to 11868470311385
- searches chunks (square) within 10000 chunks
- records (x, z) coordinates and their scores to `before_validation.csv`
- Uses 8 threads

This produces:
- `before_validation.csv`
- `results.csv` (same as `before_validation.csv` when biome validation is OFF)

### 3) With Biome Validation (Deep Dark & Mushroom Fields)

After building the wrapper, pass the correct library for your OS:

- macOS: `native/build/libcubiomeswrap.dylib`
- Linux: `native/build/libcubiomeswrap.so`
- Windows: `native\build\libcubiomeswrap.dll` (and ensure `libwinpthread-1.dll` is alongside it; see FAQ)

Run:

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
  --mc-version <ver>
  --cubiomes-lib <path-to-built-cubiomeswrap>
"
```

Example (macOS):
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
  --mc-version 1.21.11
  --cubiomes-lib native/build/libcubiomeswrap.dylib
"
```

Workflow:
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


---

## 🧩 Building the Cubiomes Native Wrapper (Required for Biome Validation)

⚠️ This step requires **CMake** to be installed and available in your system PATH.

This section explains how to build the native Cubiomes wrapper library required for biome validation. The library must be built for your platform and placed in the `native/build` folder.

### 1. Build on macOS / Linux

**For Linux Users:** Run:

```bash
sudo apt-get install -y build-essential cmake
```

This installs cmake.

**For macOS/Linux Users:** Run:

```bash
cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/build --config Release
```

This produces `libcubiomeswrap.dylib` on macOS or `libcubiomeswrap.so` on Linux.

### 2. Build on Windows (MSYS2 MinGW64 recommended)

**Important:** The following Windows build instructions must be run inside the **MSYS2 MinGW64 terminal**. Do not use Windows CMD or PowerShell for these commands, as the environment and toolchain differ.

#### Install build tools

Open the MSYS2 MinGW64 terminal and run:

```bash
pacman -Syu
pacman -S --needed mingw-w64-x86_64-toolchain mingw-w64-x86_64-cmake mingw-w64-x86_64-make
```

This installs the necessary compiler, CMake, and make tools.

#### Change directory to the Slimefinder repo

From the MSYS2 MinGW64 terminal, navigate to your Slimefinder repository folder, for example:

```bash
cd /c/Users/<PATH_TO>/Slimefinder
```

Replace `<PATH_TO>` with your current path that leads to Slimefinder.

#### Build commands

```bash
cmake -S native -B native/build -G "MinGW Makefiles" -DCMAKE_BUILD_TYPE=Release
cmake --build native/build
```

After build:

```bash
ls native/build
```

You should see the DLL file named **`libcubiomeswrap.dll`** inside the `native/build` directory.

#### Using the Built Library

- macOS / Linux: Use the path to `libcubiomeswrap.dylib` or `libcubiomeswrap.so`, e.g.:
  `--cubiomes-lib native/build/libcubiomeswrap.dylib`

- **Windows:** Use the path to the DLL as:
  `--cubiomes-lib native\build\libcubiomeswrap.dll`

  **Note:** The DLL may load correctly when running inside the MSYS2 MinGW64 terminal but fail when running from Windows CMD or PowerShell due to missing MinGW runtime dependencies. If you encounter loading errors, copy the `libwinpthread-1.dll` file from your MSYS2 installation's `mingw64\bin` folder (typically `C:\msys64\mingw64\bin`) into the `native\build\` directory alongside `libcubiomeswrap.dll`. This ensures the required runtime is available when running outside MSYS2.

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
| `--cubiomes-lib` | Path to a Cubiomes Native library (you need to build it)|
| `--mc-version` | Minecraft Java version (e.g. `1.21.11`, `1.20.1`, `1.19.4`, `1.18.2`) used to select the correct biome-generation rules |

## 📊 Chunk Breakdown Explained

For the printed **Top** result, SlimeFinder reports:
- **Full chunks**: chunk fully inside the 128-block sphere
- **Partial chunks**: chunk partially intersecting the sphere
- **Biome-ok**: no Deep Dark / Mushroom Fields
- **Partial biome**: some samples blocked
- **Fully blocked**: excluded entirely from score

The sum of contributing chunks **exactly matches the printed score.**

## 🧩 Minecraft Version Compatibility (1.18+)

SlimeFinder’s **slime chunk detection** is based on the official Java Edition algorithm and is **version-independent**.

However, **biome validation** (`--biomes`) depends on the external biome engine (currently **Cubiomes**) and on the fact that certain biomes prevent slime spawning.

### ✅ Intended Support (Current)

For now, SlimeFinder is intended for **Minecraft Java Edition 1.18 and above**.

- **1.18–1.18.2**: Deep Dark does **not** exist; biome validation blocks **Mushroom Fields** only.
- **1.19+**: Deep Dark exists and can impact farm reliability, so biome validation is strongly recommended (blocks **Deep Dark** + **Mushroom Fields**).

### ⚠ Notes on Newer Biomes (e.g., Pale Garden)

Minecraft introduces new biomes over time (for example, **Pale Garden** in **1.21.4+**).

- SlimeFinder’s current biome validation focuses on **Deep Dark** and **Mushroom Fields** because they directly affect slime spawning.
- If a newly introduced biome is not modeled by the current biome engine, SlimeFinder can still remain correct for slime spawning **as long as the blocking-biome checks remain valid** (Deep Dark / Mushroom Fields).

### 🧾 About `--mc-version`

SlimeFinder accepts a **Minecraft Java Edition version string** via `--mc-version` (for example `1.21.11`).

Internally, the Cubiomes backend uses a **numeric version ID** to select world-generation rules. SlimeFinder maps your `--mc-version` to the appropriate Cubiomes ID automatically so you don’t have to.

> ⚠️ **Important Notice**
>
> - This mapping is **best-effort** and may evolve as Minecraft and/or the backend libraries change.
> - If you run into a version that isn’t recognized, please open an issue with your Minecraft version and OS.

#### Common Version Families (Best-Effort)

| Minecraft Java Edition | Used internally by backend |
|-----------------------|----------------------------|
| 1.18.x | Cubiomes family id `118` |
| 1.19.x | Cubiomes family id `119` |
| 1.20.x | Cubiomes family id `120` |
| 1.21.x (incl. 1.21.0–1.21.11) | Cubiomes family id `125` |

In practice, SlimeFinder maps by **major.minor** (e.g., any `1.21.*` → the `1.21` family).

---

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

9. **[WINDOWS] CMake Error: unable to find a build program corresponding to ‘MinGW Makefiles’:** This error usually occurs if you try to build the native wrapper on Windows without using the MSYS2 MinGW64 terminal or if the required MinGW toolchain is not installed.

  **Fix:** Install MSYS2 and run the build commands inside the MSYS2 MinGW64 terminal. Also ensure you have installed the MinGW toolchain and build tools via:
  ```bash
  pacman -Syu
  pacman -S --needed mingw-w64-x86_64-toolchain mingw-w64-x86_64-cmake mingw-w64-x86_64-make
  ```
  Then rerun the build commands from within MSYS2 MinGW64.

10. **[WINDOWS] Biome validation works in MinGW64 terminal but fails in Windows CMD/PowerShell:** This happens because the MinGW runtime dependency `libwinpthread-1.dll` is not found when running outside MSYS2.

**Fix:** Run:
```bash
where libwinpthread-1.dll
```

Then copy `libwinpthread-1.dll` from the MSYS2 `mingw64\bin` directory (usually `C:\msys64\mingw64\bin`) into the `native\build\` folder alongside `libcubiomeswrap.dll`, or ensure your system PATH includes the MSYS2 `mingw64\bin` directory when running SlimeFinder.

---

## 🔮 Long-Term Plans & Version Support

SlimeFinder is designed with **long-term extensibility** in mind. While the current focus is on **Minecraft Java Edition 1.19+** (tested primarily around **1.21.x**), future updates aim to broaden compatibility and functionality.

Planned and potential improvements include:

- **Support for additional Minecraft Java versions**
  - Older versions (e.g. pre 1.18) with different biome layouts
  - Version-specific presets to reduce user configuration errors

- **Expanded biome veto rules**
  - Optional exclusion of other biomes if future mechanics require it
  - More fine-grained biome classification (e.g. partially safe biomes)
  
- **Optional alternative biome backends (future)**
  - Cubiomes is fast and reliable for many versions, and the maintainers have their own timelines and priorities.
  - If Minecraft adds new biome rules that require more up-to-date coverage than our current backend provides, SlimeFinder may optionally support an additional backend (e.g., an AMIDST/toolbox4minecraft-style approach) **without removing Cubiomes support**.
  - The goal would be to give users more flexibility across versions while staying respectful of and compatible with existing community tools.

- **Additional output formats**
  - JSON output for programmatic use
  - Visualization-friendly exports

- **Further performance tuning**
  - Better work-stealing strategies
  - Adaptive tiling based on memory pressure
  - Optional GPU-assisted exploration experiments (research-only)
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

This project would not be possible without the Minecraft technical community, modders, and researchers who documented and reverse-engineered game mechanics over many years.he `native/build` directory.

---
