package minisql.error;

/**
 * 编译器统一错误类型。
 *
 * <p>整个编译器里所有"输入不合法"的情况，都通过抛出一个 SqlError 来表达，
 * 而不是让程序直接崩溃。这样上层（Main）可以捕获它，并打印出
 * 整齐的、带"阶段 + 行 + 列 + 原因"的错误信息。</p>
 *
 * <p>PPT 验收目标里明确要求：非法输入不崩溃，并且能区分
 * "词法 / 语法 / 语义" 三类错误、准确定位。这个类就是干这件事的。</p>
 *
 * 使用示例：
 * <pre>
 *   throw new SqlError(Phase.LEXER, "非法字符 '@'", line, column);
 * </pre>
 */
public class SqlError extends RuntimeException {

    /** 错误阶段：词法、语法、还是语义。 */
    public enum Phase {
        LEXER,    // 词法分析阶段：字符本身不合法（比如出现 @）
        PARSER,   // 语法分析阶段：Token 顺序不符合文法（比如缺分号）
        SEMANTIC  // 语义分析阶段：语法对但语义错（比如表不存在）
    }

    /** 这个错误属于哪个阶段。 */
    public final Phase phase;

    /** 错误发生的行号（从 1 开始）。 */
    public final int line;

    /** 错误发生的列号（从 1 开始）。 */
    public final int column;

    /**
     * 构造一个编译错误。
     *
     * @param phase   错误阶段
     * @param message 错误原因（人类可读的描述）
     * @param line    行号
     * @param column  列号
     */
    public SqlError(Phase phase, String message, int line, int column) {
        super(message);          // 把 message 存到父类 RuntimeException 里
        this.phase = phase;
        this.line = line;
        this.column = column;
    }

    /**
     * 把阶段枚举翻译成 PPT 里的错误名前缀。
     * 例如 Phase.PARSER 会得到 "SyntaxError"。
     */
    private String phaseLabel() {
        switch (phase) {
            case LEXER:    return "LexerError";
            case PARSER:   return "SyntaxError";
            case SEMANTIC: return "SemanticError";
            default:       return "Error";
        }
    }

    /**
     * 最终打印出来的样子，例如：
     * <pre>SyntaxError at line 3, column 19: unexpected token ','</pre>
     */
    @Override
    public String toString() {
        return phaseLabel() + " at line " + line + ", column " + column
                + ": " + getMessage();
    }
}
