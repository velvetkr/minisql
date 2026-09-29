package minisql.plan;

import minisql.catalog.Table;

/**
 * 顺序扫描（SeqScan）：按顺序读出一张表的所有记录。计划树的叶子节点。
 *
 * <p>{@code purpose} 说明这次扫描是给谁用的：</p>
 * <ul>
 *   <li>{@code "SELECT"} —— 普通查询扫描</li>
 *   <li>{@code "DELETE"} —— 删除扫描（扫出来再按 WHERE 过滤掉要删的行）</li>
 * </ul>
 * 这样 DELETE 也能用"SeqScan + Filter"表达，而不必多造一种 Plan 节点。
 */
public class SeqScanPlan extends Plan {

    /** 要扫描的表。 */
    public final Table table;

    /** 扫描用途：SELECT 或 DELETE。 */
    public final String purpose;

    public SeqScanPlan(Table table, String purpose) {
        super(null);
        this.table = table;
        this.purpose = purpose;
    }
}
