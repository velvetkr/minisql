package minisql.ast;

import java.util.List;

/**
 * INSERT 语句的 AST 节点。
 *
 * <p>支持两种写法：</p>
 * <ul>
 *   <li>{@code INSERT INTO student VALUES (1, 'Alice', 20)}  —— 不指定列名</li>
 *   <li>{@code INSERT INTO student(id, name) VALUES (1, 'Alice')}  —— 指定列名</li>
 * </ul>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code tableName} —— 目标表名</li>
 *   <li>{@code columns}   —— 指定的列名列表；不指定时为空列表</li>
 *   <li>{@code values}    —— 要插入的值表达式列表</li>
 * </ul>
 */
public class InsertStmt extends Statement {

    public final String tableName;

    /** 指定的列名；不指定时为空。 */
    public final List<String> columns;

    /** 值表达式列表。 */
    public final List<Expr> values;

    public InsertStmt(String tableName, List<String> columns, List<Expr> values,
                      int line, int column) {
        super(line, column);
        this.tableName = tableName;
        this.columns = columns;
        this.values = values;
    }
}
