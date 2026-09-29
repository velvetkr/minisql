package minisql.ast;

/**
 * 字面量表达式：一个写死在 SQL 里的常量值。
 *
 * <p>例如 {@code 20}（INT）、{@code 3.14}（FLOAT）、
 * {@code 'Alice'}（VARCHAR）、{@code TRUE}（BOOL）。</p>
 *
 * <p>{@code value} 的实际 Java 类型由 {@code type} 决定：</p>
 * <ul>
 *   <li>INT     → Integer</li>
 *   <li>FLOAT   → Double</li>
 *   <li>VARCHAR → String</li>
 *   <li>BOOL    → Boolean</li>
 * </ul>
 */
public class Literal extends Expr {

    /** 字面量的值（Java 对象）。 */
    public final Object value;

    /** 字面量的类型。 */
    public final DataType type;

    public Literal(Object value, DataType type, int line, int column) {
        super(line, column);
        this.value = value;
        this.type = type;
    }

    @Override
    public String toString() {
        // 字符串类型加引号显示，其它类型直接转字符串
        if (type == DataType.VARCHAR) {
            return "'" + value + "'";
        }
        return String.valueOf(value);
    }
}
