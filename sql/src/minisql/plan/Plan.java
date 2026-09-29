package minisql.plan;

/**
 * 逻辑执行计划（Logical Plan）节点的共同父类。
 *
 * <p>逻辑计划是"编译器"与"执行引擎"之间的接口（PPT 第 30 页）：
 * 它把 AST 翻译成一棵"算子（operator）树"，告诉执行引擎"该按什么顺序做哪些操作"。
 * 执行引擎看到这棵树就知道：先扫哪张表 → 过滤哪些行 → 返回哪些列。</p>
 *
 * <p>按 PPT 第 30 页，Plan 只有 5 种节点，本类下分 5 个子类：</p>
 * <ul>
 *   <li>{@link SeqScanPlan}      顺序扫描一张表（叶子）</li>
 *   <li>{@link FilterPlan}       执行 WHERE 条件过滤</li>
 *   <li>{@link ProjectPlan}      返回 SELECT 指定的列</li>
 *   <li>{@link CreateTablePlan}  创建表、注册元数据（叶子）</li>
 *   <li>{@link InsertPlan}       向表写入记录（叶子）</li>
 * </ul>
 *
 * <p>因为一棵计划树里每个节点最多只有一个子节点（单链），
 * 所以这里统一用一个 {@code child} 字段；叶子节点的 child 为 {@code null}。</p>
 */
public abstract class Plan {

    /** 唯一的子节点；叶子节点为 null。 */
    public final Plan child;

    public Plan(Plan child) {
        this.child = child;
    }
}
