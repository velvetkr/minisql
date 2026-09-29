package com.minimysql.engine.executor; // 声明当前 Java 文件所属的包。

import com.minimysql.engine.storage_engine.Row; // 引入当前行使用的外部类型或工具。
import com.minimysql.engine.storage_engine.StorageEngine; // 引入当前行使用的外部类型或工具。
import minisql.ast.BinaryExpr; // 引入当前行使用的外部类型或工具。
import minisql.ast.ColumnRef; // 引入当前行使用的外部类型或工具。
import minisql.ast.DataType; // 引入当前行使用的外部类型或工具。
import minisql.ast.Expr; // 引入当前行使用的外部类型或工具。
import minisql.ast.Literal; // 引入当前行使用的外部类型或工具。
import minisql.ast.UnaryExpr; // 引入当前行使用的外部类型或工具。
import minisql.plan.CreateTablePlan; // 引入当前行使用的外部类型或工具。
import minisql.plan.FilterPlan; // 引入当前行使用的外部类型或工具。
import minisql.plan.InsertPlan; // 引入当前行使用的外部类型或工具。
import minisql.plan.Plan; // 引入当前行使用的外部类型或工具。
import minisql.plan.ProjectPlan; // 引入当前行使用的外部类型或工具。
import minisql.plan.SeqScanPlan; // 引入当前行使用的外部类型或工具。

import java.util.ArrayList; // 引入当前行使用的外部类型或工具。
import java.util.LinkedHashMap; // 引入当前行使用的外部类型或工具。
import java.util.List; // 引入当前行使用的外部类型或工具。
import java.util.Map; // 引入当前行使用的外部类型或工具。

/**
 * 执行优化后的计划，并负责计算计划中出现的表达式。
 *
 * <p>执行器不直接操作页编号，而是通过 {@link StorageEngine} 完成记录级读写。
 * 这样，计划执行逻辑可以独立于具体的存储实现。</p>
 */
public final class PlanExecutor { // 定义当前文件对外提供的核心类型。
    private final StorageEngine storage; // 保存当前类运行期间需要共享的状态或常量。

    public PlanExecutor(StorageEngine storage) { // 声明当前方法或成员，并限定其访问范围。
        this.storage = storage; // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。

    /** 根据计划类型分发到建表、插入、删除或查询流程。 */
    public List<Row> execute(Plan plan) { // 声明当前方法或成员，并限定其访问范围。
        if (plan instanceof CreateTablePlan createTablePlan) { // 根据条件决定是否执行下面的分支。
            storage.createTable(createTablePlan.tableName, createTablePlan.columns); // 执行当前语句，更新状态或调用下层组件。
            return List.of(); // 返回当前方法计算出的结果。
        } // 结束当前代码块。

        if (plan instanceof InsertPlan insertPlan) { // 根据条件决定是否执行下面的分支。
            executeInsert(insertPlan); // 执行当前语句，更新状态或调用下层组件。
            return List.of(); // 返回当前方法计算出的结果。
        } // 结束当前代码块。

        // DELETE 计划本身仍由扫描、过滤和投影节点组成，需要先找出匹配行再删除。
        if (isDelete(plan)) { // 根据条件决定是否执行下面的分支。
            SeqScanPlan scanPlan = findScan(plan); // 执行当前语句，更新状态或调用下层组件。
            List<Row> matchingRows = executeRows(plan); // 执行当前语句，更新状态或调用下层组件。
            storage.delete(scanPlan.table, matchingRows); // 执行当前语句，更新状态或调用下层组件。
            return List.of(); // 返回当前方法计算出的结果。
        } // 结束当前代码块。

        return executeRows(plan); // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 执行 INSERT：计算值表达式，并为省略的列填入默认值。 */
    private void executeInsert(InsertPlan plan) { // 声明当前方法或成员，并限定其访问范围。
        Map<String, Object> values = new LinkedHashMap<>(); // 执行当前语句，更新状态或调用下层组件。
        List<String> targetColumns = plan.columns.isEmpty() // 继续当前代码块的具体处理步骤。
                ? plan.table.columns.stream().map(column -> column.name).toList() // 继续当前代码块的具体处理步骤。
                : plan.columns; // 执行当前语句，更新状态或调用下层组件。

        if (targetColumns.size() != plan.values.size()) { // 根据条件决定是否执行下面的分支。
            throw new IllegalArgumentException("column/value count mismatch"); // 抛出异常，通知调用方当前操作无法完成。
        } // 结束当前代码块。
        

        for (int index = 0; index < targetColumns.size(); index++) { // 遍历集合或重复执行当前循环体。
            values.put(targetColumns.get(index), evaluate(plan.values.get(index), null)); // 执行当前语句，更新状态或调用下层组件。
        } // 结束当前代码块。
        for (var column : plan.table.columns) { // 遍历集合或重复执行当前循环体。
            if (!values.containsKey(column.name)) { // 根据条件决定是否执行下面的分支。
                values.put(column.name, defaultValue(column.type)); // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。
        } // 结束当前代码块。
        storage.insert(plan.table, new Row(values)); // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。

    /** 递归执行扫描、过滤和投影节点，返回中间或最终行集合。 */
    private List<Row> executeRows(Plan plan) { // 声明当前方法或成员，并限定其访问范围。
        if (plan instanceof SeqScanPlan scanPlan) { // 根据条件决定是否执行下面的分支。
            return storage.scan(scanPlan.table); // 返回当前方法计算出的结果。
        } // 结束当前代码块。

        if (plan instanceof FilterPlan filterPlan) { // 根据条件决定是否执行下面的分支。
            List<Row> inputRows = executeRows(filterPlan.child); // 执行当前语句，更新状态或调用下层组件。
            List<Row> filteredRows = new ArrayList<>(); // 执行当前语句，更新状态或调用下层组件。
            for (Row row : inputRows) { // 遍历集合或重复执行当前循环体。
                if (Boolean.TRUE.equals(evaluate(filterPlan.predicate, row))) { // 根据条件决定是否执行下面的分支。
                    filteredRows.add(row); // 执行当前语句，更新状态或调用下层组件。
                } // 结束当前代码块。
            } // 结束当前代码块。
            return filteredRows; // 返回当前方法计算出的结果。
        } // 结束当前代码块。

        if (plan instanceof ProjectPlan projectPlan) { // 根据条件决定是否执行下面的分支。
            List<Row> inputRows = executeRows(projectPlan.child); // 执行当前语句，更新状态或调用下层组件。
            List<Row> projectedRows = new ArrayList<>(); // 执行当前语句，更新状态或调用下层组件。
            for (Row row : inputRows) { // 遍历集合或重复执行当前循环体。
                Map<String, Object> projectedValues = new LinkedHashMap<>(); // 执行当前语句，更新状态或调用下层组件。
                for (String column : projectPlan.columns) { // 遍历集合或重复执行当前循环体。
                    projectedValues.put(column, row.get(column)); // 执行当前语句，更新状态或调用下层组件。
                } // 结束当前代码块。
                projectedRows.add(new Row(projectedValues)); // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。
            return projectedRows; // 返回当前方法计算出的结果。
        } // 结束当前代码块。

        // 当前计划类型没有产生行（例如未知的根节点）时返回空结果。
        return List.of(); // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 通过扫描节点的 purpose 标记识别 DELETE 计划。 */
    private boolean isDelete(Plan plan) { // 声明当前方法或成员，并限定其访问范围。
        SeqScanPlan scanPlan = findScan(plan); // 执行当前语句，更新状态或调用下层组件。
        return scanPlan != null && "DELETE".equalsIgnoreCase(scanPlan.purpose); // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 沿计划树向下查找扫描节点，删除操作需要从中取得目标表。 */
    private SeqScanPlan findScan(Plan plan) { // 声明当前方法或成员，并限定其访问范围。
        Plan current = plan; // 执行当前语句，更新状态或调用下层组件。
        while (current != null) { // 在条件成立期间持续处理循环体。
            if (current instanceof SeqScanPlan scanPlan) { // 根据条件决定是否执行下面的分支。
                return scanPlan; // 返回当前方法计算出的结果。
            } // 结束当前代码块。
            current = current.child; // 执行当前语句，更新状态或调用下层组件。
        } // 结束当前代码块。
        return null; // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 递归计算字面量、列引用、一元表达式和二元表达式。 */
    private Object evaluate(Expr expression, Row row) { // 声明当前方法或成员，并限定其访问范围。
        if (expression instanceof Literal literal) { // 根据条件决定是否执行下面的分支。
            return literal.value; // 返回当前方法计算出的结果。
        } // 结束当前代码块。

        if (expression instanceof ColumnRef columnRef) { // 根据条件决定是否执行下面的分支。
            if (row == null) { // 根据条件决定是否执行下面的分支。
                throw new IllegalArgumentException( // 抛出异常，通知调用方当前操作无法完成。
                        "column reference in value expression: " + columnRef.name); // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。
            return row.get(columnRef.name); // 返回当前方法计算出的结果。
        } // 结束当前代码块。

        if (expression instanceof UnaryExpr unaryExpr) { // 根据条件决定是否执行下面的分支。
            Object operand = evaluate(unaryExpr.operand, row); // 执行当前语句，更新状态或调用下层组件。
            if (unaryExpr.op.equals("NOT")) { // 根据条件决定是否执行下面的分支。
                return !asBool(operand); // 返回当前方法计算出的结果。
            } // 结束当前代码块。
            if (operand instanceof Integer integer) { // 根据条件决定是否执行下面的分支。
                return -integer; // 返回当前方法计算出的结果。
            } // 结束当前代码块。
            return -((Number) operand).doubleValue(); // 返回当前方法计算出的结果。
        } // 结束当前代码块。

        BinaryExpr binaryExpr = (BinaryExpr) expression; // 执行当前语句，更新状态或调用下层组件。
        if (binaryExpr.op.equals("AND")) { // 根据条件决定是否执行下面的分支。
            return asBool(evaluate(binaryExpr.left, row)) // 返回当前方法计算出的结果。
                    && asBool(evaluate(binaryExpr.right, row)); // 执行当前语句，更新状态或调用下层组件。
        } // 结束当前代码块。
        if (binaryExpr.op.equals("OR")) { // 根据条件决定是否执行下面的分支。
            return asBool(evaluate(binaryExpr.left, row)) // 返回当前方法计算出的结果。
                    || asBool(evaluate(binaryExpr.right, row)); // 执行当前语句，更新状态或调用下层组件。
        } // 结束当前代码块。

        Object leftValue = evaluate(binaryExpr.left, row); // 执行当前语句，更新状态或调用下层组件。
        Object rightValue = evaluate(binaryExpr.right, row); // 执行当前语句，更新状态或调用下层组件。
        if (binaryExpr.op.equals("+") || binaryExpr.op.equals("-") // 根据条件决定是否执行下面的分支。
                || binaryExpr.op.equals("*") || binaryExpr.op.equals("/")) { // 继续当前代码块的具体处理步骤。
            return arithmetic(binaryExpr.op, leftValue, rightValue); // 返回当前方法计算出的结果。
        } // 结束当前代码块。
        return compare(binaryExpr.op, leftValue, rightValue); // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 执行数值运算；只要任一操作数是浮点数，结果就保留为 double。 */
    private Object arithmetic(String operator, Object left, Object right) { // 声明当前方法或成员，并限定其访问范围。
        boolean floatingPoint = left instanceof Double || right instanceof Double; // 执行当前语句，更新状态或调用下层组件。
        double leftNumber = ((Number) left).doubleValue(); // 执行当前语句，更新状态或调用下层组件。
        double rightNumber = ((Number) right).doubleValue(); // 执行当前语句，更新状态或调用下层组件。
        if (operator.equals("/") && rightNumber == 0) { // 根据条件决定是否执行下面的分支。
            throw new ArithmeticException("division by zero"); // 抛出异常，通知调用方当前操作无法完成。
        } // 结束当前代码块。

        double result = switch (operator) { // 继续当前代码块的具体处理步骤。
            case "+" -> leftNumber + rightNumber; // 处理当前 switch 分支对应的类型或操作符。
            case "-" -> leftNumber - rightNumber; // 处理当前 switch 分支对应的类型或操作符。
            case "*" -> leftNumber * rightNumber; // 处理当前 switch 分支对应的类型或操作符。
            default -> leftNumber / rightNumber; // 处理当前 switch 分支对应的类型或操作符。
        }; // 执行当前语句，更新状态或调用下层组件。
        return floatingPoint ? result : (int) result; // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 对数字、字符串或布尔值执行比较，并把结果转换为布尔值。 */
    private boolean compare(String operator, Object left, Object right) { // 声明当前方法或成员，并限定其访问范围。
        int comparison; // 执行当前语句，更新状态或调用下层组件。
        if (left instanceof Number && right instanceof Number) { // 根据条件决定是否执行下面的分支。
            comparison = Double.compare( // 继续当前代码块的具体处理步骤。
                    ((Number) left).doubleValue(), ((Number) right).doubleValue()); // 执行当前语句，更新状态或调用下层组件。
        } else if (left instanceof String && right instanceof String) { // 继续当前代码块的具体处理步骤。
            comparison = ((String) left).compareTo((String) right); // 执行当前语句，更新状态或调用下层组件。
        } else if (left instanceof Boolean && right instanceof Boolean) { // 继续当前代码块的具体处理步骤。
            comparison = Boolean.compare((Boolean) left, (Boolean) right); // 执行当前语句，更新状态或调用下层组件。
        } else { // 继续当前代码块的具体处理步骤。
            throw new IllegalArgumentException("incomparable values"); // 抛出异常，通知调用方当前操作无法完成。
        } // 结束当前代码块。

        return switch (operator) { // 返回当前方法计算出的结果。
            case "=" -> comparison == 0; // 处理当前 switch 分支对应的类型或操作符。
            case "!=" -> comparison != 0; // 处理当前 switch 分支对应的类型或操作符。
            case "<" -> comparison < 0; // 处理当前 switch 分支对应的类型或操作符。
            case ">" -> comparison > 0; // 处理当前 switch 分支对应的类型或操作符。
            case "<=" -> comparison <= 0; // 处理当前 switch 分支对应的类型或操作符。
            case ">=" -> comparison >= 0; // 处理当前 switch 分支对应的类型或操作符。
            default -> throw new IllegalArgumentException("unknown operator " + operator); // 处理当前 switch 分支对应的类型或操作符。
        }; // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。

    /** 把表达式结果校验为布尔值，避免静默接受错误类型。 */
    private boolean asBool(Object value) { // 声明当前方法或成员，并限定其访问范围。
        if (!(value instanceof Boolean)) { // 根据条件决定是否执行下面的分支。
            throw new IllegalArgumentException("expected BOOL"); // 抛出异常，通知调用方当前操作无法完成。
        } // 结束当前代码块。
        return (Boolean) value; // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 为 INSERT 中省略的列提供与字段类型匹配的默认值。 */
    private Object defaultValue(DataType type) { // 声明当前方法或成员，并限定其访问范围。
        return switch (type) { // 返回当前方法计算出的结果。
            case INT -> 0; // 处理当前 switch 分支对应的类型或操作符。
            case FLOAT -> 0.0; // 处理当前 switch 分支对应的类型或操作符。
            case VARCHAR -> ""; // 处理当前 switch 分支对应的类型或操作符。
            case BOOL -> false; // 处理当前 switch 分支对应的类型或操作符。
        }; // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。
} // 结束当前代码块。
