package com.minimysql.engine; // 声明集成测试与引擎主类位于同一包，便于直接调用会话。

import com.minimysql.engine.executor.PlanExecutor; // 引入计划执行器。
import com.minimysql.engine.storage_engine.PageStorageEngine; // 引入真实页式存储实现。
import com.minimysql.engine.storage_engine.Row; // 引入查询结果行类型。
import org.junit.jupiter.api.Test; // 引入 JUnit 5 的测试注解。

import java.nio.file.Files; // 引入临时数据库文件操作。
import java.nio.file.Path; // 引入文件路径类型。
import java.util.List; // 引入测试结果列表类型。

import static org.junit.jupiter.api.Assertions.assertEquals; // 引入等值断言方法。

/** 验证编译器、执行器和页式存储协同工作的端到端测试。 */
class EngineIntegrationTest { // 定义引擎集成测试类。
    /** 验证建表、插入、查询、删除以及重启后的目录恢复。 */
    @Test // 标记该方法为 JUnit 测试用例。
    void executesCrudAndRestoresCatalogAfterRestart() throws Exception { // 执行完整 CRUD 流程并验证持久化。
        Path database = Files.createTempFile("minimysql-engine-", ".db"); // 创建本次测试专用的临时数据库文件。
        try { // 使用 finally 确保测试结束后删除临时文件。
            try (PageStorageEngine storage = new PageStorageEngine(database.toString())) { // 打开第一次运行使用的存储引擎。
                EngineSession session = new EngineSession(); // 创建负责 SQL 编译的会话。
                PlanExecutor executor = new PlanExecutor(storage); // 创建连接到当前存储的执行器。
                execute(session, executor, "CREATE TABLE student(id INT, name VARCHAR, age INT);"); // 建立包含三个字段的学生表。
                execute(session, executor, "INSERT INTO student VALUES (1, 'Alice', 20);"); // 插入一名成年学生。
                execute(session, executor, "INSERT INTO student VALUES (2, 'Bob', 15);"); // 插入一名未成年学生。
                List<Row> selected = execute(session, executor, "SELECT name FROM student WHERE age >= 18;"); // 查询年龄不小于 18 岁的学生姓名。
                assertEquals(List.of("Alice"), selected.stream().map(r -> r.get("name")).toList()); // 确认过滤和投影只返回 Alice。
                execute(session, executor, "DELETE FROM student WHERE age < 18;"); // 删除年龄小于 18 岁的记录。
            } // 关闭第一次存储，触发元数据和缓冲页刷盘。
            try (PageStorageEngine storage = new PageStorageEngine(database.toString())) { // 重新打开同一个文件，模拟进程重启。
                EngineSession session = new EngineSession(); // 创建新的编译会话，初始目录为空。
                storage.loadInto(session.catalog()); // 从持久化存储恢复 student 表定义。
                List<Row> selected = execute(session, new PlanExecutor(storage), "SELECT * FROM student;"); // 查询重启后仍然存在的全部记录。
                assertEquals(1, selected.size()); // 确认删除操作已经持久化，只剩一条记录。
                assertEquals("Alice", selected.getFirst().get("name")); // 确认剩余记录确实是 Alice。
            } // 关闭第二次存储并释放文件资源。
        } finally { // 无论断言成功或失败都执行清理。
            Files.deleteIfExists(database); // 删除临时数据库，避免测试污染工作区。
        } // 结束当前代码块。
    } // 结束当前代码块。

    /** 编译并执行 SQL，返回最后一个计划产生的结果行。 */
    private List<Row> execute(EngineSession session, PlanExecutor executor, String sql) { // 接收会话、执行器和待执行 SQL。
        List<Row> result = List.of(); // 默认没有结果，适用于建表和插入语句。
        for (var plan : session.compile(sql).plans()) { // 编译 SQL 后按顺序遍历所有优化计划。
            result = executor.execute(plan); // 执行当前计划并保存其结果。
        } // 结束当前代码块。
        return result; // 返回最后一个计划的查询结果。
    } // 结束当前代码块。
} // 结束当前代码块。
