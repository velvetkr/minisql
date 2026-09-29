package minisql.serialize;

import minisql.ast.BinaryExpr;
import minisql.ast.ColumnDef;
import minisql.ast.ColumnRef;
import minisql.ast.CreateTableStmt;
import minisql.ast.DeleteStmt;
import minisql.ast.Expr;
import minisql.ast.InsertStmt;
import minisql.ast.Literal;
import minisql.ast.SelectStmt;
import minisql.ast.Statement;
import minisql.ast.UnaryExpr;
import minisql.plan.CreateTablePlan;
import minisql.plan.FilterPlan;
import minisql.plan.InsertPlan;
import minisql.plan.Plan;
import minisql.plan.ProjectPlan;
import minisql.plan.SeqScanPlan;

import java.util.List;

/**
 * JSON 序列化器：把 AST 和 Plan 输出成结构明确的 JSON 文本。
 *
 * <p>这是「轻量加分项」，对应 PPT 第 30 页对 Plan 输出形式的要求——</p>
 * <blockquote>树形结构 / JSON / S-expression，任选其一，但结构必须明确。</blockquote>
 *
 * <p>之前 Plan 已经有「树形」（{@link minisql.plan.PlanPrinter}），
 * 这里再补上「JSON」这种机器可读、可喂给前端可视化工具的形式。
 * 两种形式讲的是同一棵树，只是呈现方式不同。</p>
 *
 * <p>设计约定：</p>
 * <ul>
 *   <li>语句（Statement）和计划（Plan）对象用<b>多行缩进</b>输出，便于肉眼核对结构；</li>
 *   <li>表达式（Expr）对象用<b>单行紧凑</b>输出（表达式深度有限，单行更易读）；</li>
 *   <li>每个对象都带一个 {@code "kind"} 字段标明节点类型，字段名即结构。</li>
 * </ul>
 *
 * <p>零第三方依赖：JSON 是纯字符串拼接写出来的，不引入任何 JSON 库。</p>
 */
public final class JsonPrinter {

    /** 私有构造，工具类不允许实例化。 */
    private JsonPrinter() {
    }

    // ==================== 公开入口 ====================

    /** 把一条 SQL 语句的 AST 转成 JSON 字符串。 */
    public static String ofStatement(Statement s) {
        StringBuilder sb = new StringBuilder();
        writeStatement(sb, s, 0);
        return sb.toString();
    }

    /** 把一棵逻辑执行计划树转成 JSON 字符串。 */
    public static String ofPlan(Plan p) {
        StringBuilder sb = new StringBuilder();
        writePlan(sb, p, 0);
        return sb.toString();
    }

    // ==================== 语句 → JSON ====================

    private static void writeStatement(StringBuilder sb, Statement s, int d) {
        String in = indent(d);
        String in1 = indent(d + 1);

        if (s instanceof CreateTableStmt) {
            CreateTableStmt c = (CreateTableStmt) s;
            sb.append("{\n");
            sb.append(in1).append("\"kind\": \"CreateTable\",\n");
            sb.append(in1).append("\"table\": ").append(q(c.tableName)).append(",\n");
            sb.append(in1).append("\"columns\": ").append(columnsJson(c.columns)).append("\n");
            sb.append(in).append("}");

        } else if (s instanceof InsertStmt) {
            InsertStmt is = (InsertStmt) s;
            sb.append("{\n");
            sb.append(in1).append("\"kind\": \"Insert\",\n");
            sb.append(in1).append("\"table\": ").append(q(is.tableName)).append(",\n");
            sb.append(in1).append("\"columns\": ").append(stringsJson(is.columns)).append(",\n");
            sb.append(in1).append("\"values\": ").append(exprsJson(is.values)).append("\n");
            sb.append(in).append("}");

        } else if (s instanceof SelectStmt) {
            SelectStmt sel = (SelectStmt) s;
            sb.append("{\n");
            sb.append(in1).append("\"kind\": \"Select\",\n");
            sb.append(in1).append("\"table\": ").append(q(sel.tableName)).append(",\n");
            sb.append(in1).append("\"select\": ").append(selectJson(sel.selectList)).append(",\n");
            sb.append(in1).append("\"where\": ").append(sel.where == null ? "null" : exprJson(sel.where)).append("\n");
            sb.append(in).append("}");

        } else if (s instanceof DeleteStmt) {
            DeleteStmt del = (DeleteStmt) s;
            sb.append("{\n");
            sb.append(in1).append("\"kind\": \"Delete\",\n");
            sb.append(in1).append("\"table\": ").append(q(del.tableName)).append(",\n");
            sb.append(in1).append("\"where\": ").append(del.where == null ? "null" : exprJson(del.where)).append("\n");
            sb.append(in).append("}");
        }
    }

    // ==================== 计划 → JSON ====================

    private static void writePlan(StringBuilder sb, Plan p, int d) {
        String in = indent(d);
        String in1 = indent(d + 1);

        if (p instanceof SeqScanPlan) {
            SeqScanPlan s = (SeqScanPlan) p;
            sb.append("{\n");
            sb.append(in1).append("\"kind\": \"SeqScan\",\n");
            sb.append(in1).append("\"table\": ").append(q(s.table.name)).append(",\n");
            sb.append(in1).append("\"purpose\": ").append(q(s.purpose)).append("\n");
            sb.append(in).append("}");

        } else if (p instanceof FilterPlan) {
            FilterPlan f = (FilterPlan) p;
            sb.append("{\n");
            sb.append(in1).append("\"kind\": \"Filter\",\n");
            sb.append(in1).append("\"predicate\": ").append(exprJson(f.predicate)).append(",\n");
            sb.append(in1).append("\"child\": ");
            writePlan(sb, f.child, d + 1);
            sb.append("\n").append(in).append("}");

        } else if (p instanceof ProjectPlan) {
            ProjectPlan pr = (ProjectPlan) p;
            sb.append("{\n");
            sb.append(in1).append("\"kind\": \"Project\",\n");
            sb.append(in1).append("\"columns\": ").append(stringsJson(pr.columns)).append(",\n");
            sb.append(in1).append("\"child\": ");
            writePlan(sb, pr.child, d + 1);
            sb.append("\n").append(in).append("}");

        } else if (p instanceof CreateTablePlan) {
            CreateTablePlan c = (CreateTablePlan) p;
            sb.append("{\n");
            sb.append(in1).append("\"kind\": \"CreateTable\",\n");
            sb.append(in1).append("\"table\": ").append(q(c.tableName)).append(",\n");
            sb.append(in1).append("\"columns\": ").append(columnsJson(c.columns)).append("\n");
            sb.append(in).append("}");

        } else if (p instanceof InsertPlan) {
            InsertPlan ip = (InsertPlan) p;
            sb.append("{\n");
            sb.append(in1).append("\"kind\": \"Insert\",\n");
            sb.append(in1).append("\"table\": ").append(q(ip.table.name)).append(",\n");
            sb.append(in1).append("\"columns\": ").append(stringsJson(ip.columns)).append(",\n");
            sb.append(in1).append("\"values\": ").append(exprsJson(ip.values)).append("\n");
            sb.append(in).append("}");
        }
    }

    // ==================== 表达式 → 单行 JSON ====================

    private static String exprJson(Expr e) {
        if (e instanceof ColumnRef) {
            ColumnRef r = (ColumnRef) e;
            return "{\"kind\":\"ColumnRef\",\"name\":" + q(r.name) + "}";

        } else if (e instanceof Literal) {
            Literal l = (Literal) e;
            return "{\"kind\":\"Literal\",\"type\":" + q(l.type.name())
                    + ",\"value\":" + value(l.value) + "}";

        } else if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            return "{\"kind\":\"BinaryExpr\",\"op\":" + q(b.op)
                    + ",\"left\":" + exprJson(b.left)
                    + ",\"right\":" + exprJson(b.right) + "}";

        } else if (e instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) e;
            return "{\"kind\":\"UnaryExpr\",\"op\":" + q(u.op)
                    + ",\"operand\":" + exprJson(u.operand) + "}";
        }

        // 理论上走不到这里（Expr 只有 4 个子类），兜底返回 null
        return "null";
    }

    // ==================== 数组 → 单行 JSON ====================

    /** 列定义列表 → {@code [{"name":"id","type":"INT"}, ...]} */
    private static String columnsJson(List<ColumnDef> cols) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(", ");
            ColumnDef c = cols.get(i);
            sb.append("{\"name\":").append(q(c.name))
                    .append(",\"type\":").append(q(c.type.name())).append("}");
        }
        return sb.append("]").toString();
    }

    /** 字符串列表 → {@code ["name", "age"]} */
    private static String stringsJson(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(q(items.get(i)));
        }
        return sb.append("]").toString();
    }

    /** 表达式列表 → {@code [{...}, {...}]} */
    private static String exprsJson(List<Expr> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(exprJson(items.get(i)));
        }
        return sb.append("]").toString();
    }

    /** SELECT 列表 → {@code ["*"]} 或 {@code ["name", "age"]}（别名写成 {@code "name AS n"}） */
    private static String selectJson(List<SelectStmt.SelectItem> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            SelectStmt.SelectItem it = items.get(i);
            if (it.star) {
                sb.append(q("*"));
            } else if (it.alias != null) {
                sb.append(q(it.name + " AS " + it.alias));
            } else {
                sb.append(q(it.name));
            }
        }
        return sb.append("]").toString();
    }

    // ==================== JSON 基础工具 ====================

    /** 把 Java 值转成 JSON 值（字符串加引号、数字原样、布尔原样）。 */
    private static String value(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean) return v.toString();
        if (v instanceof Number) return v.toString();
        return q(v.toString());
    }

    /** 给字符串加双引号并做 JSON 转义。 */
    private static String q(String s) {
        return "\"" + escape(s) + "\"";
    }

    /** JSON 字符串转义：处理引号、反斜杠、换行、制表符和控制字符。 */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /** 缩进：每层 2 个空格。 */
    private static String indent(int d) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d; i++) sb.append("  ");
        return sb.toString();
    }
}
