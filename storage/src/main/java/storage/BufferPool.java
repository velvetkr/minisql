package storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BufferPool {

    public record Stats(String policy, int capacity, int size, int hits, int misses,
                        double hitRate, int evictions, int dirtyEvictions) {
    }

    private static final class Entry {
        byte[] data;
        boolean dirty;

        Entry(byte[] data, boolean dirty) {
            this.data = data;
            this.dirty = dirty;
        }
    }

    private final PageManager pm;
    private final int capacity;
    private final String policyName;
    private final EvictionPolicy policy;
    private final Map<Integer, Entry> cache = new HashMap<>();

    private int hits;
    private int misses;
    private int evictions;
    private int dirtyEvictions;
    private final List<String> evictionLog = new ArrayList<>();

    public BufferPool(PageManager pm, int capacity, String policy) {
        this.pm = pm;
        this.capacity = capacity;
        this.policyName = policy.toUpperCase();
        this.policy = createPolicy(this.policyName);
    }

    private static EvictionPolicy createPolicy(String name) {
        return switch (name) {
            case "LRU" -> new LruPolicy();
            case "FIFO" -> new FifoPolicy();
            case "CLOCK" -> new ClockPolicy();
            default -> throw new IllegalArgumentException("unknown policy: " + name);
        };
    }

    public byte[] getPage(int pageId) {
        Entry entry = cache.get(pageId);
        if (entry != null) {
            hits++;
            policy.onAccess(pageId);
            return entry.data.clone();
        }
        misses++;
        byte[] data = pm.readPage(pageId);
        insert(pageId, data);
        return data.clone();
    }

    public void writePage(int pageId, byte[] data) {
        if (data.length != Constants.PAGE_SIZE) {
            throw new IllegalArgumentException("data length must be " + Constants.PAGE_SIZE + ", got " + data.length);
        }
        Entry entry = cache.get(pageId);
        if (entry != null) {
            entry.data = data.clone();
            entry.dirty = true;
            policy.onAccess(pageId);
        } else {
            if (cache.size() >= capacity) {
                evict();
            }
            cache.put(pageId, new Entry(data.clone(), true));
            policy.onInsert(pageId);
        }
    }

    public void flushPage(int pageId) {
        Entry entry = cache.get(pageId);
        if (entry != null && entry.dirty) {
            pm.writePage(pageId, entry.data);
            entry.dirty = false;
        }
    }

    public void flushAll() {
        for (int pageId : new ArrayList<>(cache.keySet())) {
            flushPage(pageId);
        }
    }

    public void close() {
        flushAll();
    }

    public int allocatePage() {
        return pm.allocatePage();
    }

    public void freePage(int pageId) {
        if (cache.remove(pageId) != null) {
            policy.onRemove(pageId);
        }
        pm.freePage(pageId);
    }

    public List<String> getEvictionLog() {
        return new ArrayList<>(evictionLog);
    }

    public Stats getStats() {
        int total = hits + misses;
        double hitRate = total == 0 ? 0.0 : (double) hits / total;
        return new Stats(policyName, capacity, cache.size(), hits, misses, hitRate, evictions, dirtyEvictions);
    }

    private void insert(int pageId, byte[] data) {
        if (cache.size() >= capacity) {
            evict();
        }
        cache.put(pageId, new Entry(data, false));
        policy.onInsert(pageId);
    }

    private void evict() {
        if (cache.isEmpty()) {
            return;
        }
        int victim = policy.pickVictim();
        Entry entry = cache.remove(victim);
        policy.onRemove(victim);
        evictions++;
        boolean dirty = entry.dirty;
        if (dirty) {
            pm.writePage(victim, entry.data);
            dirtyEvictions++;
        }
        String msg = "page " + victim + (dirty ? " dirty -> written back" : " clean -> discarded");
        evictionLog.add(msg);
        System.out.println("[EVICT] " + msg);
    }
}
