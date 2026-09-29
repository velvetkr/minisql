package minisql.plan;

import minisql.ast.ColumnDef;

import java.util.List;

/**
 * 创建表（CreateTable）：创建一张表并注册元数据。叶子节点。
 *
 * <p>对应 SQL {@code CREATE TABLE 表名 (列...)}。语义分析阶段已经把表
 * 注册进了 Catalog，这里只是把这条语句表示成一个"计划节点"，
 * 让整条流水线（PPT 第 42 页要求输出 Plan）对四种语句都有一致的产物。</p>
 */
public class CreateTablePlan extends Plan {

    /** 表名。 */
    public final String tableName;

    /** 列定义。 */
    public final List<ColumnDef> columns;

    public CreateTablePlan(String tableName, List<ColumnDef> columns) {
        super(null);
        this.tableName = tableName;
        this.columns = columns;
    }
}
