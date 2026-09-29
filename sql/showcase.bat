@echo off
rem ============================================================
rem  MiniSQL Compiler - live defense demo
rem  Double-click to run, or run "showcase.bat" in cmd.
rem  Flow: build -> happy path -> 3 error types -> test suite
rem ============================================================

rem Switch console to UTF-8 (avoid Chinese garbled output)
chcp 65001 >nul

rem Go to this script's own directory
cd /d "%~dp0"

echo.
echo ================================================================
echo    MiniSQL 编译器 - 答辩现场演示
echo    主线：SQL - Token - AST - Semantic - Plan - Optimize
echo ================================================================
echo.

rem ---- build first if not compiled ----
if not exist out\minisql\Main.class (
    echo [准备] 尚未编译，先执行编译...
    echo.
    call "%~dp0build.bat"
    echo.
)

rem ---- Part 1: happy path ----
echo ================================================================
echo    演示 1 / 3：正常全链路（CREATE / INSERT / SELECT / DELETE）
echo    看六层输出：Token - AST - Catalog - Plan - 优化前后 - JSON
echo ================================================================
echo.
call "%~dp0run.bat" tests\showcase.sql
echo.
echo    重点看第 [8] 条语句的优化前后对比：1=1 和 10+8 被折叠
echo.
pause

rem ---- Part 2: error location ----
echo ================================================================
echo    演示 2 / 3：错误定位 —— 词法 / 语法 / 语义 各一类
echo ================================================================
echo.

echo  --- 2.1 词法错误：非法字符 @ ---
call "%~dp0run.bat" tests\lexer_illegal_char.sql
echo.
echo  --- 2.2 语法错误：缺分号 ---
call "%~dp0run.bat" tests\parser_missing_semicolon.sql
echo.
echo  --- 2.3 语义错误：列不存在 ---
call "%~dp0run.bat" tests\semantic_column_not_exist.sql
echo.
echo    注意错误前缀：LexerError / SyntaxError / SemanticError 加行列号
echo.
pause

rem ---- Part 3: test suite ----
echo ================================================================
echo    演示 3 / 3：完整测试套件（16 个用例）
echo ================================================================
echo.
call "%~dp0test_all.bat"
echo.
pause

echo ================================================================
echo    演示结束，谢谢！
echo ================================================================
pause
