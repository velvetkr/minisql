-- 边界测试：极长标识符
CREATE TABLE this_is_a_very_long_table_name_for_testing_purposes(col_one INT, col_two VARCHAR);
SELECT col_one FROM this_is_a_very_long_table_name_for_testing_purposes WHERE col_one > 0;
