package minisql.ast;

/**
 * DELETE 语句的 AST 节点。
 *
 * <p>对应 SQL：{@code DELETE FROM student WHERE age < 18}</p>
 * <ul>
 *   <li>{@code tableName} = "student"</li>
 *   <li>{@code where}     = 条件表达式；没有 WHERE 时为 null</li>
 * </ul>
 */
public class DeleteStmt extends Statement {

    public final String tableName;

    /** WHERE 条件；没有时为 null。 */
    public final Expr where;

    public DeleteStmt(String tableName, Expr where, int line, int column) {
        super(line, column);
        this.tableName = tableName;
        this.where = where;
    }
}
