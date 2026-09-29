@echo off
rem ============================================================
rem  MiniSQL Compiler - test suite runner
rem  Runs all tests/ cases and reports pass/fail per file.
rem  Used for defense part 3 (test system / quantified testing).
rem ============================================================

chcp 65001 >nul
cd /d "%~dp0"

setlocal enabledelayedexpansion

set PASS=0
set FAIL=0

echo.
echo    MiniSQL 编译器 · 测试套件
echo    ------------------------------------------------------------

rem ---- cases expected to be ACCEPTED (compiler exits 0) ----
call :ok "demo.sql"                       "端到端正常全链路"
call :ok "showcase.sql"                   "答辩演示（全特性）"
call :ok "edge_empty.sql"                 "边界：空输入"
call :ok "edge_case_sensitive.sql"        "边界：大小写混用"
call :ok "edge_multi_stmt.sql"            "边界：多条语句"
call :ok "edge_long_ident.sql"            "边界：极长标识符"

rem ---- cases expected to be REJECTED (compiler exits 1) ----
call :err "lexer_illegal_char.sql"        "词法错误：非法字符 @"
call :err "lexer_unclosed_string.sql"     "词法错误：字符串未闭合"
call :err "parser_missing_semicolon.sql"  "语法错误：缺分号"
call :err "parser_bad_paren.sql"          "语法错误：括号不匹配"
call :err "semantic_table_not_exist.sql"  "语义错误：表不存在"
call :err "semantic_column_not_exist.sql" "语义错误：列不存在"
call :err "semantic_type_mismatch.sql"    "语义错误：INSERT 类型不符"
call :err "semantic_insert_count.sql"     "语义错误：INSERT 列数不符"
call :err "semantic_dup_table.sql"        "语义错误：重复建表"
call :err "semantic_arith_type.sql"       "语义错误：INT + VARCHAR"

echo    ------------------------------------------------------------
echo    结果：!PASS! 个通过，!FAIL! 个失败
echo.
if !FAIL! gtr 0 (
    echo    有失败用例，请检查上方标记 [失败] 的条目
) else (
    echo    全部通过
)
echo.

endlocal
exit /b 0

rem ============ subroutines ============

:ok
rem expected to be ACCEPTED: exit code should be 0
call "%~dp0run.bat" "tests\%~1" >nul 2>&1
if errorlevel 1 (
    echo    [失败] %~2   （应通过却报错）
    set /a FAIL+=1
) else (
    echo    [通过] %~2
    set /a PASS+=1
)
exit /b 0

:err
rem expected to be REJECTED: exit code should be 1
call "%~dp0run.bat" "tests\%~1" >nul 2>&1
if errorlevel 1 (
    echo    [通过] %~2   （正确拒绝）
    set /a PASS+=1
) else (
    echo    [失败] %~2   （应拒绝却通过）
    set /a FAIL+=1
)
exit /b 0
