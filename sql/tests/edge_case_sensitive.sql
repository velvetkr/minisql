-- 边界测试：关键字大小写混用 + 表名/列名大小写不敏感
CREATE TABLE STUDENT(ID INT, NAME VARCHAR, AGE INT);
INSERT INTO student VALUES (1, 'Alice', 20);
select name, age from Student where Age > 18;
