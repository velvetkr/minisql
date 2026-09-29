package minisql.ast;

/**
 * 把表达式打印成<b>一行</b>的中缀形式（带必要括号），
 * 供执行计划（{@code Filter[...]}）和优化器打印"优化前/后"对比时使用。
 *
 * <p>和 {@link AstPrinter} 的区别：AstPrinter 是多行的<b>树形</b>，
 * 用于看清 AST 结构；本类是一行的<b>中缀</b>，用于把谓词简洁地写进
 * {@code Filter[age > 18]} 这种括号里（对应 PPT 第 32/34 页的输出样式）。</p>
 *
 * <p>括号按"运算符优先级"省略：子表达式优先级不低于父运算符时不需要括号。
 * 优先级（从低到高）与 {@code Parser} 的分层完全一致：</p>
 * <pre>
 *   OR  <  AND  <  NOT  <  比较(= != < > <= >=)  <  + -  <  * /  <  一元 -  <  原子
 * </pre>
 * 例如 {@code 1 = 1 AND age > 10 + 8} 会原样打印（不添多余括号），
 * 而 {@code NOT (a = 1 AND b = 2)} 里的 AND 比 NOT 低，所以要加括号。
 */
public class ExprPrinter {

    /** 入口：打印成一行。 */
    public static String print(Expr e) {
        return format(e, 0);
    }

    /**
     * 递归格式化。{@code parentPrec} 是父运算符的优先级：
     * 当前节点优先级更低时，要加括号把它"包住"。
     */
    private static String format(Expr e, int parentPrec) {
        // 原子（字面量 / 列引用）：toString 已经是想要的单行样子
        if (e instanceof Literal || e instanceof ColumnRef) {
            return e.toString();
        }

        if (e instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) e;
            int p = (u.op.equals("NOT")) ? precNot : precUnaryMinus;
            String s = (u.op.equals("NOT"))
                    ? "NOT " + format(u.operand, p)
                    : "-" + format(u.operand, p);
            return paren(s, p, parentPrec);
        }

        if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            int p = precBinary(b.op);
            String s = format(b.left, p) + " " + b.op + " " + format(b.right, p);
            return paren(s, p, parentPrec);
        }

        return "?";
    }

    // 优先级常量（数字越大，结合越紧）
    private static final int precNot = 3;
    private static final int precUnaryMinus = 7;

    /** 二元运算符的优先级。 */
    private static int precBinary(String op) {
        switch (op) {
            case "OR":  return 1;
            case "AND": return 2;
            case "=": case "!=": case "<": case ">": case "<=": case ">=": return 4;
            case "+": case "-": return 5;
            case "*": case "/": return 6;
            default:   return 7;
        }
    }

    /** 优先级不够高就加括号。 */
    private static String paren(String s, int prec, int parentPrec) {
        return (prec < parentPrec) ? "(" + s + ")" : s;
    }
}
