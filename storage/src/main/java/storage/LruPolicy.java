package storage;

import java.util.LinkedHashSet;

/** 最近最少使用：命中即移到队尾，淘汰队首。 */
public final class LruPolicy implements EvictionPolicy {

    private final LinkedHashSet<Integer> order = new LinkedHashSet<>();

    @Override
    public void onAccess(int pageId) {
        order.remove(pageId);
        order.add(pageId);
    }

    @Override
    public void onInsert(int pageId) {
        order.add(pageId);
    }

    @Override
    public void onRemove(int pageId) {
        order.remove(pageId);
    }

    @Override
    public int pickVictim() {
        return order.iterator().next();
    }

    @Override
    public String name() {
        return "LRU";
    }
}
