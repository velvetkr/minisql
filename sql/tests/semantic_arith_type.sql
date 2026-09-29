-- 语义错误：INT + VARCHAR 类型不匹配（PPT 第 27 页的例子）
CREATE TABLE t(id INT);
SELECT * FROM t WHERE id + 'x' > 0;
