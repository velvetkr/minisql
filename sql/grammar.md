# MiniSQL 文法说明（grammar.md）

> 本文件显式提交 MiniSQL 编译器的完整文法（词法 + 语法）。
> 语法部分与 `minisql.parser.Parser` 的递归下降实现**逐条对应**（PPT 第 15 页要求「代码实现必须与文法一致」）。

---

## 一、词法规则（Lexer）

### 1. Token 类别

| 类别 | 示例 | 说明 |
|------|------|------|
| 关键字 KEYWORD | `SELECT` `FROM` `WHERE` `CREATE` `TABLE` `INSERT` `INTO` `VALUES` `DELETE` `AND` `OR` `NOT` `TRUE` `FALSE` `INT` `VARCHAR` `BOOL` `FLOAT` | 大小写不敏感（`select` / `SELECT` / `SeLeCt` 等价） |
| 标识符 IDENT | `student` `age` `user_name` | 字母 / 下划线开头，后接字母数字下划线 |
| 整数/小数 CONST | `20` `3.14` | 数字，可含小数点 |
| 字符串 STRING | `'Alice'` `'Tom''s book'` | 单引号包裹，`''` 表示转义的单引号 |
| 运算符 OPERATOR | `= != <> < <= > >= + - * /` | `==` 等同 `=`，`<>` 等同 `!=` |
| 分隔符 DELIMITER | `( ) , ;` | |

### 2. 词法约定

- **关键字与标识符区分**：先扫描成字符串，再查关键字表；命中则归类为关键字，否则为标识符。
- **关键字大小写不敏感**：查表前转小写。
- **字符串内容保持原样**：`'Tom''s book'` 的词法值是 `Tom's book`（去掉外层引号、`''` 还原为 `'`）。
- **注释**：`--` 到行尾为单行注释，词法阶段直接跳过。
- **非法输入**：`@`、未闭合字符串、`123abc` 这类非法数字，均抛出带行/列的 `LexerError`，不崩溃。

---

## 二、语法文法（Parser）

记法：`->` 产生式；`|` 或；`(...)` 分组；`*` 零次或多次；`?` 可选；`'...'` 终结符字面量；`ident` / `number` / `string` 为词法记号。

### 1. 语句层

```
program        -> statement*                          （多条语句用分号分隔）

statement      -> create_table
                | insert
                | select
                | delete

create_table   -> CREATE TABLE ident '(' column_list ')' ';'
column_list    -> column_def (',' column_def)*
column_def     -> ident type
type           -> INT | FLOAT | VARCHAR | BOOL

insert         -> INSERT INTO ident ('(' ident_list ')')? VALUES '(' expr_list ')' ';'
ident_list     -> ident (',' ident)*
expr_list      -> expr (',' expr)*

select         -> SELECT select_list FROM ident (WHERE expr)? ';'
select_list    -> '*' | select_item (',' select_item)*
select_item    -> ident (AS ident)?

delete         -> DELETE FROM ident (WHERE expr)? ';'
```

### 2. 表达式层（按优先级从低到高，一层一个方法）

```
expr            -> or_expr

or_expr         -> and_expr (OR and_expr)*             优先级最低
and_expr        -> not_expr (AND not_expr)*
not_expr        -> NOT not_expr | comparison
comparison      -> additive (比较符 additive)?
additive        -> multiplicative (('+' | '-') multiplicative)*
multiplicative  -> unary (('*' | '/') unary)*
unary           -> '-' unary | primary                 优先级最高
primary         -> number | string | TRUE | FALSE | ident | '(' expr ')'
```

其中比较符：`= | != | < | > | <= | >=`

### 3. 运算符优先级表（从高到低）

| 优先级 | 运算符 | 结合性 | Parser 方法 |
|-------|--------|--------|-------------|
| 1（最高） | `-`（一元负号）、原子（字面量/列/括号） | 右结合 | `parseUnary` / `parsePrimary` |
| 2 | `*` `/` | 左结合 | `parseMultiplicative` |
| 3 | `+` `-` | 左结合 | `parseAdditive` |
| 4 | `= != < > <= >=` | 左结合 | `parseComparison` |
| 5 | `NOT` | 右结合 | `parseNot` |
| 6 | `AND` | 左结合 | `parseAnd` |
| 7（最低） | `OR` | 左结合 | `parseOr` |

**优先级示例**（PPT 第 18/21 页）：
`WHERE a = 1 OR b = 2 AND c = 3` 解析为 `OR(=(a,1), AND(=(b,2), =(c,3)))`，
即 **AND 比 OR 更贴近叶子、结合更紧**。

---

## 三、类型系统（语义分析用）

| 类型 | 值 | 允许的运算 |
|------|-----|-----------|
| `INT` | 整数 | 算术、数值比较 |
| `FLOAT` | 小数 | 算术、数值比较 |
| `VARCHAR` | 字符串 | `= !=` 及字典序比较 |
| `BOOL` | `TRUE` / `FALSE` | `AND` `OR` `NOT`、`= !=` |

类型规则（PPT 第 27 页，集中在 `SemanticAnalyzer` 内）：

| 运算 | 规则 | 结果 |
|------|------|------|
| `+ - * /` | 两边数值；任一 FLOAT → FLOAT，否则 INT | 数值 |
| `= != < > <= >=` | 两边同类（数值之间可互比） | BOOL |
| `AND` `OR` `NOT` | 操作数 BOOL | BOOL |
| `INT + VARCHAR` | — | **报错** |

---

## 四、文法 ↔ 代码对应关系

| 产生式 | Parser 方法 |
|--------|-------------|
| `create_table` | `parseCreateTable()` |
| `insert` | `parseInsert()` |
| `select` | `parseSelect()` |
| `delete` | `parseDelete()` |
| `or_expr` / `and_expr` / `not_expr` | `parseOr()` / `parseAnd()` / `parseNot()` |
| `comparison` / `additive` / `multiplicative` / `unary` / `primary` | `parseComparison()` / `parseAdditive()` / `parseMultiplicative()` / `parseUnary()` / `parsePrimary()` |
