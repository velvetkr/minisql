package minisql.ast;

/**
 * 所有"表达式" AST 节点的共同父类。
 *
 * <p>表达式是能"算出一个值"的东西，比如：</p>
 * <ul>
 *   <li>一个列引用：{@code age}</li>
 *   <li>一个字面量：{@code 18}、{@code 'Alice'}</li>
 *   <li>一个运算：{@code age + 1}、{@code a = 1 AND b = 2}</li>
 * </ul>
 *
 * <p>它有四个子类（见同包下其它文件）：</p>
 * <ul>
 *   <li>{@link ColumnRef}   列引用</li>
 *   <li>{@link Literal}     字面量</li>
 *   <li>{@link BinaryExpr}  二元运算（+、-、AND、OR、比较……）</li>
 *   <li>{@link UnaryExpr}   一元运算（NOT、负号 -）</li>
 * </ul>
 *
 * <p>每个表达式都带着它在源码里的行列位置，供语义分析报错时精确定位。</p>
 */
public abstract class Expr {

    /** 表达式在源码里的行号（从 1 开始）。 */
    public final int line;

    /** 表达式在源码里的列号（从 1 开始）。 */
    public final int column;

    public Expr(int line, int column) {
        this.line = line;
        this.column = column;
    }
}
