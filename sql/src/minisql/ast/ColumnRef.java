package minisql.ast;

/**
 * 列引用表达式：指代某个列的名字。
 *
 * <p>例如 WHERE 里的 {@code age} 就是一个 ColumnRef。
 * 它到底指向哪张表的哪一列，要等到<b>语义分析</b>阶段查 Catalog 才能确定。</p>
 */
public class ColumnRef extends Expr {

    /** 列名。 */
    public final String name;

    public ColumnRef(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
