package minisql.ast;

import java.util.List;

/**
 * SELECT 语句的 AST 节点。
 *
 * <p>对应 SQL：{@code SELECT name, age FROM student WHERE age > 18}</p>
 * <ul>
 *   <li>{@code selectList} = [name, age]（或 * 表示所有列）</li>
 *   <li>{@code tableName}  = "student"</li>
 *   <li>{@code where}      = BinaryExpr(&gt;, ColumnRef(age), Literal(18))</li>
 * </ul>
 */
public class SelectStmt extends Statement {

    /** SELECT 后面的每一项（列名，可能带别名），或 * 。 */
    public final List<SelectItem> selectList;

    /** FROM 后面的表名。 */
    public final String tableName;

    /** WHERE 条件表达式；没有 WHERE 时为 null。 */
    public final Expr where;

    public SelectStmt(List<SelectItem> selectList, String tableName, Expr where,
                      int line, int column) {
        super(line, column);
        this.selectList = selectList;
        this.tableName = tableName;
        this.where = where;
    }

    /**
     * SELECT 列表中的一项：要么是 {@code *}，要么是一个列名（可带别名）。
     */
    public static class SelectItem {
        /** 是否为 * （SELECT *）。 */
        public final boolean star;

        /** 列名（当 star 为 false 时有效）。 */
        public final String name;

        /** 别名（AS xxx）；没有别名时为 null。 */
        public final String alias;

        public SelectItem(boolean star, String name, String alias) {
            this.star = star;
            this.name = name;
            this.alias = alias;
        }
    }
}
