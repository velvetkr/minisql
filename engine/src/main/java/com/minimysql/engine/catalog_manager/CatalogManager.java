package com.minimysql.engine.catalog_manager; // 声明目录管理器所在的引擎包。

import minisql.ast.ColumnDef; // 引入编译器提供的列定义类型。
import minisql.catalog.Catalog; // 引入编译器提供的表目录类型。
import minisql.catalog.Table; // 引入编译器提供的表结构类型。
import com.minimysql.engine.storage_engine.PageStorageEngine; // 引入可选的持久化存储实现。

import java.util.List; // 使用 List 保存列定义。

/**
 * 引擎侧的目录门面。
 *
 * <p>目录负责记录表结构；如果配置了页存储引擎，构造时会先恢复持久化目录，
 * 注册新表时也会同步写入存储层。</p>
 */
public final class CatalogManager { // 暴露统一的目录操作入口。
    private final Catalog catalog; // 保存编译器使用的内存目录。
    private final PageStorageEngine storage; // 保存可选的持久化目录连接。

    /** 创建仅使用内存目录的管理器。 */
    public CatalogManager(Catalog catalog) { // 接收调用方已经创建的目录。
        this(catalog, null); // 没有存储连接时只管理内存目录。
    } // 结束当前代码块。

    /** 创建目录管理器，并可选地连接持久化存储。 */
    public CatalogManager(Catalog catalog, PageStorageEngine storage) { // 初始化目录和存储引用。
        this.catalog = java.util.Objects.requireNonNull(catalog); // 拒绝空目录，避免后续调用出现隐式空指针。
        this.storage = storage; // 记录存储引用，允许它为 null。

        // 存储层存在时，先把磁盘中的表结构补充到编译器目录中。
        if (storage != null) { // 只有连接存储时才执行恢复。
            storage.loadInto(catalog); // 将持久化表定义加载到内存目录。
        } // 结束当前代码块。
    } // 结束当前代码块。

    /** 使用新的目录并连接指定的持久化存储。 */
    public CatalogManager(PageStorageEngine storage) { // 提供只传存储对象的便捷构造方法。
        this(new Catalog(), storage); // 创建空目录后立即恢复磁盘元数据。
    } // 结束当前代码块。

    /** 返回编译器使用的目录。 */
    public Catalog catalog() { // 暴露当前目录供会话和执行器使用。
        return catalog; // 返回构造时保存的目录对象。
    } // 结束当前代码块。

    /** 按名称查找表，查找失败时沿用编译器目录的行为。 */
    public Table findTable(String name) { // 接收待查询的表名。
        return catalog.findTable(name); // 委托编译器目录完成大小写和存在性处理。
    } // 结束当前代码块。

    /** 创建表并在配置了存储时同步持久化表结构。 */
    public void registerTable(String name, List<ColumnDef> columns) { // 接收表名和完整列定义。
        catalog.createTable(name, columns); // 先更新编译器内存目录。
        if (storage != null) { // 连接持久化存储时同步写盘。
            storage.createTable(name, columns); // 让存储层建立相同的表目录项。
        } // 结束当前代码块。
    } // 结束当前代码块。
} // 结束当前代码块。
