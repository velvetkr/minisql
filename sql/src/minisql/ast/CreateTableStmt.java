package minisql.ast;

import java.util.List;

/**
 * CREATE TABLE 语句的 AST 节点。
 *
 * <p>对应 SQL：{@code CREATE TABLE student(id INT, name VARCHAR, age INT)}</p>
 * <ul>
 *   <li>{@code tableName} = "student"</li>
 *   <li>{@code columns}  = [ColumnDef(id,INT), ColumnDef(name,VARCHAR), ColumnDef(age,INT)]</li>
 * </ul>
 */
public class CreateTableStmt extends Statement {

    /** 要创建的表名。 */
    public final String tableName;

    /** 列定义列表（按书写顺序）。 */
    public final List<ColumnDef> columns;

    public CreateTableStmt(String tableName, List<ColumnDef> columns, int line, int column) {
        super(line, column);
        this.tableName = tableName;
        this.columns = columns;
    }
}
