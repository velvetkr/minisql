package minisql.optimizer;

import minisql.ast.BinaryExpr;
import minisql.ast.DataType;
import minisql.ast.Expr;
import minisql.ast.Literal;
import minisql.ast.UnaryExpr;
import minisql.plan.FilterPlan;
import minisql.plan.Plan;
import minisql.plan.ProjectPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则式优化器（RBO）：对逻辑计划做"结构等价变换"，让它更高效但语义不变。
 *
 * <p>它按固定顺序应用 PPT 第 33 页列的几条规则，每条规则都是"局部改写"：</p>
 * <ol>
 *   <li><b>常量折叠</b> —— {@code 10 + 8} 这类纯常量的式子直接算成 {@code 18}</li>
 *   <li><b>布尔化简</b> —— {@code TRUE AND x → x}、{@code TRUE OR x → TRUE} 等短路</li>
 *   <li><b>投影裁剪</b> —— Project 只保留真正需要的列（去重）</li>
 *   <li><b>谓词下推</b> —— 把 Filter 尽量推到靠近 SeqScan 的位置</li>
 *   <li><b>冗余节点消除</b> —— 删掉谓词恒为 TRUE 的 Filter 等无用节点</li>
 * </ol>
 *
 * <p>优化前后都是合法的 Plan，打印出来能直观看到"结构变化"（PPT 第 34 页示例）：</p>
 * <pre>
 *   SELECT name FROM student WHERE 1=1 AND age>10+8;
 *   优化前  Project[name] → Filter[1 = 1 AND age > 10 + 8] → SeqScan[student]
 *   优化后  Project[name] → Filter[age > 18] → SeqScan[student]
 * </pre>
 */
public class Optimizer {

    /** 入口：对计划做整套优化，返回优化后的计划。 */
    public Plan optimize(Plan plan) {
        Plan p = plan;
        p = foldAndSimplify(p);     // ① 常量折叠 + ② 布尔化简（作用于 Filter 谓词）
        p = eliminateRedundant(p);  // ⑤ 冗余节点消除（删恒真 Filter）
        p = pushDownPredicates(p);  // ④ 谓词下推
        p = pruneProjection(p);     // ③ 投影裁剪
        return p;
    }

    // ------------------------------------------------------------------
    // ① 常量折叠 + ② 布尔化简
    // ------------------------------------------------------------------

    /** 遍历计划，把每个 Filter 的谓词先折叠常量、再化简布尔。 */
    private Plan foldAndSimplify(Plan p) {
        if (p instanceof FilterPlan) {
            FilterPlan f = (FilterPlan) p;
            Plan child = foldAndSimplify(f.child);
            Expr opt = simplifyBooleans(foldConstants(f.predicate));
            return new FilterPlan(opt, child);
        }
        if (p instanceof ProjectPlan) {
            ProjectPlan proj = (ProjectPlan) p;
            return new ProjectPlan(proj.columns, foldAndSimplify(proj.child));
        }
        return p;   // 叶子（SeqScan / CreateTable / Insert）原样返回
    }

    /**
     * 常量折叠：递归遍历表达式，遇到"纯常量"的子式就直接算出结果。
     * 例如 {@code 10 + 8 → 18}、{@code 1 = 1 → TRUE}。
     * 无法安全计算（如除零）就保留原样，不折叠。
     */
    private Expr foldConstants(Expr e) {
        if (e instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) e;
            Expr inner = foldConstants(u.operand);
            if (inner instanceof Literal) {
                Literal folded = evalUnary(u.op, (Literal) inner, u.line, u.column);
                if (folded != null) return folded;
            }
            return new UnaryExpr(u.op, inner, u.line, u.column);
        }

        if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            Expr l = foldConstants(b.left);
            Expr r = foldConstants(b.right);
            if (l instanceof Literal && r instanceof Literal) {
                Literal folded = evalBinary(b.op, (Literal) l, (Literal) r, b.line, b.column);
                if (folded != null) return folded;
            }
            return new BinaryExpr(b.op, l, r, b.line, b.column);
        }

        return e;   // Literal / ColumnRef 原样返回
    }

    /**
     * 布尔化简：{@code TRUE AND x → x}、{@code FALSE AND x → FALSE}、
     * {@code TRUE OR x → TRUE}、{@code FALSE OR x → x}、{@code NOT TRUE → FALSE}。
     */
    private Expr simplifyBooleans(Expr e) {
        if (e instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) e;
            Expr inner = simplifyBooleans(u.operand);
            if (u.op.equals("NOT") && isBool(inner)) {
                return new Literal(!boolVal(inner), DataType.BOOL, u.line, u.column);
            }
            return new UnaryExpr(u.op, inner, u.line, u.column);
        }

        if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            Expr l = simplifyBooleans(b.left);
            Expr r = simplifyBooleans(b.right);

            if (b.op.equals("AND")) {
                if (isTrue(l))  return r;
                if (isFalse(l)) return new Literal(false, DataType.BOOL, b.line, b.column);
                if (isTrue(r))  return l;
                if (isFalse(r)) return new Literal(false, DataType.BOOL, b.line, b.column);
            } else if (b.op.equals("OR")) {
                if (isTrue(l))  return new Literal(true, DataType.BOOL, b.line, b.column);
                if (isFalse(l)) return r;
                if (isTrue(r))  return new Literal(true, DataType.BOOL, b.line, b.column);
                if (isFalse(r)) return l;
            }

            return new BinaryExpr(b.op, l, r, b.line, b.column);
        }

        return e;
    }

    // ------------------------------------------------------------------
    // ⑤ 冗余节点消除
    // ------------------------------------------------------------------

    /** 删掉谓词恒为 TRUE 的 Filter（对结果没有任何过滤作用）。 */
    private Plan eliminateRedundant(Plan p) {
        if (p instanceof FilterPlan) {
            FilterPlan f = (FilterPlan) p;
            Plan child = eliminateRedundant(f.child);
            if (isTrue(f.predicate)) {
                return child;   // 恒真 Filter：直接越过它，用 child 顶替
            }
            return new FilterPlan(f.predicate, child);
        }
        if (p instanceof ProjectPlan) {
            ProjectPlan proj = (ProjectPlan) p;
            return new ProjectPlan(proj.columns, eliminateRedundant(proj.child));
        }
        return p;
    }

    // ------------------------------------------------------------------
    // ④ 谓词下推
    // ------------------------------------------------------------------

    /**
     * 谓词下推：把 Filter 尽量推到靠近 SeqScan 的位置。
     * 本项目的计划里 Filter 本就在 Project 之下、SeqScan 之上（已是最下位置）；
     * 这条规则处理"Filter 落在 Project 之上"的情形，把它下穿到 Project 之下：
     * <pre>
     *   Filter(pred, Project(cols, child))  →  Project(cols, Filter(pred, child))
     * </pre>
     */
    private Plan pushDownPredicates(Plan p) {
        if (p instanceof FilterPlan) {
            FilterPlan f = (FilterPlan) p;
            if (f.child instanceof ProjectPlan) {
                ProjectPlan proj = (ProjectPlan) f.child;
                Plan newFilter = new FilterPlan(f.predicate, proj.child);
                return new ProjectPlan(proj.columns, newFilter);
            }
            return new FilterPlan(f.predicate, pushDownPredicates(f.child));
        }
        if (p instanceof ProjectPlan) {
            ProjectPlan proj = (ProjectPlan) p;
            return new ProjectPlan(proj.columns, pushDownPredicates(proj.child));
        }
        return p;
    }

    // ------------------------------------------------------------------
    // ③ 投影裁剪
    // ------------------------------------------------------------------

    /** 投影裁剪：去掉 Project 里重复的列名（保持首次出现顺序）。 */
    private Plan pruneProjection(Plan p) {
        if (p instanceof ProjectPlan) {
            ProjectPlan proj = (ProjectPlan) p;
            List<String> dedup = new ArrayList<>();
            for (String c : proj.columns) {
                if (!dedup.contains(c)) {
                    dedup.add(c);
                }
            }
            return new ProjectPlan(dedup, pruneProjection(proj.child));
        }
        if (p instanceof FilterPlan) {
            FilterPlan f = (FilterPlan) p;
            return new FilterPlan(f.predicate, pruneProjection(f.child));
        }
        return p;
    }

    // ------------------------------------------------------------------
    // 求值 + 判断（供上面各规则调用）
    // ------------------------------------------------------------------

    /** 计算一个二元常量表达式的结果；无法计算（如除零）返回 null 表示不折叠。 */
    private Literal evalBinary(String op, Literal l, Literal r, int line, int col) {
        Object lv = l.value, rv = r.value;

        if (isArith(op)) {
            boolean floatOp = (l.type == DataType.FLOAT || r.type == DataType.FLOAT);
            if (floatOp) {
                double a = num(lv), b = num(rv);
                if (op.equals("/") && b == 0.0) return null;
                double res = arith(op, a, b);
                return new Literal(res, DataType.FLOAT, line, col);
            } else {
                int a = (Integer) lv, b = (Integer) rv;
                if (op.equals("/") && b == 0) return null;
                int res = arithInt(op, a, b);
                return new Literal(res, DataType.INT, line, col);
            }
        }

        if (isComparison(op)) {
            Boolean res = compare(op, lv, rv);
            return (res == null) ? null : new Literal(res, DataType.BOOL, line, col);
        }

        if (op.equals("AND") || op.equals("OR")) {
            boolean a = (Boolean) lv, b = (Boolean) rv;
            boolean res = op.equals("AND") ? (a && b) : (a || b);
            return new Literal(res, DataType.BOOL, line, col);
        }

        return null;
    }

    /** 计算一元常量表达式：NOT 和负号 -。 */
    private Literal evalUnary(String op, Literal lit, int line, int col) {
        if (op.equals("NOT")) {
            if (lit.type != DataType.BOOL) return null;
            return new Literal(!((Boolean) lit.value), DataType.BOOL, line, col);
        }
        if (lit.type == DataType.INT) {
            return new Literal(-((Integer) lit.value), DataType.INT, line, col);
        }
        if (lit.type == DataType.FLOAT) {
            return new Literal(-((Double) lit.value), DataType.FLOAT, line, col);
        }
        return null;
    }

    /** 数值的 double 算术。 */
    private double arith(String op, double a, double b) {
        switch (op) {
            case "+": return a + b;
            case "-": return a - b;
            case "*": return a * b;
            default:  return a / b;
        }
    }

    /** 整数的 int 算术（除法为整除）。 */
    private int arithInt(String op, int a, int b) {
        switch (op) {
            case "+": return a + b;
            case "-": return a - b;
            case "*": return a * b;
            default:  return a / b;
        }
    }

    /** 比较两个常量值；不支持的情况返回 null。 */
    private Boolean compare(String op, Object lv, Object rv) {
        if (lv instanceof Number && rv instanceof Number) {
            double a = num(lv), b = num(rv);
            return cmp(op, a, b);
        }
        if (lv instanceof String && rv instanceof String) {
            int c = ((String) lv).compareTo((String) rv);
            return cmp(op, c, 0);
        }
        if (lv instanceof Boolean && rv instanceof Boolean) {
            boolean a = (Boolean) lv, b = (Boolean) rv;
            if (op.equals("="))  return a == b;
            if (op.equals("!=")) return a != b;
            return null;   // 布尔之间不做 < > 之类比较
        }
        return null;
    }

    /** 把 op 应用到 double 比较上。 */
    private Boolean cmp(String op, double a, double b) {
        switch (op) {
            case "=":  return a == b;
            case "!=": return a != b;
            case "<":  return a < b;
            case ">":  return a > b;
            case "<=": return a <= b;
            case ">=": return a >= b;
            default:   return null;
        }
    }

    private double num(Object o) {
        return o instanceof Integer ? ((Integer) o).doubleValue() : (Double) o;
    }

    private boolean isBool(Expr e) {
        return e instanceof Literal && ((Literal) e).type == DataType.BOOL;
    }

    private boolean isTrue(Expr e) {
        return isBool(e) && (Boolean) ((Literal) e).value;
    }

    private boolean isFalse(Expr e) {
        return isBool(e) && !(Boolean) ((Literal) e).value;
    }

    private boolean boolVal(Expr e) {
        return (Boolean) ((Literal) e).value;
    }

    private static boolean isArith(String op) {
        return op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/");
    }

    private static boolean isComparison(String op) {
        return op.equals("=") || op.equals("!=") || op.equals("<")
                || op.equals(">") || op.equals("<=") || op.equals(">=");
    }
}
