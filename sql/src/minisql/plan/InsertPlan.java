package minisql.plan;

import minisql.ast.Expr;
import minisql.catalog.Table;

import java.util.List;

/**
 * 插入（Insert）：向目标表写入一条记录。叶子节点。
 *
 * <p>{@code columns} 为空表示"不指定列名、按表全部列插入"。</p>
 */
public class InsertPlan extends Plan {

    /** 目标表。 */
    public final Table table;

    /** 指定的列名列表；不指定时为空。 */
    public final List<String> columns;

    /** 要插入的值表达式列表。 */
    public final List<Expr> values;

    public InsertPlan(Table table, List<String> columns, List<Expr> values) {
        super(null);
        this.table = table;
        this.columns = columns;
        this.values = values;
    }
}
