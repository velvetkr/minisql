package storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 时钟（第二次机会）算法：页排成一个环，每页有一个引用位。
 * 命中只置引用位（不移位）；淘汰时从指针扫描：
 * 引用位为 1 → 清 0 并跳过（给第二次机会），为 0 → 选中淘汰。
 */
public final class ClockPolicy implements EvictionPolicy {

    private final List<Integer> ring = new ArrayList<>();
    private final Map<Integer, Boolean> refBit = new HashMap<>();
    private int hand;

    @Override
    public void onAccess(int pageId) {
        refBit.put(pageId, true);
    }

    @Override
    public void onInsert(int pageId) {
        ring.add(pageId);
        refBit.put(pageId, true);
    }

    @Override
    public void onRemove(int pageId) {
        int idx = ring.indexOf(pageId);
        if (idx < 0) {
            refBit.remove(pageId);
            return;
        }
        ring.remove(idx);
        refBit.remove(pageId);
        if (ring.isEmpty()) {
            hand = 0;
        } else {
            if (idx < hand) {
                hand--;
            }
            hand %= ring.size();
        }
    }

    @Override
    public int pickVictim() {
        while (true) {
            int id = ring.get(hand);
            if (Boolean.TRUE.equals(refBit.get(id))) {
                refBit.put(id, false);
                hand = (hand + 1) % ring.size();
            } else {
                return id;
            }
        }
    }

    @Override
    public String name() {
        return "CLOCK";
    }
}
