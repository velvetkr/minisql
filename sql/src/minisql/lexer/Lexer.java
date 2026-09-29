package minisql.lexer;

import minisql.error.SqlError;
import minisql.error.SqlError.Phase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 词法分析器（Lexer）：把 SQL 源码字符串，切成一串 Token。
 *
 * <p>它做的事非常"机械"：从左到右一个字符一个字符地读，
 * 每读到一段有意义的文本（一个关键字、一个数字、一个字符串……），
 * 就切出一个 Token，同时记录它出现在第几行第几列。</p>
 *
 * <p>怎么用：</p>
 * <pre>
 *   Lexer lexer = new Lexer("SELECT * FROM student");
 *   List&lt;Token&gt; tokens = lexer.tokenize();
 * </pre>
 *
 * <p>词法阶段要处理的"边界情况"（PPT 第 9 页），本类都覆盖了：</p>
 * <ul>
 *   <li>注释 {@code --} 单行注释</li>
 *   <li>多字符运算符 {@code >=  <=  !=  <>  ==}</li>
 *   <li>字符串 {@code 'Alice'} 以及转义 {@code 'Tom''s book'}</li>
 *   <li>关键字大小写不敏感 {@code select / SELECT / SeLeCt}</li>
 *   <li>非法输入 {@code @}、未闭合字符串、非法数字 → 抛错而不是崩溃</li>
 * </ul>
 */
public class Lexer {

    /** 待分析的源码字符串。 */
    private final String src;

    /** 当前读取到第几个字符（下标，从 0 开始）。 */
    private int pos = 0;

    /** 当前所在行号（从 1 开始）。 */
    private int line = 1;

    /** 当前所在列号（从 1 开始）。 */
    private int col = 1;

    /**
     * 关键字表：把"小写形式"映射到对应的 TokenType。
     * 因为 SQL 关键字大小写不敏感，查找前先把标识符转成小写即可。
     */
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        KEYWORDS.put("select",   TokenType.SELECT);
        KEYWORDS.put("from",     TokenType.FROM);
        KEYWORDS.put("where",    TokenType.WHERE);
        KEYWORDS.put("create",   TokenType.CREATE);
        KEYWORDS.put("table",    TokenType.TABLE);
        KEYWORDS.put("insert",   TokenType.INSERT);
        KEYWORDS.put("into",     TokenType.INTO);
        KEYWORDS.put("values",   TokenType.VALUES);
        KEYWORDS.put("delete",   TokenType.DELETE);
        KEYWORDS.put("and",      TokenType.AND);
        KEYWORDS.put("or",       TokenType.OR);
        KEYWORDS.put("not",      TokenType.NOT);
        KEYWORDS.put("true",     TokenType.TRUE);
        KEYWORDS.put("false",    TokenType.FALSE);
        KEYWORDS.put("int",      TokenType.INT);
        KEYWORDS.put("varchar",  TokenType.VARCHAR);
        KEYWORDS.put("bool",     TokenType.BOOL);
        KEYWORDS.put("float",    TokenType.FLOAT);
        // 扩展关键字（核心阶段暂不解析，但词法上能识别出来）：
        KEYWORDS.put("update",   TokenType.UPDATE);
        KEYWORDS.put("set",      TokenType.SET);
        KEYWORDS.put("order",    TokenType.ORDER);
        KEYWORDS.put("by",       TokenType.BY);
        KEYWORDS.put("group",    TokenType.GROUP);
        KEYWORDS.put("join",     TokenType.JOIN);
        KEYWORDS.put("as",       TokenType.AS);
        KEYWORDS.put("distinct", TokenType.DISTINCT);
        KEYWORDS.put("on",       TokenType.ON);
        KEYWORDS.put("limit",    TokenType.LIMIT);
    }

    public Lexer(String src) {
        this.src = src;
    }

    /**
     * 对整个源码做词法分析，返回所有 Token（最后一个一定是 EOF）。
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token t;
        do {
            t = nextToken();
            tokens.add(t);
        } while (t.type != TokenType.EOF);   // 读到 EOF 就停
        return tokens;
    }

    /**
     * 读取下一个 Token。
     */
    private Token nextToken() {
        skipWhitespaceAndComments();           // 先跳过空白和注释

        int startLine = line;                  // 记录这个 Token 的起始位置
        int startCol = col;

        if (isAtEnd()) {
            return new Token(TokenType.EOF, "", startLine, startCol);
        }

        char c = peek();

        if (isIdentStart(c)) return scanIdentifier(startLine, startCol);
        if (isDigit(c))     return scanNumber(startLine, startCol);
        if (c == '\'')      return scanString(startLine, startCol);
        return scanOperator(startLine, startCol);
    }

    // ------------------------------------------------------------------
    // 下面是一系列"扫描某一种 Token"的方法。
    // ------------------------------------------------------------------

    /** 扫描一个标识符或关键字。 */
    private Token scanIdentifier(int startLine, int startCol) {
        int begin = pos;
        while (isIdentPart(peek())) {
            advance();
        }
        String text = src.substring(begin, pos);       // 原始文本
        String lower = text.toLowerCase();             // 转小写用于查关键字表
        TokenType type = KEYWORDS.getOrDefault(lower, TokenType.IDENT);
        return new Token(type, text, startLine, startCol);
    }

    /** 扫描一个数字字面量，支持整数和小数（如 20、3.14）。 */
    private Token scanNumber(int startLine, int startCol) {
        int begin = pos;
        while (isDigit(peek())) {
            advance();
        }
        // 小数点 + 至少一位数字，才算小数部分
        if (peek() == '.' && isDigit(peekNext())) {
            advance();                       // 吃掉 '.'
            while (isDigit(peek())) {
                advance();
            }
        }
        // 非法数字：数字后面直接跟字母，比如 "123abc"
        if (isIdentStart(peek())) {
            throw error("非法数字字面量：数字后面不能直接跟字母", startLine, startCol);
        }
        String text = src.substring(begin, pos);
        return new Token(TokenType.NUMBER, text, startLine, startCol);
    }

    /** 扫描一个字符串字面量，支持 '' 作为单引号转义。 */
    private Token scanString(int startLine, int startCol) {
        advance();                           // 吃掉开头的单引号 '
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (isAtEnd()) {
                // 读到文件结尾还没闭合 → 未闭合字符串
                throw error("字符串未闭合（缺少结尾的单引号 '）", startLine, startCol);
            }
            char c = advance();
            if (c == '\'') {
                // 遇到单引号：如果是两个连续单引号 ''，表示转义的单引号；
                // 否则就是字符串的结尾。
                if (peek() == '\'') {
                    advance();               // 吃掉第二个 '，把它当成内容里的一个单引号
                    sb.append('\'');
                } else {
                    break;
                }
            } else {
                sb.append(c);
            }
        }
        // 注意：lexeme 存的是"去掉引号的纯内容"，方便语义阶段直接拿到 'Alice' 里的 Alice
        return new Token(TokenType.STRING, sb.toString(), startLine, startCol);
    }

    /** 扫描一个运算符或分隔符。 */
    private Token scanOperator(int startLine, int startCol) {
        char c = advance();
        switch (c) {
            case '=':
                // = 和 == 都当成"等于"
                if (peek() == '=') advance();
                return new Token(TokenType.EQ, "=", startLine, startCol);
            case '!':
                // != 是不等于；单独的 ! 不合法
                if (peek() == '=') { advance(); return new Token(TokenType.NEQ, "!=", startLine, startCol); }
                throw error("非法字符 '!'（你是不是想写 != ？）", startLine, startCol);
            case '<':
                if (peek() == '=') { advance(); return new Token(TokenType.LE, "<=", startLine, startCol); }
                if (peek() == '>') { advance(); return new Token(TokenType.NEQ, "<>", startLine, startCol); }
                return new Token(TokenType.LT, "<", startLine, startCol);
            case '>':
                if (peek() == '=') { advance(); return new Token(TokenType.GE, ">=", startLine, startCol); }
                return new Token(TokenType.GT, ">", startLine, startCol);
            case '*': return new Token(TokenType.STAR, "*", startLine, startCol);
            case '+': return new Token(TokenType.PLUS, "+", startLine, startCol);
            case '-':
                // 两个减号 -- 是单行注释
                if (peek() == '-') { skipLineComment(); return nextToken(); }
                return new Token(TokenType.MINUS, "-", startLine, startCol);
            case '/': return new Token(TokenType.SLASH, "/", startLine, startCol);
            case '(': return new Token(TokenType.LPAREN, "(", startLine, startCol);
            case ')': return new Token(TokenType.RPAREN, ")", startLine, startCol);
            case ',': return new Token(TokenType.COMMA, ",", startLine, startCol);
            case ';': return new Token(TokenType.SEMICOLON, ";", startLine, startCol);
            default:
                throw error("非法字符 '" + c + "'", startLine, startCol);
        }
    }

    // ------------------------------------------------------------------
    // 下面是辅助方法：跳过空白/注释、读取字符。
    // ------------------------------------------------------------------

    /** 跳过空格、制表符、换行，以及 -- 单行注释。 */
    private void skipWhitespaceAndComments() {
        while (!isAtEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                advance();
            } else if (c == '-' && peekNext() == '-') {
                skipLineComment();
            } else {
                break;
            }
        }
    }

    /** 跳过一行 -- 注释（一直读到换行或文件结尾）。 */
    private void skipLineComment() {
        while (!isAtEnd() && peek() != '\n') {
            advance();
        }
    }

    /** 当前字符（若已到结尾，返回 '\0' 作为哨兵）。 */
    private char peek() {
        return isAtEnd() ? '\0' : src.charAt(pos);
    }

    /** 下一个字符（不越过结尾）。 */
    private char peekNext() {
        return (pos + 1 >= src.length()) ? '\0' : src.charAt(pos + 1);
    }

    /**
     * 返回当前字符，并把读取位置向前推进一个字符，
     * 同时维护 line / col（遇到换行则行号 +1、列号归 1）。
     */
    private char advance() {
        char c = src.charAt(pos);
        pos++;
        if (c == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        return c;
    }

    private boolean isAtEnd() {
        return pos >= src.length();
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** 快捷构造一个词法错误。 */
    private SqlError error(String message, int line, int col) {
        return new SqlError(Phase.LEXER, message, line, col);
    }
}
