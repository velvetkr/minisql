package storage;

import java.util.LinkedHashSet;

/** 先进先出：严格按装入顺序，命中不改变顺序，淘汰队首。 */
public final class FifoPolicy implements EvictionPolicy {

    private final LinkedHashSet<Integer> order = new LinkedHashSet<>();

    @Override
    public void onAccess(int pageId) {
        // FIFO 命中不改变顺序
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
        return "FIFO";
    }
}
