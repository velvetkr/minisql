-- 语义错误：INSERT 值类型与列类型不匹配（字符串塞进 INT 列）
CREATE TABLE t(id INT, name VARCHAR);
INSERT INTO t VALUES ('abc', 'def');
