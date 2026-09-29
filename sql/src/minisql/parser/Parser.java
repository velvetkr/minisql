package minisql.parser;

import minisql.ast.*;
import minisql.error.SqlError;
import minisql.error.SqlError.Phase;
import minisql.lexer.Token;
import minisql.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * 语法分析器（Parser）：把 Token 流，按照文法拼成 AST（抽象语法树）。
 *
 * <p>本实现采用<b>递归下降（recursive descent）</b>方法：
 * 文法里的每一个"非终结符"（如 statement、expr、or_expr……），
 * 都对应这里的一个 Java 方法。方法之间互相调用，正好对应文法的"递归"结构。</p>
 *
 * <p>为什么能用递归下降？因为它要求文法不能有<b>左递归</b>
 * （否则会无限递归调用自己，见 PPT 第 14 页）。
 * 表达式部分我们按"运算符优先级从低到高"分层写方法，天然避免了左递归：</p>
 *
 * <pre>
 *   or_expr        -> and_expr (OR and_expr)*         优先级最低
 *   and_expr       -> not_expr (AND not_expr)*
 *   not_expr       -> NOT not_expr | comparison
 *   comparison     -> additive (比较符 additive)?
 *   additive       -> multiplicative ((+|-） multiplicative)*
 *   multiplicative -> unary ((*|/) unary)*
 *   unary          -> - unary | primary              优先级最高
 * </pre>
 *
 * <p>这个分层正好实现了 PPT 第 13 页要求的优先级：NOT &gt; 比较 &gt; AND &gt; OR。</p>
 */
public class Parser {

    /** 待分析的 Token 列表（末尾一定带 EOF）。 */
    private final List<Token> tokens;

    /** 当前读到的位置（下标）。 */
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * 解析整段 SQL，返回所有语句的 AST 列表。
     * 支持多条语句用分号隔开。
     */
    public List<Statement> parse() {
        List<Statement> statements = new ArrayList<>();
        while (!isAtEnd()) {
            // 允许语句之间有多余的分号
            while (match(TokenType.SEMICOLON)) {
                // 跳过空语句
            }
            if (isAtEnd()) break;
            statements.add(parseStatement());
        }
        return statements;
    }

    // ------------------------------------------------------------------
    // 语句层
    // ------------------------------------------------------------------

    /** 根据第一个 Token 判断是哪类语句，然后调用对应的解析方法。 */
    private Statement parseStatement() {
        if (match(TokenType.CREATE)) return parseCreateTable();
        if (match(TokenType.INSERT)) return parseInsert();
        if (match(TokenType.SELECT)) return parseSelect();
        if (match(TokenType.DELETE)) return parseDelete();
        throw error("无法识别的语句，期望 CREATE / INSERT / SELECT / DELETE", peek());
    }

    /** 解析：CREATE TABLE 表名 (列名 类型, 列名 类型, ...) */
    private CreateTableStmt parseCreateTable() {
        consume(TokenType.TABLE, "CREATE 后面期望 TABLE");

        Token tableTok = consume(TokenType.IDENT, "期望表名");
        String tableName = tableTok.lexeme;

        consume(TokenType.LPAREN, "期望 '('");

        List<ColumnDef> columns = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                String colName = consume(TokenType.IDENT, "期望列名").lexeme;
                DataType type = parseType();
                columns.add(new ColumnDef(colName, type));
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "期望 ')' 结束列定义");
        consume(TokenType.SEMICOLON, "期望 ';' 结束语句");

        return new CreateTableStmt(tableName, columns, tableTok.line, tableTok.column);
    }

    /** 把类型关键字映射成 DataType。 */
    private DataType parseType() {
        if (match(TokenType.INT))     return DataType.INT;
        if (match(TokenType.FLOAT))   return DataType.FLOAT;
        if (match(TokenType.VARCHAR)) return DataType.VARCHAR;
        if (match(TokenType.BOOL))    return DataType.BOOL;
        throw error("期望数据类型（INT / FLOAT / VARCHAR / BOOL）", peek());
    }

    /** 解析：INSERT INTO 表名 (列,...)? VALUES (值,...) */
    private InsertStmt parseInsert() {
        consume(TokenType.INTO, "INSERT 后面期望 INTO");
        Token tableTok = consume(TokenType.IDENT, "期望表名");
        String tableName = tableTok.lexeme;

        // 可选的列名列表
        List<String> columns = new ArrayList<>();
        if (match(TokenType.LPAREN)) {
            if (!check(TokenType.RPAREN)) {
                do {
                    columns.add(consume(TokenType.IDENT, "期望列名").lexeme);
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "期望 ')'");
        }

        consume(TokenType.VALUES, "期望 VALUES");

        // 值列表
        consume(TokenType.LPAREN, "VALUES 后面期望 '('");
        List<Expr> values = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                values.add(parseExpr());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "期望 ')' 结束值列表");
        consume(TokenType.SEMICOLON, "期望 ';' 结束语句");

        return new InsertStmt(tableName, columns, values, tableTok.line, tableTok.column);
    }

    /** 解析：SELECT 列表 FROM 表名 (WHERE 表达式)? */
    private SelectStmt parseSelect() {
        List<SelectStmt.SelectItem> items = new ArrayList<>();

        if (match(TokenType.STAR)) {
            items.add(new SelectStmt.SelectItem(true, null, null));
        } else {
            do {
                String name = consume(TokenType.IDENT, "期望列名或 *").lexeme;
                String alias = null;
                if (match(TokenType.AS)) {
                    alias = consume(TokenType.IDENT, "AS 后面期望别名").lexeme;
                }
                items.add(new SelectStmt.SelectItem(false, name, alias));
            } while (match(TokenType.COMMA));
        }

        consume(TokenType.FROM, "期望 FROM");
        Token tableTok = consume(TokenType.IDENT, "FROM 后面期望表名");
        String tableName = tableTok.lexeme;

        Expr where = null;
        if (match(TokenType.WHERE)) {
            where = parseExpr();
        }
        consume(TokenType.SEMICOLON, "期望 ';' 结束语句");

        return new SelectStmt(items, tableName, where, tableTok.line, tableTok.column);
    }

    /** 解析：DELETE FROM 表名 (WHERE 表达式)? */
    private DeleteStmt parseDelete() {
        consume(TokenType.FROM, "DELETE 后面期望 FROM");
        Token tableTok = consume(TokenType.IDENT, "期望表名");
        String tableName = tableTok.lexeme;

        Expr where = null;
        if (match(TokenType.WHERE)) {
            where = parseExpr();
        }
        consume(TokenType.SEMICOLON, "期望 ';' 结束语句");

        return new DeleteStmt(tableName, where, tableTok.line, tableTok.column);
    }

    // ------------------------------------------------------------------
    // 表达式层（按优先级从低到高，一层一个方法）
    // ------------------------------------------------------------------

    /** 最外层入口：解析整个表达式。 */
    private Expr parseExpr() {
        return parseOr();
    }

    /** or_expr -> and_expr (OR and_expr)*  （优先级最低） */
    private Expr parseOr() {
        Expr left = parseAnd();
        while (match(TokenType.OR)) {
            Token op = previous();
            Expr right = parseAnd();
            left = new BinaryExpr("OR", left, right, op.line, op.column);
        }
        return left;
    }

    /** and_expr -> not_expr (AND not_expr)* */
    private Expr parseAnd() {
        Expr left = parseNot();
        while (match(TokenType.AND)) {
            Token op = previous();
            Expr right = parseNot();
            left = new BinaryExpr("AND", left, right, op.line, op.column);
        }
        return left;
    }

    /** not_expr -> NOT not_expr | comparison （NOT 优先级最高） */
    private Expr parseNot() {
        if (match(TokenType.NOT)) {
            Token op = previous();
            return new UnaryExpr("NOT", parseNot(), op.line, op.column);
        }
        return parseComparison();
    }

    /** comparison -> additive (比较符 additive)? */
    private Expr parseComparison() {
        Expr left = parseAdditive();
        if (isComparisonOp(peek().type)) {
            Token op = advance();
            Expr right = parseAdditive();
            return new BinaryExpr(op.lexeme, left, right, op.line, op.column);
        }
        return left;
    }

    /** additive -> multiplicative ((+|-) multiplicative)* */
    private Expr parseAdditive() {
        Expr left = parseMultiplicative();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token op = advance();
            Expr right = parseMultiplicative();
            left = new BinaryExpr(op.lexeme, left, right, op.line, op.column);
        }
        return left;
    }

    /** multiplicative -> unary ((*|/) unary)* */
    private Expr parseMultiplicative() {
        Expr left = parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            Token op = advance();
            Expr right = parseUnary();
            left = new BinaryExpr(op.lexeme, left, right, op.line, op.column);
        }
        return left;
    }

    /** unary -> - unary | primary */
    private Expr parseUnary() {
        if (match(TokenType.MINUS)) {
            Token op = previous();
            return new UnaryExpr("-", parseUnary(), op.line, op.column);
        }
        return parsePrimary();
    }

    /** primary -> 数字 | 字符串 | TRUE/FALSE | 标识符 | ( expr ) */
    private Expr parsePrimary() {
        if (match(TokenType.NUMBER)) {
            Token t = previous();
            String text = t.lexeme;
            // 含小数点就是 FLOAT，否则 INT
            if (text.contains(".")) {
                return new Literal(Double.parseDouble(text), DataType.FLOAT, t.line, t.column);
            }
            return new Literal(Integer.parseInt(text), DataType.INT, t.line, t.column);
        }
        if (match(TokenType.STRING)) {
            Token t = previous();
            return new Literal(t.lexeme, DataType.VARCHAR, t.line, t.column);
        }
        if (match(TokenType.TRUE)) {
            Token t = previous();
            return new Literal(true, DataType.BOOL, t.line, t.column);
        }
        if (match(TokenType.FALSE)) {
            Token t = previous();
            return new Literal(false, DataType.BOOL, t.line, t.column);
        }
        if (match(TokenType.IDENT)) {
            Token t = previous();
            return new ColumnRef(t.lexeme, t.line, t.column);
        }
        if (match(TokenType.LPAREN)) {
            Expr inner = parseExpr();
            consume(TokenType.RPAREN, "期望 ')'");
            return inner;
        }
        throw error("期望表达式（数字、字符串、标识符、TRUE/FALSE 或 '('）", peek());
    }

    // ------------------------------------------------------------------
    // 下面是一组"读取 Token"的小工具，是递归下降分析器的标准配件。
    // ------------------------------------------------------------------

    /** 看当前 Token（不消费）。 */
    private Token peek() {
        return tokens.get(current);
    }

    /** 看上一个已经消费掉的 Token。 */
    private Token previous() {
        return tokens.get(current - 1);
    }

    /** 消费当前 Token 并返回它（current 前进一格）。 */
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    /** 当前 Token 是不是某个类型？ */
    private boolean check(TokenType type) {
        return peek().type == type;
    }

    /**
     * 如果当前 Token 是给定类型之一，就消费并返回 true；否则返回 false。
     * 用于"可选语法"。
     */
    private boolean match(TokenType... types) {
        for (TokenType t : types) {
            if (check(t)) {
                advance();
                return true;
            }
        }
        return false;
    }

    /**
     * 要求当前 Token 必须是某类型并消费；否则抛语法错误。
     * 用于"必选语法"。
     */
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(message, peek());
    }

    /** 是否已经读到 EOF。 */
    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    /** 比较运算符集合。 */
    private boolean isComparisonOp(TokenType t) {
        return t == TokenType.EQ || t == TokenType.NEQ
                || t == TokenType.LT || t == TokenType.GT
                || t == TokenType.LE || t == TokenType.GE;
    }

    /** 快捷构造一个语法错误（定位到给定 Token 的位置）。 */
    private SqlError error(String message, Token token) {
        return new SqlError(Phase.PARSER, message, token.line, token.column);
    }
}
