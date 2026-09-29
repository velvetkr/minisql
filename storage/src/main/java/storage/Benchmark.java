package storage;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

/**
 * 替换策略对比实验。
 *   Part A —— 策略 × 容量 的命中率矩阵（4 种负载）
 *   Part B —— Bélády 异常验证（FIFO 扩容反而更差，LRU / CLOCK 不退化）
 *
 * 运行：mvn compile && java -cp target/classes storage.Benchmark
 */
public class Benchmark {

    private static final int PAGES = 200;
    private static final int[] CAPACITIES = {5, 10, 20, 50};
    private static final String[] POLICIES = {"LRU", "FIFO", "CLOCK"};

    private record Workload(String name, int[] trace) {
    }

    public static void main(String[] args) throws Exception {
        Path db = Files.createTempFile("bench", ".db");
        db.toFile().deleteOnExit();
        PageManager pm = new PageManager(new FileManager(db.toString()));

        int[] ids = new int[PAGES];
        byte[] blank = new byte[Constants.PAGE_SIZE];
        for (int i = 0; i < PAGES; i++) {
            ids[i] = pm.allocatePage();
            pm.writePage(ids[i], blank);
        }

        System.out.println("共 " + PAGES + " 个数据页；容量取值 " + Arrays.toString(CAPACITIES));
        System.out.println("（命中率越高越好；同一份访问序列喂给三种策略，保证可比）");

        Workload[] workloads = {
            new Workload("顺序扫描", sequential(ids, 3)),
            new Workload("均匀随机", random(ids, 2000, 42)),
            new Workload("热点倾斜 80/20", hotspot(ids, 2000, 42)),
            new Workload("局部循环 窗口60", loop(ids, 2000, 60)),
        };

        for (Workload w : workloads) {
            System.out.println();
            System.out.println("== 负载：" + w.name() + "（访问 " + w.trace().length + " 次）==");
            System.out.printf("%-10s", "策略");
            for (int cap : CAPACITIES) {
                System.out.printf("%12s", "cap=" + cap);
            }
            System.out.println();
            for (String policy : POLICIES) {
                System.out.printf("%-10s", policy);
                for (int cap : CAPACITIES) {
                    double hr = hitRate(pm, policy, cap, w.trace());
                    System.out.printf("%11.1f%%", hr * 100);
                }
                System.out.println();
            }
        }

        belady(pm, ids);
    }

    /** 用指定策略 / 容量把序列跑一遍，返回命中率。 */
    private static double hitRate(PageManager pm, String policy, int capacity, int[] trace) {
        PrintStream real = System.out;
        // 屏蔽 BufferPool 淘汰时的 [EVICT] 打印，避免污染实验输出
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        double rate;
        try {
            BufferPool bp = new BufferPool(pm, capacity, policy);
            for (int pageId : trace) {
                bp.getPage(pageId);
            }
            rate = bp.getStats().hitRate();
        } finally {
            System.setOut(real);
        }
        return rate;
    }

    private static int[] sequential(int[] ids, int rounds) {
        int[] t = new int[ids.length * rounds];
        int k = 0;
        for (int r = 0; r < rounds; r++) {
            for (int id : ids) {
                t[k++] = id;
            }
        }
        return t;
    }

    private static int[] random(int[] ids, int n, int seed) {
        Random rnd = new Random(seed);
        int[] t = new int[n];
        for (int i = 0; i < n; i++) {
            t[i] = ids[rnd.nextInt(ids.length)];
        }
        return t;
    }

    /** 80% 的访问集中在 20% 的热页上。 */
    private static int[] hotspot(int[] ids, int n, int seed) {
        Random rnd = new Random(seed);
        int hot = Math.max(1, ids.length / 5);
        int[] t = new int[n];
        for (int i = 0; i < n; i++) {
            if (rnd.nextDouble() < 0.8) {
                t[i] = ids[rnd.nextInt(hot)];
            } else {
                t[i] = ids[hot + rnd.nextInt(ids.length - hot)];
            }
        }
        return t;
    }

    /** 在一个滑动窗口内随机访问，窗口每次整体前移一格（带局部性）。 */
    private static int[] loop(int[] ids, int n, int window) {
        Random rnd = new Random(7);
        int[] t = new int[n];
        int base = 0;
        for (int i = 0; i < n; i++) {
            t[i] = ids[(base + rnd.nextInt(window)) % ids.length];
            if ((i + 1) % window == 0) {
                base = (base + 1) % ids.length;
            }
        }
        return t;
    }

    /** 经典 Bélády 序列：1 2 3 4 1 2 5 1 2 3 4 5。 */
    private static void belady(PageManager pm, int[] ids) {
        int[] pattern = {0, 1, 2, 3, 0, 1, 4, 0, 1, 2, 3, 4};
        int[] trace = new int[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            trace[i] = ids[pattern[i]];
        }

        System.out.println();
        System.out.println("== Bélády 异常验证（同一序列，容量 3 vs 4）==");
        System.out.printf("%-10s%14s%14s%16s%n", "策略", "cap=3 命中率", "cap=4 命中率", "是否出现异常");
        for (String policy : POLICIES) {
            double h3 = hitRate(pm, policy, 3, trace);
            double h4 = hitRate(pm, policy, 4, trace);
            String flag = h4 < h3 ? "扩容反而变差" : "正常";
            System.out.printf("%-10s%13.1f%%%13.1f%%%16s%n", policy, h3 * 100, h4 * 100, flag);
        }
        System.out.println("说明：LRU 属于栈式算法，扩容必定不退化；");
        System.out.println("      FIFO / CLOCK 不是栈式算法，本序列上扩容反而更差（Bélády 异常）。");
    }
}
