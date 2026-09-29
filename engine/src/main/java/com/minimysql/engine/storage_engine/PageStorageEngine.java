package com.minimysql.engine.storage_engine; // 声明当前 Java 文件所属的包。

import minisql.ast.ColumnDef; // 引入当前行使用的外部类型或工具。
import minisql.ast.DataType; // 引入当前行使用的外部类型或工具。
import minisql.catalog.Table; // 引入当前行使用的外部类型或工具。
import storage.BufferPool; // 引入当前行使用的外部类型或工具。
import storage.Constants; // 引入当前行使用的外部类型或工具。
import storage.FileManager; // 引入当前行使用的外部类型或工具。
import storage.PageManager; // 引入当前行使用的外部类型或工具。

import java.io.ByteArrayInputStream; // 引入当前行使用的外部类型或工具。
import java.io.ByteArrayOutputStream; // 引入当前行使用的外部类型或工具。
import java.io.DataInputStream; // 引入当前行使用的外部类型或工具。
import java.io.DataOutputStream; // 引入当前行使用的外部类型或工具。
import java.io.EOFException; // 引入当前行使用的外部类型或工具。
import java.io.IOException; // 引入当前行使用的外部类型或工具。
import java.io.UncheckedIOException; // 引入当前行使用的外部类型或工具。
import java.nio.charset.StandardCharsets; // 引入当前行使用的外部类型或工具。
import java.util.ArrayList; // 引入当前行使用的外部类型或工具。
import java.util.Arrays; // 引入当前行使用的外部类型或工具。
import java.util.Iterator; // 引入当前行使用的外部类型或工具。
import java.util.LinkedHashMap; // 引入当前行使用的外部类型或工具。
import java.util.List; // 引入当前行使用的外部类型或工具。
import java.util.Locale; // 引入当前行使用的外部类型或工具。
import java.util.Map; // 引入当前行使用的外部类型或工具。

/**
 * 基于页式存储模块的记录级存储实现。
 *
 * <p>第 1 页保存表目录元数据，后续每个数据页保存一条记录。这样做虽然简单，
 * 但能完整演示引擎如何把逻辑行转换成页读写操作；记录格式一旦建立后，读写两端
 * 必须始终使用相同的字段顺序和编码方式。</p>
 */
public final class PageStorageEngine implements StorageEngine { // 定义当前文件对外提供的核心类型。
    /** 元数据固定放在第 1 页，第 0 页由页式存储模块保留。 */
    private static final int META_PAGE = 1; // 保存当前类运行期间需要共享的状态或常量。
    /** 用于识别元数据页的文件头。 */
    private static final byte[] META_MAGIC = {'M', 'E', 'T', 'A'}; // 保存当前类运行期间需要共享的状态或常量。
    /** 用于识别行数据页的文件头。 */
    private static final byte[] ROW_MAGIC = {'R', 'O', 'W', '1'}; // 保存当前类运行期间需要共享的状态或常量。

    private final FileManager fileManager; // 保存当前类运行期间需要共享的状态或常量。
    private final PageManager pageManager; // 保存当前类运行期间需要共享的状态或常量。
    private final BufferPool bufferPool; // 保存当前类运行期间需要共享的状态或常量。
    /** 以小写表名为键，保证 SQL 的表名大小写不影响查找。 */
    private final Map<String, TableData> tables = new LinkedHashMap<>(); // 保存当前类运行期间需要共享的状态或常量。

    /** 内存中的表目录项，同时记录该表占用的所有数据页编号。 */
    private static final class TableData { // 保存当前类运行期间需要共享的状态或常量。
        private final String name; // 保存当前类运行期间需要共享的状态或常量。
        private final List<ColumnDef> columns; // 保存当前类运行期间需要共享的状态或常量。
        private final List<Integer> pages = new ArrayList<>(); // 保存当前类运行期间需要共享的状态或常量。

        private TableData(String name, List<ColumnDef> columns) { // 声明当前方法或成员，并限定其访问范围。
            this.name = name; // 执行当前语句，更新状态或调用下层组件。
            this.columns = List.copyOf(columns); // 执行当前语句，更新状态或调用下层组件。
        } // 结束当前代码块。

        private Table asTable() { // 声明当前方法或成员，并限定其访问范围。
            return new Table(name, columns); // 返回当前方法计算出的结果。
        } // 结束当前代码块。
    } // 结束当前代码块。

    /** 打开数据库文件、创建页管理器，并恢复上次保存的目录元数据。 */
    public PageStorageEngine(String filename) { // 声明当前方法或成员，并限定其访问范围。
        this.fileManager = new FileManager(filename); // 执行当前语句，更新状态或调用下层组件。
        this.pageManager = new PageManager(fileManager); // 执行当前语句，更新状态或调用下层组件。
        this.bufferPool = new BufferPool(pageManager, Constants.DEFAULT_BUFFER_SIZE, "LRU"); // 执行当前语句，更新状态或调用下层组件。
        loadMetadata(); // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。

    /** 暴露页管理器，便于测试或需要直接观察页状态的工具使用。 */
    public PageManager pageManager() { // 声明当前方法或成员，并限定其访问范围。
        return pageManager; // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 暴露缓冲池，便于测试验证页面是否正确刷回磁盘。 */
    public BufferPool bufferPool() { // 声明当前方法或成员，并限定其访问范围。
        return bufferPool; // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 创建表目录项，并立即把新的目录写入元数据页。 */
    @Override // 使用注解声明当前类型或方法的框架语义。
    public synchronized void createTable(String tableName, List<ColumnDef> columns) { // 声明当前方法或成员，并限定其访问范围。
        String key = tableName.toLowerCase(Locale.ROOT); // 执行当前语句，更新状态或调用下层组件。
        if (tables.containsKey(key)) { // 根据条件决定是否执行下面的分支。
            throw new IllegalArgumentException("table already exists: " + tableName); // 抛出异常，通知调用方当前操作无法完成。
        } // 结束当前代码块。

        tables.put(key, new TableData(tableName, columns)); // 执行当前语句，更新状态或调用下层组件。
        persistMetadata(); // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。

    /** 将一行按表定义编码后分配到一个新的数据页中。 */
    @Override // 使用注解声明当前类型或方法的框架语义。
    public synchronized void insert(Table table, Row row) { // 声明当前方法或成员，并限定其访问范围。
        TableData tableData = requireTable(table); // 执行当前语句，更新状态或调用下层组件。
        byte[] payload = encodeRow(tableData.columns, row); // 执行当前语句，更新状态或调用下层组件。

        // 4 字节魔数 + 1 字节有效标记 + 4 字节负载长度 + 实际字段数据。
        if (payload.length + 9 > Constants.PAGE_SIZE) { // 根据条件决定是否执行下面的分支。
            throw new IllegalArgumentException("row exceeds page size"); // 抛出异常，通知调用方当前操作无法完成。
        } // 结束当前代码块。

        int pageId = bufferPool.allocatePage(); // 执行当前语句，更新状态或调用下层组件。
        byte[] page = new byte[Constants.PAGE_SIZE]; // 执行当前语句，更新状态或调用下层组件。
        System.arraycopy(ROW_MAGIC, 0, page, 0, ROW_MAGIC.length); // 执行当前语句，更新状态或调用下层组件。
        page[4] = 1; // 执行当前语句，更新状态或调用下层组件。
        writeInt(page, 5, payload.length); // 执行当前语句，更新状态或调用下层组件。
        System.arraycopy(payload, 0, page, 9, payload.length); // 执行当前语句，更新状态或调用下层组件。

        bufferPool.writePage(pageId, page); // 执行当前语句，更新状态或调用下层组件。
        tableData.pages.add(pageId); // 执行当前语句，更新状态或调用下层组件。
        persistMetadata(); // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。

    /** 扫描表的所有数据页，跳过无效页并把有效负载解码成 Row。 */
    @Override // 使用注解声明当前类型或方法的框架语义。
    public synchronized List<Row> scan(Table table) { // 声明当前方法或成员，并限定其访问范围。
        TableData tableData = requireTable(table); // 执行当前语句，更新状态或调用下层组件。
        List<Row> result = new ArrayList<>(); // 执行当前语句，更新状态或调用下层组件。

        for (int pageId : tableData.pages) { // 遍历集合或重复执行当前循环体。
            byte[] page = bufferPool.getPage(pageId); // 执行当前语句，更新状态或调用下层组件。
            if (!isValidRowPage(page)) { // 根据条件决定是否执行下面的分支。
                continue; // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。

            int payloadLength = readInt(page, 5); // 执行当前语句，更新状态或调用下层组件。
            if (payloadLength < 0 || payloadLength > page.length - 9) { // 根据条件决定是否执行下面的分支。
                continue; // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。

            byte[] payload = Arrays.copyOfRange(page, 9, 9 + payloadLength); // 执行当前语句，更新状态或调用下层组件。
            result.add(decodeRow(tableData.columns, payload)); // 执行当前语句，更新状态或调用下层组件。
        } // 结束当前代码块。
        return result; // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 按行内容匹配待删除记录，并释放对应数据页。 */
    @Override // 使用注解声明当前类型或方法的框架语义。
    public synchronized int delete(Table table, List<Row> rows) { // 声明当前方法或成员，并限定其访问范围。
        if (rows.isEmpty()) { // 根据条件决定是否执行下面的分支。
            return 0; // 返回当前方法计算出的结果。
        } // 结束当前代码块。

        TableData tableData = requireTable(table); // 执行当前语句，更新状态或调用下层组件。
        List<Row> remaining = new ArrayList<>(rows); // 执行当前语句，更新状态或调用下层组件。
        int removed = 0; // 执行当前语句，更新状态或调用下层组件。
        Iterator<Integer> pageIterator = tableData.pages.iterator(); // 执行当前语句，更新状态或调用下层组件。

        while (pageIterator.hasNext()) { // 在条件成立期间持续处理循环体。
            int pageId = pageIterator.next(); // 执行当前语句，更新状态或调用下层组件。
            byte[] page = bufferPool.getPage(pageId); // 执行当前语句，更新状态或调用下层组件。
            if (!isValidRowPage(page)) { // 根据条件决定是否执行下面的分支。
                continue; // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。

            int payloadLength = readInt(page, 5); // 执行当前语句，更新状态或调用下层组件。
            if (payloadLength < 0 || payloadLength > page.length - 9) { // 根据条件决定是否执行下面的分支。
                continue; // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。

            Row candidate = decodeRow( // 继续当前代码块的具体处理步骤。
                    tableData.columns, // 继续当前代码块的具体处理步骤。
                    Arrays.copyOfRange(page, 9, 9 + payloadLength)); // 执行当前语句，更新状态或调用下层组件。
            int matchIndex = indexOfRow(remaining, candidate); // 执行当前语句，更新状态或调用下层组件。
            if (matchIndex >= 0) { // 根据条件决定是否执行下面的分支。
                remaining.remove(matchIndex); // 执行当前语句，更新状态或调用下层组件。
                bufferPool.freePage(pageId); // 执行当前语句，更新状态或调用下层组件。
                pageIterator.remove(); // 执行当前语句，更新状态或调用下层组件。
                removed++; // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。
        } // 结束当前代码块。

        if (removed > 0) { // 根据条件决定是否执行下面的分支。
            persistMetadata(); // 执行当前语句，更新状态或调用下层组件。
        } // 结束当前代码块。
        return removed; // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 返回当前已知的表定义，供目录恢复和诊断使用。 */
    @Override // 使用注解声明当前类型或方法的框架语义。
    public synchronized List<Table> tables() { // 声明当前方法或成员，并限定其访问范围。
        return tables.values().stream().map(TableData::asTable).toList(); // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 把持久化目录补充到编译器目录中，已存在的表不会重复创建。 */
    public synchronized void loadInto(minisql.catalog.Catalog catalog) { // 声明当前方法或成员，并限定其访问范围。
        for (TableData tableData : tables.values()) { // 遍历集合或重复执行当前循环体。
            if (!catalog.tableExists(tableData.name)) { // 根据条件决定是否执行下面的分支。
                catalog.createTable(tableData.name, tableData.columns); // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。
        } // 结束当前代码块。
    } // 结束当前代码块。

    /** 查找表的持久化信息；首次遇到未登记表时建立对应目录项。 */
    private TableData requireTable(Table table) { // 声明当前方法或成员，并限定其访问范围。
        if (table == null) { // 根据条件决定是否执行下面的分支。
            throw new IllegalArgumentException("table is null"); // 抛出异常，通知调用方当前操作无法完成。
        } // 结束当前代码块。

        String key = table.name.toLowerCase(Locale.ROOT); // 执行当前语句，更新状态或调用下层组件。
        TableData tableData = tables.get(key); // 执行当前语句，更新状态或调用下层组件。
        if (tableData == null) { // 根据条件决定是否执行下面的分支。
            tableData = new TableData(table.name, table.columns); // 执行当前语句，更新状态或调用下层组件。
            tables.put(key, tableData); // 执行当前语句，更新状态或调用下层组件。
            persistMetadata(); // 执行当前语句，更新状态或调用下层组件。
        } // 结束当前代码块。
        return tableData; // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 从固定的元数据页恢复表名、列定义以及数据页列表。 */
    private void loadMetadata() { // 声明当前方法或成员，并限定其访问范围。
        if (pageManager.numPages() <= META_PAGE) { // 根据条件决定是否执行下面的分支。
            return; // 执行当前语句，更新状态或调用下层组件。
        } // 结束当前代码块。

        byte[] page; // 执行当前语句，更新状态或调用下层组件。
        try { // 开始可能抛出异常的资源或业务操作。
            page = pageManager.readPage(META_PAGE); // 执行当前语句，更新状态或调用下层组件。
        } catch (RuntimeException ignored) { // 继续当前代码块的具体处理步骤。
            return; // 执行当前语句，更新状态或调用下层组件。
        } // 结束当前代码块。

        if (page.length < META_MAGIC.length // 根据条件决定是否执行下面的分支。
                || !Arrays.equals(Arrays.copyOf(page, META_MAGIC.length), META_MAGIC)) { // 继续当前代码块的具体处理步骤。
            return; // 执行当前语句，更新状态或调用下层组件。
        } // 结束当前代码块。

        try (DataInputStream input = new DataInputStream( // 开始可能抛出异常的资源或业务操作。
                new ByteArrayInputStream(page, META_MAGIC.length, page.length - META_MAGIC.length))) { // 继续当前代码块的具体处理步骤。
            int tableCount = input.readInt(); // 执行当前语句，更新状态或调用下层组件。
            if (tableCount < 0 || tableCount > 10_000) { // 根据条件决定是否执行下面的分支。
                return; // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。

            for (int i = 0; i < tableCount; i++) { // 遍历集合或重复执行当前循环体。
                String tableName = readString(input); // 执行当前语句，更新状态或调用下层组件。
                int columnCount = input.readInt(); // 执行当前语句，更新状态或调用下层组件。
                List<ColumnDef> columns = new ArrayList<>(); // 执行当前语句，更新状态或调用下层组件。

                for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) { // 遍历集合或重复执行当前循环体。
                    String columnName = readString(input); // 执行当前语句，更新状态或调用下层组件。
                    int typeOrdinal = input.readByte(); // 执行当前语句，更新状态或调用下层组件。
                    columns.add(new ColumnDef(columnName, DataType.values()[typeOrdinal])); // 执行当前语句，更新状态或调用下层组件。
                } // 结束当前代码块。

                TableData tableData = new TableData(tableName, columns); // 执行当前语句，更新状态或调用下层组件。
                int pageCount = input.readInt(); // 执行当前语句，更新状态或调用下层组件。
                for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) { // 遍历集合或重复执行当前循环体。
                    tableData.pages.add(input.readInt()); // 执行当前语句，更新状态或调用下层组件。
                } // 结束当前代码块。
                tables.put(tableName.toLowerCase(Locale.ROOT), tableData); // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。
        } catch (IOException | RuntimeException ignored) { // 继续当前代码块的具体处理步骤。
            // 元数据损坏时清空内存目录，避免继续使用不完整的表定义。
            tables.clear(); // 执行当前语句，更新状态或调用下层组件。
        } // 结束当前代码块。
    } // 结束当前代码块。

    /** 将当前目录序列化到一个固定大小的元数据页，并刷盘保证重启可恢复。 */
    private void persistMetadata() { // 声明当前方法或成员，并限定其访问范围。
        try { // 开始可能抛出异常的资源或业务操作。
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); // 执行当前语句，更新状态或调用下层组件。
            DataOutputStream output = new DataOutputStream(bytes); // 执行当前语句，更新状态或调用下层组件。
            output.write(META_MAGIC); // 执行当前语句，更新状态或调用下层组件。
            output.writeInt(tables.size()); // 执行当前语句，更新状态或调用下层组件。

            for (TableData tableData : tables.values()) { // 遍历集合或重复执行当前循环体。
                writeString(output, tableData.name); // 执行当前语句，更新状态或调用下层组件。
                output.writeInt(tableData.columns.size()); // 执行当前语句，更新状态或调用下层组件。
                for (ColumnDef column : tableData.columns) { // 遍历集合或重复执行当前循环体。
                    writeString(output, column.name); // 执行当前语句，更新状态或调用下层组件。
                    output.writeByte(column.type.ordinal()); // 执行当前语句，更新状态或调用下层组件。
                } // 结束当前代码块。

                output.writeInt(tableData.pages.size()); // 执行当前语句，更新状态或调用下层组件。
                for (int pageId : tableData.pages) { // 遍历集合或重复执行当前循环体。
                    output.writeInt(pageId); // 执行当前语句，更新状态或调用下层组件。
                } // 结束当前代码块。
            } // 结束当前代码块。
            output.flush(); // 执行当前语句，更新状态或调用下层组件。

            if (bytes.size() > Constants.PAGE_SIZE) { // 根据条件决定是否执行下面的分支。
                throw new IllegalStateException("catalog exceeds one page"); // 抛出异常，通知调用方当前操作无法完成。
            } // 结束当前代码块。

            byte[] page = new byte[Constants.PAGE_SIZE]; // 执行当前语句，更新状态或调用下层组件。
            System.arraycopy(bytes.toByteArray(), 0, page, 0, bytes.size()); // 执行当前语句，更新状态或调用下层组件。
            if (pageManager.numPages() <= META_PAGE) { // 根据条件决定是否执行下面的分支。
                pageManager.allocatePage(); // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。
            bufferPool.writePage(META_PAGE, page); // 执行当前语句，更新状态或调用下层组件。
            bufferPool.flushPage(META_PAGE); // 执行当前语句，更新状态或调用下层组件。
        } catch (IOException exception) { // 继续当前代码块的具体处理步骤。
            throw new UncheckedIOException(exception); // 抛出异常，通知调用方当前操作无法完成。
        } // 结束当前代码块。
    } // 结束当前代码块。

    /** 检查页头和有效标记，过滤被释放或格式不正确的数据页。 */
    private static boolean isValidRowPage(byte[] page) { // 声明当前方法或成员，并限定其访问范围。
        return page.length >= 9 // 返回当前方法计算出的结果。
                && Arrays.equals(Arrays.copyOf(page, ROW_MAGIC.length), ROW_MAGIC) // 继续当前代码块的具体处理步骤。
                && page[4] != 0; // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。

    /** 按列定义顺序编码一行，缺少字段时立即报错。 */
    private static byte[] encodeRow(List<ColumnDef> columns, Row row) { // 声明当前方法或成员，并限定其访问范围。
        try { // 开始可能抛出异常的资源或业务操作。
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); // 执行当前语句，更新状态或调用下层组件。
            DataOutputStream output = new DataOutputStream(bytes); // 执行当前语句，更新状态或调用下层组件。
            for (ColumnDef column : columns) { // 遍历集合或重复执行当前循环体。
                Object value = row.get(column.name); // 执行当前语句，更新状态或调用下层组件。
                if (value == null) { // 根据条件决定是否执行下面的分支。
                    throw new IllegalArgumentException("missing value for " + column.name); // 抛出异常，通知调用方当前操作无法完成。
                } // 结束当前代码块。
                writeValue(output, column.type, value); // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。
            output.flush(); // 执行当前语句，更新状态或调用下层组件。
            return bytes.toByteArray(); // 返回当前方法计算出的结果。
        } catch (IOException exception) { // 继续当前代码块的具体处理步骤。
            throw new UncheckedIOException(exception); // 抛出异常，通知调用方当前操作无法完成。
        } // 结束当前代码块。
    } // 结束当前代码块。

    /** 按列定义顺序解码字段，重新构造保持列顺序的 Row。 */
    private static Row decodeRow(List<ColumnDef> columns, byte[] payload) { // 声明当前方法或成员，并限定其访问范围。
        try { // 开始可能抛出异常的资源或业务操作。
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)); // 执行当前语句，更新状态或调用下层组件。
            Map<String, Object> values = new LinkedHashMap<>(); // 执行当前语句，更新状态或调用下层组件。
            for (ColumnDef column : columns) { // 遍历集合或重复执行当前循环体。
                values.put(column.name, readValue(input, column.type)); // 执行当前语句，更新状态或调用下层组件。
            } // 结束当前代码块。
            return new Row(values); // 返回当前方法计算出的结果。
        } catch (IOException exception) { // 继续当前代码块的具体处理步骤。
            throw new IllegalArgumentException("corrupt row", exception); // 抛出异常，通知调用方当前操作无法完成。
        } // 结束当前代码块。
    } // 结束当前代码块。

    /** 根据字段类型写入固定格式的二进制值。 */
    private static void writeValue(DataOutputStream output, DataType type, Object value) // 声明当前方法或成员，并限定其访问范围。
            throws IOException { // 继续当前代码块的具体处理步骤。
        switch (type) { // 根据枚举或操作符选择对应处理分支。
            case INT -> output.writeInt(((Number) value).intValue()); // 处理当前 switch 分支对应的类型或操作符。
            case FLOAT -> output.writeDouble(((Number) value).doubleValue()); // 处理当前 switch 分支对应的类型或操作符。
            case BOOL -> output.writeBoolean((Boolean) value); // 处理当前 switch 分支对应的类型或操作符。
            case VARCHAR -> writeString(output, String.valueOf(value)); // 处理当前 switch 分支对应的类型或操作符。
        } // 结束当前代码块。
    } // 结束当前代码块。

    /** 根据字段类型读取二进制值。 */
    private static Object readValue(DataInputStream input, DataType type) throws IOException { // 声明当前方法或成员，并限定其访问范围。
        return switch (type) { // 返回当前方法计算出的结果。
            case INT -> input.readInt(); // 处理当前 switch 分支对应的类型或操作符。
            case FLOAT -> input.readDouble(); // 处理当前 switch 分支对应的类型或操作符。
            case BOOL -> input.readBoolean(); // 处理当前 switch 分支对应的类型或操作符。
            case VARCHAR -> readString(input); // 处理当前 switch 分支对应的类型或操作符。
        }; // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。

    /** 使用 UTF-8 写入带长度前缀的字符串，确保中文等字符可持久化。 */
    private static void writeString(DataOutputStream output, String value) throws IOException { // 声明当前方法或成员，并限定其访问范围。
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); // 执行当前语句，更新状态或调用下层组件。
        output.writeInt(bytes.length); // 执行当前语句，更新状态或调用下层组件。
        output.write(bytes); // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。

    /** 读取并校验长度前缀字符串，防止损坏数据导致越界分配。 */
    private static String readString(DataInputStream input) throws IOException { // 声明当前方法或成员，并限定其访问范围。
        int length = input.readInt(); // 执行当前语句，更新状态或调用下层组件。
        if (length < 0 || length > Constants.PAGE_SIZE) { // 根据条件决定是否执行下面的分支。
            throw new IOException("bad string length"); // 抛出异常，通知调用方当前操作无法完成。
        } // 结束当前代码块。

        byte[] bytes = input.readNBytes(length); // 执行当前语句，更新状态或调用下层组件。
        if (bytes.length != length) { // 根据条件决定是否执行下面的分支。
            throw new EOFException(); // 抛出异常，通知调用方当前操作无法完成。
        } // 结束当前代码块。
        return new String(bytes, StandardCharsets.UTF_8); // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 在待删除行列表中查找值完全相同的记录。 */
    private static int indexOfRow(List<Row> rows, Row candidate) { // 声明当前方法或成员，并限定其访问范围。
        for (int index = 0; index < rows.size(); index++) { // 遍历集合或重复执行当前循环体。
            if (rows.get(index).values().equals(candidate.values())) { // 根据条件决定是否执行下面的分支。
                return index; // 返回当前方法计算出的结果。
            } // 结束当前代码块。
        } // 结束当前代码块。
        return -1; // 返回当前方法计算出的结果。
    } // 结束当前代码块。

    /** 以大端序写入 4 字节整数，避免依赖平台字节序。 */
    private static void writeInt(byte[] bytes, int offset, int value) { // 声明当前方法或成员，并限定其访问范围。
        bytes[offset] = (byte) (value >>> 24); // 执行当前语句，更新状态或调用下层组件。
        bytes[offset + 1] = (byte) (value >>> 16); // 执行当前语句，更新状态或调用下层组件。
        bytes[offset + 2] = (byte) (value >>> 8); // 执行当前语句，更新状态或调用下层组件。
        bytes[offset + 3] = (byte) value; // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。

    /** 以大端序读取 4 字节整数，与 writeInt 保持一致。 */
    private static int readInt(byte[] bytes, int offset) { // 声明当前方法或成员，并限定其访问范围。
        return ((bytes[offset] & 0xff) << 24) // 返回当前方法计算出的结果。
                | ((bytes[offset + 1] & 0xff) << 16) // 继续当前代码块的具体处理步骤。
                | ((bytes[offset + 2] & 0xff) << 8) // 继续当前代码块的具体处理步骤。
                | (bytes[offset + 3] & 0xff); // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。

    /** 关闭时先保存目录，再关闭缓冲池和底层文件资源。 */
    @Override // 使用注解声明当前类型或方法的框架语义。
    public synchronized void close() { // 声明当前方法或成员，并限定其访问范围。
        persistMetadata(); // 执行当前语句，更新状态或调用下层组件。
        bufferPool.close(); // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。
} // 结束当前代码块。
