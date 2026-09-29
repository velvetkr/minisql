package minisql.catalog;

import minisql.ast.ColumnDef;

import java.util.List;

/**
 * Catalog 里的一张表：表名 + 列的 schema（每列的名字和类型）。
 *
 * <p>这里复用了 {@link ColumnDef}（名字 + 类型）作为"列"的数据结构，
 * 因为一张表的列定义，本质上就是 name + type 两个信息，和 CREATE TABLE
 * 里写的列定义是同一回事。这样就不用再造一个几乎一样的 Column 类了。</p>
 */
public class Table {

    /** 表名。 */
    public final String name;

    /** 列 schema（按定义顺序）。 */
    public final List<ColumnDef> columns;

    public Table(String name, List<ColumnDef> columns) {
        this.name = name;
        this.columns = columns;
    }

    /**
     * 按列名查列定义；找不到返回 null。
     * 列名大小写不敏感（SQL 惯例：未加引号的标识符不区分大小写）。
     */
    public ColumnDef findColumn(String columnName) {
        for (ColumnDef c : columns) {
            if (c.name.equalsIgnoreCase(columnName)) {
                return c;
            }
        }
        return null;
    }

    /** 是否有某列。 */
    public boolean hasColumn(String columnName) {
        return findColumn(columnName) != null;
    }

    /** 列的数量。 */
    public int columnCount() {
        return columns.size();
    }
}
