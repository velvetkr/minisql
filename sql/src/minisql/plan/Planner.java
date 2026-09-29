package minisql.plan;

import minisql.ast.ColumnDef;
import minisql.ast.CreateTableStmt;
import minisql.ast.DeleteStmt;
import minisql.ast.InsertStmt;
import minisql.ast.SelectStmt;
import minisql.ast.Statement;
import minisql.catalog.Catalog;
import minisql.catalog.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * 计划生成器（Planner）：把 AST 翻译成逻辑执行计划树（PPT 第 31 页）。
 *
 * <p>核心方法 {@link #buildPlan} 是 PPT 第 40 页现场验收会随机点名的函数之一。</p>
 *
 * <p>转换规则（PPT 第 31 页）——每种语句对应一棵计划树：</p>
 * <ul>
 *   <li>{@code SELECT ... WHERE ...} → {@code Project(选列) → Filter(条件) → SeqScan(表)}</li>
 *   <li>{@code INSERT INTO ...}      → {@code Insert(表)}</li>
 *   <li>{@code DELETE FROM ... WHERE ...} → {@code Filter(条件) → SeqScan(表, DELETE)}</li>
 *   <li>{@code CREATE TABLE ...}     → {@code CreateTable(表)}</li>
 * </ul>
 *
 * <p>注意：Planner 假设语义分析已经跑过（表都已在 Catalog 里），
 * 所以这里 {@code findTable} 一定能找到；真正的"表不存在"检查由 SemanticAnalyzer 负责。</p>
 */
public class Planner {

    private final Catalog catalog;

    public Planner(Catalog catalog) {
        this.catalog = catalog;
    }

    /** 把一批语句各自转成计划（保持顺序）。 */
    public List<Plan> plan(List<Statement> statements) {
        List<Plan> plans = new ArrayList<>();
        for (Statement stmt : statements) {
            plans.add(buildPlan(stmt));
        }
        return plans;
    }

    /** 单条语句 → 计划树。 */
    public Plan buildPlan(Statement stmt) {
        if (stmt instanceof SelectStmt)      return buildSelect((SelectStmt) stmt);
        if (stmt instanceof InsertStmt)      return buildInsert((InsertStmt) stmt);
        if (stmt instanceof DeleteStmt)      return buildDelete((DeleteStmt) stmt);
        if (stmt instanceof CreateTableStmt) return buildCreate((CreateTableStmt) stmt);
        throw new IllegalStateException("未知语句类型：" + stmt.getClass().getSimpleName());
    }

    /** SELECT → Project → (Filter) → SeqScan。 */
    private Plan buildSelect(SelectStmt stmt) {
        Table table = catalog.findTable(stmt.tableName);

        // 最底层：扫描表
        Plan plan = new SeqScanPlan(table, "SELECT");
        // 中间：WHERE 过滤（没有 WHERE 就跳过这层）
        if (stmt.where != null) {
            plan = new FilterPlan(stmt.where, plan);
        }
        // 最顶层：投影（SELECT * 展开成全部列名）
        plan = new ProjectPlan(expandSelectList(stmt, table), plan);
        return plan;
    }

    /** 把 SELECT 列表解析成"要返回的列名"：* 展开为全部列，其余用列名。 */
    private List<String> expandSelectList(SelectStmt stmt, Table table) {
        List<String> cols = new ArrayList<>();
        for (SelectStmt.SelectItem item : stmt.selectList) {
            if (item.star) {
                for (ColumnDef c : table.columns) {
                    cols.add(c.name);
                }
            } else {
                cols.add(item.name);
            }
        }
        return cols;
    }

    /** INSERT → Insert 叶子。 */
    private Plan buildInsert(InsertStmt stmt) {
        Table table = catalog.findTable(stmt.tableName);
        return new InsertPlan(table, stmt.columns, stmt.values);
    }

    /** DELETE → (Filter) → SeqScan(DELETE)。 */
    private Plan buildDelete(DeleteStmt stmt) {
        Table table = catalog.findTable(stmt.tableName);
        Plan plan = new SeqScanPlan(table, "DELETE");
        if (stmt.where != null) {
            plan = new FilterPlan(stmt.where, plan);
        }
        return plan;
    }

    /** CREATE TABLE → CreateTable 叶子。 */
    private Plan buildCreate(CreateTableStmt stmt) {
        return new CreateTablePlan(stmt.tableName, stmt.columns);
    }
}
