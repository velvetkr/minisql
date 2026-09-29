package minisql.catalog;

import minisql.ast.ColumnDef;
import minisql.ast.DataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog：编译器的"符号表"，存放当前已经创建了哪些表、每张表有哪些列。
 *
 * <p>编译原理里的"符号表（Symbol Table）"负责记录名字 → 信息 的映射；
 * 在 SQL 编译器里，它对应的就是 Catalog（模式目录），见 PPT 第 21 页。</p>
 *
 * <p>它只负责"存"和"查"，<b>不做错误检查</b>（不抛异常）——
 * 检查由语义分析器 SemanticAnalyzer 负责，那里才有行/列信息可以定位。</p>
 *
 * <p>表名大小写不敏感：内部统一用小写当 key。</p>
 */
public class Catalog {

    /** 表名(小写) → Table。用 LinkedHashMap 保证遍历顺序 = 创建顺序，输出稳定。 */
    private final Map<String, Table> tables = new LinkedHashMap<>();

    /** 注册一张表。 */
    public void createTable(String name, List<ColumnDef> columns) {
        tables.put(name.toLowerCase(), new Table(name, columns));
    }

    /** 表是否存在。 */
    public boolean tableExists(String name) {
        return tables.containsKey(name.toLowerCase());
    }

    /** 按名字找表；不存在返回 null。 */
    public Table findTable(String name) {
        return tables.get(name.toLowerCase());
    }

    /** 查某张表里的某列；表或列不存在返回 null。 */
    public ColumnDef findColumn(Table table, String columnName) {
        if (table == null) return null;
        return table.findColumn(columnName);
    }

    /** 查某张表里某列的类型；不存在返回 null。 */
    public DataType getType(Table table, String columnName) {
        ColumnDef c = findColumn(table, columnName);
        return c == null ? null : c.type;
    }

    /** 返回所有表（按创建顺序）。 */
    public List<Table> allTables() {
        return new ArrayList<>(tables.values());
    }
}
