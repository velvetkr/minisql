# miniMySQL 当前 SQL 语法支持情况

本文档描述当前 MVP 版本通过 CLI 可使用的 SQL 子集。SQL 关键字不区分大小写；表名和列名也不区分大小写。

## 已支持

### `CREATE TABLE`

```sql
CREATE TABLE student (
    id INT,
    name VARCHAR,
    score FLOAT,
    active BOOL
);
```

支持的数据类型：`INT`、`FLOAT`、`VARCHAR`、`BOOL`。

执行效果：创建表、注册列定义，并将目录信息持久化到页式存储文件。

### `INSERT INTO ... VALUES`

```sql
INSERT INTO student VALUES (1, 'Alice', 95.5, TRUE);
INSERT INTO student (name, id, score, active)
VALUES ('Bob', 2, 80, FALSE);
```

支持按表中全部列插入，也支持指定列名。指定列时，未指定的列使用引擎 MVP 默认值：数值为 `0`，字符串为空串，布尔值为 `FALSE`。

### `SELECT ... FROM`

```sql
SELECT * FROM student;
SELECT name, score FROM student;
```

支持投影列和 `*`。查询结果由执行器从页式存储中读取。

### `WHERE` 条件

```sql
SELECT name FROM student WHERE score >= 80 AND active = TRUE;
DELETE FROM student WHERE id = 2;
```

支持：

- 比较：`=`、`!=`、`<`、`>`、`<=`、`>=`
- 逻辑：`AND`、`OR`、`NOT`
- 算术：`+`、`-`、`*`、`/`
- 括号和一元负号

### `DELETE FROM`

```sql
DELETE FROM student WHERE score < 60;
DELETE FROM student;
```

支持按条件删除和删除整张表中的记录。当前采用物理删除并回收数据页。

## 部分支持或存在 MVP 限制

### 多条语句

CLI 参数中可以传入以分号分隔的多条语句，例如：

```sql
CREATE TABLE t(id INT); INSERT INTO t VALUES (1); SELECT * FROM t;
```

语句按书写顺序编译和执行。当前没有事务，执行到中途失败时不会回滚之前已经完成的语句。

### 类型转换

`INT` 和 `FLOAT` 之间允许数值兼容；其它类型必须严格匹配。当前不支持显式 `CAST`，也不支持 `NULL`。

### 持久化目录容量

当前系统目录存储在一个固定页中。表和列数量很大时可能超过单页容量，届时会报告目录超限；记录数据本身可以使用多个数据页。

## 当前不支持

| 语法或功能 | 原因 |
| --- | --- |
| `UPDATE ... SET ...` | 受保护的 SQL 编译器虽然词法表包含 `UPDATE`，但 Parser 没有 UPDATE 分支，AST、语义分析和执行计划也未提供 `UpdateStmt`/`UpdatePlan`。引擎无法接收到可执行计划。 |
| `ALTER TABLE`、`DROP TABLE` | 编译器没有对应 AST、Parser 和 Plan；引擎目录也没有表删除/修改 schema 的接口。 |
| `JOIN`、子查询、多表 `FROM` | 当前计划模型只有单表 `SeqScan`，没有连接算子或多表绑定逻辑。 |
| `ORDER BY`、`GROUP BY`、聚合函数 | 当前没有排序、分组、聚合执行算子及对应计划节点。 |
| `LIMIT`、`DISTINCT` | 编译器计划和执行器尚未提供限制行数、去重算子。 |
| `CREATE DATABASE`、`USE`、多数据库 | 目录模型只有单一数据库和表名，没有数据库级目录或当前数据库状态。 |
| `NULL`、主键、唯一约束、默认约束 | 当前列定义只包含列名和基础类型，没有约束元数据及 NULL 语义。 |
| 事务、回滚、锁和并发控制 | MVP 尚未实现事务管理器和并发控制模块。 |

## 受保护模块说明

`minisql/`（SQL 编译器）和 `页式存储系统/`（页式存储实现）属于只读模块。本清单中“不支持”的编译器能力不能通过只修改 `engine/` 来直接补齐；如后续确需实现，应先在 `engine/docs/protected-module-change-proposals/` 提交对应的 API 和兼容性方案，获得批准后再修改受保护模块。

