@echo off
rem ============================================================
rem  MiniSQL 编译器 运行脚本
rem  用法：run.bat tests\demo.sql
rem  不带参数时从标准输入读取 SQL（空行结束）
rem ============================================================

rem 控制台切 UTF-8，并把 Java 的输出编码也固定为 UTF-8
chcp 65001 >nul
cd /d "%~dp0"

if "%~1"=="" (
    java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp out minisql.Main
) else (
    java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp out minisql.Main "%~1"
)
