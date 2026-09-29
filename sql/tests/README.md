# 测试用例说明

> 对应 PPT 第 37 页「测试体系」：把「正确性」变成可验证、可量化的事。
> 运行方式：`run.bat tests\<文件名>`（或 `java -cp out minisql.Main tests\<文件名>`）。
> 答辩现场演示：双击项目根目录的 `showcase.bat`（正常全链路 + 3 类错误定位 + 全套件）；单独跑全套件用 `test_all.bat`。

## 测试清单

| 文件 | 类别 | 预期 |
|------|------|------|
| `demo.sql` | 正常（端到端） | 五层全通过，含优化示例 |
| `showcase.sql` | 答辩演示（全特性） | 九条语句全通过，含四种类型 / 优先级 / 字符串转义 / 优化示例 |
| `edge_empty.sql` | 边界：空输入 | 通过（0 条语句，不崩溃） |
| `edge_case_sensitive.sql` | 边界：大小写混用 | 通过（关键字/表名/列名大小写不敏感） |
| `edge_multi_stmt.sql` | 边界：多语句 | 通过 |
| `edge_long_ident.sql` | 边界：极长标识符 | 通过 |
| `lexer_illegal_char.sql` | 词法错误：非法字符 `@` | 拒绝，LexerError |
| `lexer_unclosed_string.sql` | 词法错误：字符串未闭合 | 拒绝，LexerError |
| `parser_missing_semicolon.sql` | 语法错误：缺分号 | 拒绝，SyntaxError |
| `parser_bad_paren.sql` | 语法错误：括号不匹配 | 拒绝，SyntaxError |
| `semantic_table_not_exist.sql` | 语义错误：表不存在 | 拒绝，SemanticError |
| `semantic_column_not_exist.sql` | 语义错误：列不存在 | 拒绝，SemanticError |
| `semantic_type_mismatch.sql` | 语义错误：INSERT 类型不匹配 | 拒绝，SemanticError |
| `semantic_insert_count.sql` | 语义错误：INSERT 列数不符 | 拒绝，SemanticError |
| `semantic_dup_table.sql` | 语义错误：重复建表 | 拒绝，SemanticError |
| `semantic_arith_type.sql` | 语义错误：INT + VARCHAR | 拒绝，SemanticError |

## 失败案例分析

每个「失败案例」回答三个问题：**哪个阶段发现？为什么拒绝？定位准不准？**

### 1. `lexer_illegal_char.sql` — 非法字符
```sql
SELECT * FROM student WHERE name = @;
```
- 阶段：**词法**。`@` 不属于任何 Token 类别，`Lexer.scanOperator` 直接抛错。
- 报错：`LexerError at line 2, column 36: 非法字符 '@'`（列号精确指向 `@`）。

### 2. `lexer_unclosed_string.sql` — 字符串未闭合
```sql
SELECT 'abc FROM student;
```
- 阶段：**词法**。`scanString` 读到文件尾都没遇到收尾的 `'`。
- 报错：`LexerError at line 2, column 8: 字符串未闭合`。

### 3. `parser_missing_semicolon.sql` — 缺分号
```sql
SELECT * FROM student
```
- 阶段：**语法**。`parseSelect` 结尾 `consume(SEMICOLON)` 没等到 `;`，实际遇到 EOF。
- 报错：`SyntaxError at line 3, column 1: 期望 ';' 结束语句`。

### 4. `parser_bad_paren.sql` — 括号不匹配
```sql
SELECT * FROM student WHERE (age > 18;
```
- 阶段：**语法**。`parsePrimary` 消费了 `(` 后解析 `age > 18`，但 `consume(RPAREN)` 遇到 `;`。
- 报错：`SyntaxError at line 2, column 38: 期望 ')'`。

### 5. `semantic_table_not_exist.sql` — 表不存在
```sql
SELECT * FROM nosuchtable;
```
- 阶段：**语义**（词法、语法都合法，但 `nosuchtable` 没建过）。
- 报错：`SemanticError at line 2, column 15: 表 'nosuchtable' 不存在`。

### 6. `semantic_column_not_exist.sql` — 列不存在
```sql
CREATE TABLE t(id INT, name VARCHAR);
SELECT score FROM t;
```
- 阶段：**语义**。`resolveColumn` 在表 `t` 里找不到 `score`。
- 报错：`SemanticError ... 列 'score' 不存在于表 't'`（对应 PPT 第 26 页的名字绑定失败场景）。

### 7. `semantic_type_mismatch.sql` — INSERT 类型不匹配
```sql
CREATE TABLE t(id INT, name VARCHAR);
INSERT INTO t VALUES ('abc', 'def');
```
- 阶段：**语义**。值 `'abc'`（VARCHAR）不能赋给列 `id`（INT）。
- 报错：`SemanticError ... 第 1 个值的类型 VARCHAR 与列 'id' 的类型 INT 不匹配`。

### 8. `semantic_insert_count.sql` — INSERT 列数不符
```sql
CREATE TABLE t(id INT, name VARCHAR);
INSERT INTO t VALUES (1);
```
- 阶段：**语义**。表有 2 列，只给了 1 个值。
- 报错：`SemanticError ... 值数量 1 与表的列数量 2 不匹配`。

### 9. `semantic_dup_table.sql` — 重复建表
```sql
CREATE TABLE t(id INT);
CREATE TABLE t(id INT);
```
- 阶段：**语义**。`Catalog.tableExists` 发现 `t` 已注册。
- 报错：`SemanticError ... 表 't' 已存在，不能重复创建`。

### 10. `semantic_arith_type.sql` — 类型不匹配（PPT 第 27 页）
```sql
CREATE TABLE t(id INT);
SELECT * FROM t WHERE id + 'x' > 0;
```
- 阶段：**语义**。`id + 'x'` 是 `INT + VARCHAR`，违反类型规则。
- 报错：`SemanticError ... 算术运算 + 需要数值类型，实际是 VARCHAR`。
