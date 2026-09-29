package storage;

import java.io.File;
import java.util.Arrays;

public class Demo {

    public static void main(String[] args) {
        String path = "demo.db";
        File f = new File(path);
        if (f.exists()) {
            f.delete();
        }

        PageManager pm = new PageManager(new FileManager(path));

        // 1. allocate 5 pages (page 0 is the super block, data pages start at 1)
        int[] pages = new int[5];
        for (int i = 0; i < pages.length; i++) {
            pages[i] = pm.allocatePage();
        }
        System.out.println("allocated pages: " + Arrays.toString(pages));

        // 2. write -> read roundtrip through the cache
        BufferPool bp = new BufferPool(pm, 3, "LRU");
        byte[] a = filled('A');
        bp.writePage(pages[0], a);
        if (!Arrays.equals(bp.getPage(pages[0]), a)) {
            throw new AssertionError("roundtrip failed");
        }
        bp.flushAll();
        System.out.println("roundtrip OK");

        // 3. persistence: reopen the file and read the written page
        PageManager pm2 = new PageManager(new FileManager(path));
        if (!Arrays.equals(pm2.readPage(pages[0]), a)) {
            throw new AssertionError("persistence failed");
        }
        System.out.println("persistence OK: data survived reopen");

        // 4. LRU vs FIFO on a workload with temporal locality
        int[] access = {0, 1, 2, 0, 1, 3, 0, 1, 4};
        BufferPool lru = new BufferPool(pm, 3, "LRU");
        BufferPool fifo = new BufferPool(pm, 3, "FIFO");
        for (int idx : access) {
            lru.getPage(pages[idx]);
            fifo.getPage(pages[idx]);
        }

        System.out.println("\n--- cache stats ---");
        System.out.println("LRU : " + lru.getStats());
        System.out.println("FIFO: " + fifo.getStats());

        // 5. eviction log for a write-heavy workload
        BufferPool bp2 = new BufferPool(pm, 2, "LRU");
        for (int i = 0; i < 4; i++) {
            bp2.writePage(pages[i], filled('D'));
        }
        System.out.println("\neviction log (capacity 2):");
        for (String s : bp2.getEvictionLog()) {
            System.out.println("  " + s);
        }

        bp2.flushAll();
        f.delete();
        System.out.println("\ndemo complete");
    }

    private static byte[] filled(char c) {
        byte[] b = new byte[Constants.PAGE_SIZE];
        Arrays.fill(b, (byte) c);
        return b;
    }
}
