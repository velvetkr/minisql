package minisql.semantic;

import minisql.ast.*;
import minisql.catalog.Catalog;
import minisql.catalog.Table;
import minisql.error.SqlError;
import minisql.error.SqlError.Phase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 语义分析器（SemanticAnalyzer）：在"语法正确"的基础上，检查"语义是否可执行"。
 *
 * <p>它拿着 {@link Catalog}（符号表），逐条语句做 PPT 第 24 页要求的五件事：</p>
 * <ol>
 *   <li><b>表存在性</b>   —— 用到的表是否已经 CREATE 过</li>
 *   <li><b>列存在性</b>   —— 用到的列是否真的存在于那张表</li>
 *   <li><b>名字绑定</b>   —— 把列名绑定到 Catalog 里的具体列定义（{@link #resolveColumn}）</li>
 *   <li><b>类型一致性</b> —— 运算两边的类型能不能做这个运算（{@link #typeOf}）</li>
 *   <li><b>INSERT 匹配</b>—— 列数、顺序、值类型是否一致</li>
 * </ol>
 *
 * <p>所有"语义错误"都用 {@link SqlError}（阶段 {@code Phase.SEMANTIC}）抛出，
 * 带上行/列，供 Main 打印成整齐的 {@code SemanticError at line x, column y: ...}。</p>
 *
 * <p>类型规则（PPT 第 27 页）集中放在本类的一小组辅助方法里，而不是散落各处：</p>
 * <ul>
 *   <li>算术 {@code + - * /} → 两边数值，任一 FLOAT 则结果 FLOAT，否则 INT</li>
 *   <li>比较 {@code = != < > <= >=} → 两边同类可比，结果 BOOL</li>
 *   <li>逻辑 {@code AND OR NOT} → 操作数 BOOL，结果 BOOL</li>
 *   <li>{@code INT + VARCHAR} → 语义错误</li>
 * </ul>
 */
public class SemanticAnalyzer {

    /** 符号表：记录当前已创建了哪些表、每张表的列 schema。 */
    private final Catalog catalog;

    public SemanticAnalyzer(Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 分析一批语句（按书写顺序，因为 CREATE 会注册表、影响后面的语句）。
     * 任一句检查不过就抛 {@link SqlError}，不会继续往下走。
     */
    public void analyze(List<Statement> statements) {
        for (Statement stmt : statements) {
            if (stmt instanceof CreateTableStmt) {
                checkCreate((CreateTableStmt) stmt);
            } else if (stmt instanceof InsertStmt) {
                checkInsert((InsertStmt) stmt);
            } else if (stmt instanceof SelectStmt) {
                checkSelect((SelectStmt) stmt);
            } else if (stmt instanceof DeleteStmt) {
                checkDelete((DeleteStmt) stmt);
            }
        }
    }

    // ------------------------------------------------------------------
    // 各语句的检查
    // ------------------------------------------------------------------

    /** CREATE TABLE：表名不能重复、列名不能重复；通过则注册到 Catalog。 */
    private void checkCreate(CreateTableStmt stmt) {
        if (catalog.tableExists(stmt.tableName)) {
            throw error("表 '" + stmt.tableName + "' 已存在，不能重复创建", stmt.line, stmt.column);
        }

        // 列名去重检查（大小写不敏感，与 Catalog 一致）
        Set<String> seen = new HashSet<>();
        for (ColumnDef col : stmt.columns) {
            String key = col.name.toLowerCase();
            if (!seen.add(key)) {
                throw error("列 '" + col.name + "' 重复定义", stmt.line, stmt.column);
            }
        }

        catalog.createTable(stmt.tableName, stmt.columns);
    }

    /** INSERT：表存在、列存在、值数量匹配、值类型匹配。 */
    private void checkInsert(InsertStmt stmt) {
        Table table = requireTable(stmt.tableName, stmt);

        // 确定"值要放进哪些列"：
        // ① 没写列名 → 按表的全部列，值数量必须 = 列数量
        // ② 写了列名 → 按指定的列，逐个解析、数量一致
        List<ColumnDef> targetCols;
        if (stmt.columns.isEmpty()) {
            if (stmt.values.size() != table.columns.size()) {
                throw error("值数量 " + stmt.values.size() + " 与表的列数量 "
                        + table.columns.size() + " 不匹配", stmt.line, stmt.column);
            }
            targetCols = table.columns;
        } else {
            if (stmt.columns.size() != stmt.values.size()) {
                throw error("列数量 " + stmt.columns.size() + " 与值数量 "
                        + stmt.values.size() + " 不匹配", stmt.line, stmt.column);
            }
            targetCols = new ArrayList<>();
            for (String colName : stmt.columns) {
                targetCols.add(resolveColumn(table, colName, stmt.line, stmt.column));
            }
        }

        // 逐个值做类型检查：值类型必须能赋给对应列类型
        for (int i = 0; i < stmt.values.size(); i++) {
            Expr value = stmt.values.get(i);
            DataType valueType = typeOf(value, table);
            DataType colType = targetCols.get(i).type;
            if (!assignable(valueType, colType)) {
                throw error("第 " + (i + 1) + " 个值的类型 " + valueType
                        + " 与列 '" + targetCols.get(i).name + "' 的类型 " + colType + " 不匹配",
                        value.line, value.column);
            }
        }
    }

    /** SELECT：表存在、SELECT 列表里每个列存在、WHERE 表达式类型合法。 */
    private void checkSelect(SelectStmt stmt) {
        Table table = requireTable(stmt.tableName, stmt);

        for (SelectStmt.SelectItem item : stmt.selectList) {
            if (!item.star) {   // * 不用检查列名
                resolveColumn(table, item.name, stmt.line, stmt.column);
            }
        }

        if (stmt.where != null) {
            DataType t = typeOf(stmt.where, table);   // 先做完整类型检查
            requireBool(t, stmt.where, "WHERE 条件必须是 BOOL 类型");
        }
    }

    /** DELETE：表存在、WHERE 表达式类型合法。 */
    private void checkDelete(DeleteStmt stmt) {
        Table table = requireTable(stmt.tableName, stmt);

        if (stmt.where != null) {
            DataType t = typeOf(stmt.where, table);
            requireBool(t, stmt.where, "WHERE 条件必须是 BOOL 类型");
        }
    }

    // ------------------------------------------------------------------
    // 名字绑定 + 类型推导（PPT 第 40 页现场验收会点这两个函数名）
    // ------------------------------------------------------------------

    /**
     * 名字绑定：把"列名"解析成 Catalog 里的具体列定义。
     * 列不存在就抛语义错误——这正是 PPT 第 26 页"标识符到底指向谁"的落地。
     */
    public ColumnDef resolveColumn(Table table, String columnName, int line, int column) {
        ColumnDef col = table.findColumn(columnName);
        if (col == null) {
            throw error("列 '" + columnName + "' 不存在于表 '" + table.name + "'", line, column);
        }
        return col;
    }

    /**
     * 类型推导 + 类型检查：递归算出表达式的结果类型，
     * 并在过程中检查每一步运算的类型是否合法（不合法就抛错）。
     *
     * @return 表达式的结果类型（供上层做 INSERT 匹配、WHERE 检查等）
     */
    private DataType typeOf(Expr e, Table table) {
        if (e instanceof Literal) {
            return ((Literal) e).type;
        }

        if (e instanceof ColumnRef) {
            ColumnRef c = (ColumnRef) e;
            ColumnDef col = resolveColumn(table, c.name, c.line, c.column);
            return col.type;
        }

        if (e instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) e;
            DataType t = typeOf(u.operand, table);
            if (u.op.equals("NOT")) {
                requireBool(t, u, "NOT 需要 BOOL 类型");
                return DataType.BOOL;
            }
            // 负号 -
            requireNumeric(t, u, "负号 - 需要数值类型");
            return t;
        }

        if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            DataType lt = typeOf(b.left, table);
            DataType rt = typeOf(b.right, table);

            if (isArith(b.op)) {
                requireNumeric(lt, b, "算术运算 " + b.op + " 需要数值类型");
                requireNumeric(rt, b, "算术运算 " + b.op + " 需要数值类型");
                return (lt == DataType.FLOAT || rt == DataType.FLOAT)
                        ? DataType.FLOAT : DataType.INT;
            }

            if (isComparison(b.op)) {
                requireComparable(lt, rt, b, b.op);
                return DataType.BOOL;
            }

            if (b.op.equals("AND") || b.op.equals("OR")) {
                requireBool(lt, b, b.op + " 需要 BOOL 类型");
                requireBool(rt, b, b.op + " 需要 BOOL 类型");
                return DataType.BOOL;
            }

            throw error("未知运算符 '" + b.op + "'", b.line, b.column);
        }

        throw error("未知表达式", e.line, e.column);
    }

    // ------------------------------------------------------------------
    // 类型规则辅助（PPT 第 27 页：集中管理，不散落各处）
    // ------------------------------------------------------------------

    private static boolean isArith(String op) {
        return op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/");
    }

    private static boolean isComparison(String op) {
        return op.equals("=") || op.equals("!=") || op.equals("<")
                || op.equals(">") || op.equals("<=") || op.equals(">=");
    }

    private static boolean isNumeric(DataType t) {
        return t == DataType.INT || t == DataType.FLOAT;
    }

    private void requireNumeric(DataType t, Expr e, String msg) {
        if (!isNumeric(t)) {
            throw error(msg + "，实际是 " + t, e.line, e.column);
        }
    }

    private void requireBool(DataType t, Expr e, String msg) {
        if (t != DataType.BOOL) {
            throw error(msg + "，实际是 " + t, e.line, e.column);
        }
    }

    /** 比较运算的左右类型必须"可比"：数值之间可比，同类型可比。 */
    private void requireComparable(DataType lt, DataType rt, Expr e, String op) {
        if (isNumeric(lt) && isNumeric(rt)) return;   // INT/FLOAT 属于数值，可互比
        if (lt == rt) return;                          // VARCHAR=VARCHAR、BOOL=BOOL
        throw error("比较 " + op + " 左右类型不匹配：" + lt + " 与 " + rt, e.line, e.column);
    }

    /** 值类型能否赋给列类型：数值之间互相兼容，其余严格同类型。 */
    private boolean assignable(DataType valueType, DataType colType) {
        if (valueType == colType) return true;
        return isNumeric(valueType) && isNumeric(colType);
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    /** 要求表存在，返回该表；否则抛语义错误。 */
    private Table requireTable(String name, Statement stmt) {
        Table table = catalog.findTable(name);
        if (table == null) {
            throw error("表 '" + name + "' 不存在", stmt.line, stmt.column);
        }
        return table;
    }

    /** 快捷构造一个语义错误。 */
    private SqlError error(String message, int line, int column) {
        return new SqlError(Phase.SEMANTIC, message, line, column);
    }
}
