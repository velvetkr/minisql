package com.minimysql.engine.storage_engine; // 声明当前 Java 文件所属的包。

import minisql.ast.ColumnDef; // 引入当前行使用的外部类型或工具。
import minisql.catalog.Table; // 引入当前行使用的外部类型或工具。

import java.util.List; // 引入当前行使用的外部类型或工具。

/**
 * 引擎使用的记录级存储接口。
 *
 * <p>执行器只依赖这些抽象操作，不关心记录最终落在内存、文件还是页式存储中，
 * 这样可以把 SQL 执行逻辑与具体存储实现解耦。</p>
 */
public interface StorageEngine extends AutoCloseable { // 定义当前文件对外提供的核心类型。
    /** 创建表结构；重复表名应由实现抛出异常。 */
    void createTable(String tableName, List<ColumnDef> columns); // 执行当前语句，更新状态或调用下层组件。

    /** 向指定表追加一条记录。 */
    void insert(Table table, Row row); // 执行当前语句，更新状态或调用下层组件。

    /** 扫描指定表并返回当前可见的全部记录。 */
    List<Row> scan(Table table); // 执行当前语句，更新状态或调用下层组件。

    /** 删除给定记录，返回实际删除的行数。 */
    int delete(Table table, List<Row> rows); // 执行当前语句，更新状态或调用下层组件。

    /** 返回已恢复的表定义；不支持持久化的实现默认返回空列表。 */
    default List<Table> tables() { // 处理当前 switch 分支对应的类型或操作符。
        return List.of(); // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 内存实现没有资源需要释放，因此默认什么也不做。 */
    @Override // 使用注解声明当前类型或方法的框架语义。
    default void close() { // 处理当前 switch 分支对应的类型或操作符。
    } // 结束当前代码块。
} // 结束当前代码块。
