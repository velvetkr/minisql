package com.minimysql.engine.cli; // 声明命令行入口所在的包。

import com.minimysql.engine.CompiledSql; // 引入编译结果封装。
import com.minimysql.engine.EngineSession; // 引入 SQL 编译会话。
import com.minimysql.engine.executor.PlanExecutor; // 引入执行计划执行器。
import com.minimysql.engine.storage_engine.PageStorageEngine; // 引入页式持久化存储。
import minisql.plan.PlanPrinter; // 引入执行计划打印器。
import minisql.error.SqlError; // 引入编译阶段错误类型。
import storage.Constants; // 引入数据库文件名等存储常量。

/** 最小命令行入口：接收 SQL，编译成优化计划，再交给执行器执行。 */
public final class Main { // 定义不可实例化的命令行工具类。
    private Main() { // 隐藏构造器，防止创建无意义的工具类对象。
        // 工具类不需要实例化。
    } // 结束当前代码块。

    public static void main(String[] args) { // JVM 从这里接收命令行参数。
        if (args.length == 0) { // 没有 SQL 参数时无法执行任何语句。
            System.err.println("Usage: java ... com.minimysql.engine.cli.Main <sql>"); // 打印正确用法。
            return; // 结束本次命令行调用。
        } // 结束当前代码块。

        try (PageStorageEngine storage = new PageStorageEngine(Constants.DB_FILENAME)) { // 打开数据库文件并确保最终关闭资源。
            EngineSession session = new EngineSession(); // 创建使用新目录的编译会话。
            storage.loadInto(session.catalog()); // 在编译前恢复磁盘中的表定义。
            CompiledSql compiled = session.compile(String.join(" ", args)); // 拼接参数并生成优化计划。
            PlanExecutor executor = new PlanExecutor(storage); // 创建连接到页存储的执行器。

            for (var plan : compiled.plans()) { // 按 SQL 出现顺序逐个执行计划。
                System.out.print(PlanPrinter.print(plan)); // 先打印计划，方便观察编译结果。
                var rows = executor.execute(plan); // 执行计划并取得查询结果行。
                for (var row : rows) { // 遍历当前计划返回的每一行。
                    System.out.println(row.values()); // 以列名到值的映射形式输出行。
                } // 结束当前代码块。
            } // 结束当前代码块。
        } catch (SqlError e) { // 捕获词法、语法或语义阶段的 SQL 错误。
            System.err.println("编译失败：" + e); // 将编译错误输出到标准错误流。
            System.exit(1); // 使用非零状态表示命令执行失败。
        } catch (RuntimeException e) { // 捕获存储或执行阶段的运行时错误。
            System.err.println("执行失败：" + e.getMessage()); // 输出便于用户理解的错误信息。
            System.exit(1); // 使用非零状态表示命令执行失败。
        } // 结束当前代码块。
    } // 结束当前代码块。
} // 结束当前代码块。
