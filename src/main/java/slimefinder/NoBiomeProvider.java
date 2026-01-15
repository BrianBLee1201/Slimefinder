package slimefinder;

/** Biomes OFF: never blocks anything. */
public final class NoBiomeProvider implements BiomeProvider {
    @Override
    public boolean isBlocked(int x, int y, int z) {
        return false;
    }
}