package minisql.ast;

/**
 * 一个列的定义，出现在 CREATE TABLE 里。
 * 例如 {@code CREATE TABLE student(id INT, name VARCHAR)} 中的 {@code id INT}。
 */
public class ColumnDef {

    /** 列名。 */
    public final String name;

    /** 列的类型。 */
    public final DataType type;

    public ColumnDef(String name, DataType type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String toString() {
        return name + " " + type;
    }
}
