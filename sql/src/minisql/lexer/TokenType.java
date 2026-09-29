package minisql.lexer;

/**
 * Token 的"种别"：一个 Token 到底是什么东西。
 *
 * <p>词法分析器（Lexer）的任务，就是把一串字符归类成这些类型之一。
 * 后续 Parser 只需要看 TokenType 就能判断语法，不用再关心字符细节。</p>
 *
 * <p>举例：字符 "SELECT" 会被归类为 {@link #SELECT}，
 * 字符 "student" 会被归类为 {@link #IDENT}（标识符，因为不是关键字）。</p>
 */
public enum TokenType {

    // ---------- 关键字 ----------
    // 注意：关键字是"大小写不敏感"的，SELECT / select / SeLeCt 都是同一个 SELECT。
    SELECT, FROM, WHERE, CREATE, TABLE, INSERT, INTO, VALUES, DELETE,
    AND, OR, NOT, TRUE, FALSE,               // 逻辑运算和布尔字面量
    INT, VARCHAR, BOOL, FLOAT,               // 数据类型关键字
    // 下面是扩展能力会用到的关键字，先声明好，核心阶段暂不解析：
    UPDATE, SET, ORDER, BY, GROUP, JOIN, AS, DISTINCT, ON, LIMIT,

    // ---------- 标识符 & 字面量 ----------
    IDENT,     // 标识符：表名、列名等，比如 student、age
    NUMBER,    // 数字字面量：比如 20、3.14
    STRING,    // 字符串字面量：比如 'Alice'

    // ---------- 运算符 ----------
    STAR,      // *
    EQ,        // =   （也接受 ==）
    NEQ,       // !=  或  <>
    LT,        // <
    GT,        // >
    LE,        // <=
    GE,        // >=
    PLUS,      // +
    MINUS,     // -
    SLASH,     // /

    // ---------- 分隔符 ----------
    LPAREN,    // (
    RPAREN,    // )
    COMMA,     // ,
    SEMICOLON, // ;

    // ---------- 特殊 ----------
    EOF        // 输入结束标记（不是真实字符，是 Lexer 人为加的终点）
}
