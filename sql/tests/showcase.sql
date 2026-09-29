-- ================================================================
-- MiniSQL 编译器 · 答辩演示（happy path 全链路）
-- 运行：showcase.bat  或  run.bat tests\showcase.sql
-- 主线：SQL → Token → AST → Semantic → Plan → Optimize
-- ================================================================

-- [1] 建表：四种数据类型 INT / VARCHAR / FLOAT / BOOL
CREATE TABLE student(id INT, name VARCHAR, score FLOAT, active BOOL);

-- [2] 插入：不写列名（按表的全部列，值按顺序）
INSERT INTO student VALUES (1, 'Alice', 95.5, TRUE);

-- [3] 插入：指定列名（列顺序可以和表定义不同）
INSERT INTO student(name, id, score, active) VALUES ('Bob', 2, 80.0, FALSE);

-- [4] 插入：字符串里的单引号用 '' 转义（SQL 标准写法）
INSERT INTO student VALUES (3, 'Tom''s book', 88.5, TRUE);

-- [5] 查询：投影 + WHERE + 算术表达式（score + 5 >= 90）
SELECT name, score FROM student WHERE score + 5 >= 90;

-- [6] 查询：运算符优先级 —— AND 比 OR 结合更紧（请看 AST 的结构）
SELECT name FROM student WHERE id = 1 OR id = 2 AND score > 85;

-- [7] 查询：逻辑 NOT + 字符串比较 + SELECT *
SELECT * FROM student WHERE NOT active OR name = 'Tom''s book';

-- [8] ★ 优化器：1=1 被折叠、10+8 被算成 18（请看"优化前 vs 优化后"）
SELECT name FROM student WHERE 1 = 1 AND score > 10 + 8;

-- [9] 删除：WHERE 条件删除
DELETE FROM student WHERE score < 85;
