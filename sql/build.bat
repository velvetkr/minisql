@echo off
rem ============================================================
rem  MiniSQL 编译器 一键编译脚本
rem  双击运行，或在命令行执行 build.bat
rem ============================================================

rem 把控制台编码切到 UTF-8，避免中文乱码
chcp 65001 >nul

rem 切到脚本所在目录（%~dp0 = 本脚本所在文件夹）
cd /d "%~dp0"

if not exist out mkdir out

echo [1/2] 收集所有 .java 源文件...
dir /s /b src\*.java > sources.txt

echo [2/2] 编译中...
javac -encoding UTF-8 -d out @sources.txt

if errorlevel 1 (
    echo.
    echo 编译失败！请检查上方的错误信息。
    del sources.txt
    exit /b 1
)

del sources.txt
echo.
echo 编译成功！字节码已输出到 out\ 目录。
echo 下一步运行：run.bat tests\demo.sql
