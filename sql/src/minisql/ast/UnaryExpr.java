package minisql.ast;

/**
 * 一元表达式：一个运算符 + 一个子表达式。
 *
 * <p>运算符 {@code op} 取值：</p>
 * <ul>
 *   <li>{@code NOT} —— 逻辑非，例如 {@code NOT a = 1}</li>
 *   <li>{@code -}   —— 负号，例如 {@code -age}</li>
 * </ul>
 */
public class UnaryExpr extends Expr {

    /** 运算符：NOT 或 -。 */
    public final String op;

    /** 被作用的子表达式。 */
    public final Expr operand;

    public UnaryExpr(String op, Expr operand, int line, int column) {
        super(line, column);
        this.op = op;
        this.operand = operand;
    }
}
