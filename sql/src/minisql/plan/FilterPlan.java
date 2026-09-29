package minisql.plan;

import minisql.ast.Expr;

/**
 * 过滤（Filter）：对下层输出的每一行，判断 WHERE 条件是否为真，只留下满足的行。
 *
 * <p>{@code predicate} 就是 WHERE 后面的表达式；
 * {@code child} 是被过滤的数据来源（通常是 {@link SeqScanPlan}）。</p>
 */
public class FilterPlan extends Plan {

    /** WHERE 条件表达式。 */
    public final Expr predicate;

    public FilterPlan(Expr predicate, Plan child) {
        super(child);
        this.predicate = predicate;
    }
}
