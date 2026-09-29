package minisql.ast;

/**
 * 二元表达式：左右两个子表达式 + 一个运算符。
 *
 * <p>运算符 {@code op} 用字符串表示，取值包括：</p>
 * <ul>
 *   <li>算术：{@code + - * /}</li>
 *   <li>比较：{@code = != < > <= >=}</li>
 *   <li>逻辑：{@code AND OR}</li>
 * </ul>
 *
 * <p>注意：<b>优先级不是在这个类里体现的，而是在 Parser 里体现的</b>。
 * 比如 {@code a = 1 OR b = 2 AND c = 3} 会被 Parser 解析成
 * {@code OR(=(a,1), AND(=(b,2), =(c,3)))}，从树结构上就保证了
 * AND 比 OR 更"贴近叶子"，即 AND 优先级更高（PPT 第 13/18 页）。</p>
 */
public class BinaryExpr extends Expr {

    /** 运算符。 */
    public final String op;

    /** 左子表达式。 */
    public final Expr left;

    /** 右子表达式。 */
    public final Expr right;

    public BinaryExpr(String op, Expr left, Expr right, int line, int column) {
        super(line, column);
        this.op = op;
        this.left = left;
        this.right = right;
    }
}
