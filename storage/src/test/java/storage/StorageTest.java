package storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageTest {

    @TempDir
    Path tempDir;

    Path dbPath;
    FileManager fm;
    PageManager pm;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test.db");
        fm = new FileManager(dbPath.toString());
        pm = new PageManager(fm);
    }

    private static byte[] filled(char c) {
        byte[] b = new byte[Constants.PAGE_SIZE];
        Arrays.fill(b, (byte) c);
        return b;
    }

    @Test
    void superBlockOccupiesPageZero() {
        assertEquals(1, pm.numPages());
    }

    @Test
    void readOutOfRangeThrows() {
        assertThrows(PageNotFoundException.class, () -> fm.readPage(999));
    }

    @Test
    void writeThenReadRoundtrip() {
        pm.writePage(1, filled('x'));
        assertArrayEquals(filled('x'), pm.readPage(1));
    }

    @Test
    void allocateSequentialFromOne() {
        assertEquals(1, pm.allocatePage());
        assertEquals(2, pm.allocatePage());
        assertEquals(3, pm.numPages());
    }

    @Test
    void freeAndReuse() {
        int a = pm.allocatePage();
        pm.allocatePage();
        pm.freePage(a);
        assertEquals(a, pm.allocatePage());
    }

    @Test
    void freeSuperBlockThrows() {
        assertThrows(PageNotFoundException.class, () -> pm.freePage(0));
    }

    @Test
    void freeListSurvivesReopen() {
        int a = pm.allocatePage();
        pm.freePage(a);
        PageManager pm2 = new PageManager(new FileManager(dbPath.toString()));
        assertEquals(a, pm2.allocatePage());
    }

    @Test
    void hitAndMiss() {
        BufferPool bp = new BufferPool(pm, 4, "LRU");
        int p = pm.allocatePage();
        bp.getPage(p);
        bp.getPage(p);
        assertEquals(1, bp.getStats().hits());
        assertEquals(1, bp.getStats().misses());
    }

    @Test
    void dirtyEvictionWritesBack() {
        BufferPool bp = new BufferPool(pm, 2, "LRU");
        int p1 = pm.allocatePage();
        int p2 = pm.allocatePage();
        int p3 = pm.allocatePage();
        bp.writePage(p1, filled('a'));
        bp.writePage(p2, filled('b'));
        bp.writePage(p3, filled('c'));
        assertEquals(1, bp.getStats().evictions());
        assertArrayEquals(filled('a'), pm.readPage(p1));
    }

    @Test
    void flushAllPersists() {
        BufferPool bp = new BufferPool(pm, 4, "LRU");
        int p = pm.allocatePage();
        bp.writePage(p, filled('z'));
        bp.flushAll();
        assertArrayEquals(filled('z'), pm.readPage(p));
    }

    @Test
    void lruEvictsLeastRecent() {
        BufferPool bp = new BufferPool(pm, 2, "LRU");
        int p1 = pm.allocatePage();
        int p2 = pm.allocatePage();
        int p3 = pm.allocatePage();
        bp.getPage(p1);
        bp.getPage(p2);
        bp.getPage(p1);
        bp.getPage(p3);
        List<String> log = bp.getEvictionLog();
        assertFalse(log.isEmpty());
        assertTrue(log.get(log.size() - 1).startsWith("page " + p2));
    }

    @Test
    void fifoEvictsEarliestInserted() {
        BufferPool bp = new BufferPool(pm, 2, "FIFO");
        int p1 = pm.allocatePage();
        int p2 = pm.allocatePage();
        int p3 = pm.allocatePage();
        bp.getPage(p1);
        bp.getPage(p2);
        bp.getPage(p1);
        bp.getPage(p3);
        List<String> log = bp.getEvictionLog();
        assertFalse(log.isEmpty());
        assertTrue(log.get(log.size() - 1).startsWith("page " + p1));
    }

    @Test
    void freePageDiscardsCache() {
        BufferPool bp = new BufferPool(pm, 4, "LRU");
        int p = pm.allocatePage();
        bp.writePage(p, filled('d'));
        bp.freePage(p);
        assertEquals(p, pm.allocatePage());
    }

    @Test
    void closeFlushesAll() {
        BufferPool bp = new BufferPool(pm, 4, "LRU");
        int p = pm.allocatePage();
        bp.writePage(p, filled('c'));
        bp.close();
        assertArrayEquals(filled('c'), pm.readPage(p));
    }

    @Test
    void clockGivesSecondChance() {
        BufferPool bp = new BufferPool(pm, 3, "CLOCK");
        int p1 = pm.allocatePage();
        int p2 = pm.allocatePage();
        int p3 = pm.allocatePage();
        int p4 = pm.allocatePage();
        int p5 = pm.allocatePage();
        bp.getPage(p1);
        bp.getPage(p2);
        bp.getPage(p3);
        bp.getPage(p4);   // 装满后第一次淘汰：扫一圈清引用位，淘汰 p1
        bp.getPage(p2);   // 命中，p2 重新获得引用位
        bp.getPage(p5);   // p3 引用位已清零 -> 淘汰 p3（FIFO 此时会淘汰 p2）
        List<String> log = bp.getEvictionLog();
        assertEquals(2, log.size());
        assertTrue(log.get(0).startsWith("page " + p1));
        assertTrue(log.get(1).startsWith("page " + p3));
    }

    @Test
    void clockStatsReportPolicyName() {
        BufferPool bp = new BufferPool(pm, 2, "CLOCK");
        assertEquals("CLOCK", bp.getStats().policy());
    }

    @Test
    void unknownPolicyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new BufferPool(pm, 2, "RANDOM"));
    }
}
