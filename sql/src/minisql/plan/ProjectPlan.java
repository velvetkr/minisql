package minisql.plan;

import java.util.List;

/**
 * 投影（Project）：只保留 SELECT 列表里指定的列，丢弃其它列。
 *
 * <p>{@code columns} 是要返回的列名列表（{@code SELECT *} 时展开成表的所有列名）。</p>
 */
public class ProjectPlan extends Plan {

    /** 要返回的列名列表。 */
    public final List<String> columns;

    public ProjectPlan(List<String> columns, Plan child) {
        super(child);
        this.columns = columns;
    }
}
