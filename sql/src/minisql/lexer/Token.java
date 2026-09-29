package minisql.lexer;

/**
 * 词法分析产出的最小单元：一个 Token（词法记号）。
 *
 * <p>PPT 第 8 页强调 Token 不止有"种别码"，还要带上三个信息：</p>
 * <ol>
 *   <li><b>type</b>   —— 种别（什么类型，供 Parser 判断语法）</li>
 *   <li><b>lexeme</b> —— 字面量（原始文本，供后续保留标识符/常量的实际值）</li>
 *   <li><b>line / column</b> —— 位置（供语法/语义错误精确定位）</li>
 * </ol>
 */
public class Token {

    /** 种别：这个 Token 是什么类型。 */
    public final TokenType type;

    /** 字面量：源码里的原始文本。比如标识符 "student"、数字 "20"、字符串 "Alice"。 */
    public final String lexeme;

    /** 这个 Token 在源码里出现的行号（从 1 开始）。 */
    public final int line;

    /** 这个 Token 在源码里出现的列号（从 1 开始）。 */
    public final int column;

    public Token(TokenType type, String lexeme, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
    }

    /**
     * 方便调试打印的样子，例如：
     * <pre>SELECT(line=1, col=1, "SELECT")</pre>
     */
    @Override
    public String toString() {
        return type + "(line=" + line + ", col=" + column + ", \"" + lexeme + "\")";
    }
}
