package minisql.ast;

/**
 * 把 AST 打印成"带缩进的树形结构"，方便在答辩/演示时
 * 直观看到语法树长什么样、以及运算符优先级是否正确。
 *
 * <p>例如表达式 {@code a = 1 OR b = 2 AND c = 3} 会打印成：</p>
 * <pre>
 *   BinaryExpr(op=OR)
 *   ├─ left: BinaryExpr(op==)
 *   │   ├─ left: ColumnRef(a)
 *   │   └─ right: Literal(1)
 *   └─ right: BinaryExpr(op=AND)
 *       ├─ left: BinaryExpr(op==)
 *       │   ├─ left: ColumnRef(b)
 *       │   └─ right: Literal(2)
 *       └─ right: BinaryExpr(op==)
 *           ├─ left: ColumnRef(c)
 *           └─ right: Literal(3)
 * </pre>
 *
 * <p>从缩进就能看出 AND 在 OR 的右边子树里，说明 AND 优先级更高。</p>
 */
public class AstPrinter {

    /** 打印一条语句的 AST。 */
    public static String print(Statement stmt) {
        StringBuilder sb = new StringBuilder();
        printStatement(stmt, sb, "", "", true);
        return sb.toString();
    }

    /** 打印一个表达式的 AST（用于单独看 WHERE 条件）。 */
    public static String printExpr(Expr expr) {
        StringBuilder sb = new StringBuilder();
        printExpr(expr, sb, "", "", true);
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 语句的打印
    // ------------------------------------------------------------------

    private static void printStatement(Statement s, StringBuilder sb,
                                       String prefix, String label, boolean root) {
        if (s instanceof CreateTableStmt) {
            CreateTableStmt c = (CreateTableStmt) s;
            sb.append("CreateTableStmt\n");
            sb.append(prefix).append("├─ table: ").append(c.tableName).append("\n");
            sb.append(prefix).append("└─ columns:\n");
            for (int i = 0; i < c.columns.size(); i++) {
                boolean last = i == c.columns.size() - 1;
                sb.append(prefix).append("   ").append(last ? "└─ " : "├─ ")
                  .append(c.columns.get(i)).append("\n");
            }
        } else if (s instanceof InsertStmt) {
            InsertStmt ins = (InsertStmt) s;
            sb.append("InsertStmt\n");
            sb.append(prefix).append("├─ table: ").append(ins.tableName).append("\n");
            sb.append(prefix).append("├─ columns: ").append(ins.columns).append("\n");
            sb.append(prefix).append("└─ values:\n");
            for (int i = 0; i < ins.values.size(); i++) {
                boolean last = i == ins.values.size() - 1;
                printExpr(ins.values.get(i), sb, prefix + "   ", last ? "└─ " : "├─ ", false);
            }
        } else if (s instanceof SelectStmt) {
            SelectStmt sel = (SelectStmt) s;
            sb.append("SelectStmt\n");
            sb.append(prefix).append("├─ select: ").append(formatSelectList(sel.selectList)).append("\n");
            sb.append(prefix).append("├─ from: ").append(sel.tableName).append("\n");
            sb.append(prefix).append(sel.where == null ? "└─ where: (无)\n" : "└─ where:\n");
            if (sel.where != null) {
                printExpr(sel.where, sb, prefix + "   ", "", false);
            }
        } else if (s instanceof DeleteStmt) {
            DeleteStmt d = (DeleteStmt) s;
            sb.append("DeleteStmt\n");
            sb.append(prefix).append("├─ table: ").append(d.tableName).append("\n");
            sb.append(prefix).append(d.where == null ? "└─ where: (无)\n" : "└─ where:\n");
            if (d.where != null) {
                printExpr(d.where, sb, prefix + "   ", "", false);
            }
        } else {
            sb.append(s.getClass().getSimpleName()).append("\n");
        }
    }

    private static String formatSelectList(java.util.List<SelectStmt.SelectItem> items) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (SelectStmt.SelectItem it : items) {
            if (it.star) {
                parts.add("*");
            } else if (it.alias != null) {
                parts.add(it.name + " AS " + it.alias);
            } else {
                parts.add(it.name);
            }
        }
        return parts.toString();
    }

    // ------------------------------------------------------------------
    // 表达式的打印
    // ------------------------------------------------------------------

    private static void printExpr(Expr e, StringBuilder sb,
                                  String prefix, String label, boolean root) {
        if (root) {
            // 根节点：直接打印内容，不需要前导标记
            if (e instanceof BinaryExpr) {
                BinaryExpr b = (BinaryExpr) e;
                sb.append("BinaryExpr(op=").append(b.op).append(")\n");
                printExpr(b.left,  sb, "├─ ", "left:  ", false);
                printExpr(b.right, sb, "└─ ", "right: ", false);
            } else if (e instanceof UnaryExpr) {
                UnaryExpr u = (UnaryExpr) e;
                sb.append("UnaryExpr(op=").append(u.op).append(")\n");
                printExpr(u.operand, sb, "└─ ", "operand: ", false);
            } else {
                sb.append(e).append("\n");
            }
            return;
        }

        // 非根节点：打印时带上 label（如 "left: "），再递归处理子节点
        String childPrefix = prefix;
        if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            sb.append(prefix).append(label).append("BinaryExpr(op=").append(b.op).append(")\n");
            String extend = prefix.replace("├─ ", "│  ").replace("└─ ", "   ");
            printExpr(b.left,  sb, extend + "├─ ", "left:  ", false);
            printExpr(b.right, sb, extend + "└─ ", "right: ", false);
        } else if (e instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) e;
            sb.append(prefix).append(label).append("UnaryExpr(op=").append(u.op).append(")\n");
            String extend = prefix.replace("├─ ", "│  ").replace("└─ ", "   ");
            printExpr(u.operand, sb, extend + "└─ ", "operand: ", false);
        } else {
            sb.append(prefix).append(label).append(e).append("\n");
        }
    }
}
