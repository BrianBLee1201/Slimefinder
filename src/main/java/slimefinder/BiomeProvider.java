package slimefinder;

/**
 * Biome veto interface.
 * Return true if (x,y,z) is in a biome that blocks slime spawning
 * (Deep Dark or Mushroom Fields for your use case).
 */
public interface BiomeProvider {
    boolean isBlocked(int x, int y, int z);
}