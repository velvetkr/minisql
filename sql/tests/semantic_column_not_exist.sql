-- 语义错误：列不存在
CREATE TABLE t(id INT, name VARCHAR);
SELECT score FROM t;
