package minisql.ast;

/**
 * 所有 SQL 语句 AST 节点的共同父类。
 *
 * <p>它本身不包含业务字段，只是作为"类型标签"，
 * 让 {@code List<Statement>} 能统一装下 CREATE / INSERT / SELECT / DELETE 四种语句。
 * 这种"用抽象父类统一不同子类"的做法，Java 里叫<b>多态</b>。</p>
 *
 * <p>每条语句都带着它在源码里的行列位置（取的是表名出现的位置），
 * 供语义分析报"表不存在/表已存在"时定位。</p>
 */
public abstract class Statement {

    /** 语句在源码里的行号（从 1 开始）。 */
    public final int line;

    /** 语句在源码里的列号（从 1 开始）。 */
    public final int column;

    public Statement(int line, int column) {
        this.line = line;
        this.column = column;
    }
}
