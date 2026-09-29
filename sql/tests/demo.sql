-- ============================================================
-- 端到端演示：覆盖 CREATE / INSERT / SELECT / DELETE 全链路
-- 运行：run.bat tests\demo.sql
-- ============================================================

-- 建表
CREATE TABLE student(id INT, name VARCHAR, age INT);

-- 插入（不指定列名 / 指定列名两种写法）
INSERT INTO student VALUES (1, 'Alice', 20);
INSERT INTO student(id, name, age) VALUES (2, 'Bob', 17);

-- 查询（普通 WHERE）
SELECT name, age FROM student WHERE age > 18;

-- 查询（会被优化：1=1 AND age>10+8 → age>18）
SELECT name FROM student WHERE 1 = 1 AND age > 10 + 8;

-- 查询（SELECT *）
SELECT * FROM student WHERE name = 'Alice';

-- 删除
DELETE FROM student WHERE age < 18;
