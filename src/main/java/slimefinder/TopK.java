package slimefinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.List;

public final class TopK {
    public static final class Item {
        public final int x, z;
        public final double score;
        public Item(int x, int z, double score) {
            this.x = x; this.z = z; this.score = score;
        }
    }

    private final int k;
    private final PriorityQueue<Item> pq; // min-heap by score

    public TopK(int k) {
        this.k = k;
        this.pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a.score));
    }

    public void offer(int x, int z, double score) {
        if (k <= 0) return;
        if (pq.size() < k) {
            pq.offer(new Item(x, z, score));
            return;
        }
        if (!pq.isEmpty() && score > pq.peek().score) {
            pq.poll();
            pq.offer(new Item(x, z, score));
        }
    }

    public List<Item> toSortedListDesc() {
        ArrayList<Item> out = new ArrayList<>(pq);
        out.sort((a,b) -> Double.compare(b.score, a.score));
        return out;
    }
}