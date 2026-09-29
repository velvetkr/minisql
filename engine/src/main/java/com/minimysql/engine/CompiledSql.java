package com.minimysql.engine; // 声明编译结果所在的引擎包。

import minisql.ast.Statement; // 引入抽象语法树语句类型。
import minisql.catalog.Catalog; // 引入编译时使用的目录类型。
import minisql.plan.Plan; // 引入优化后的执行计划类型。

import java.util.List; // 使用 List 保存多个语句和计划。

/**
 * 编译阶段与执行阶段之间传递的数据。
 *
 * <p>编译器会同时产生语句、优化后的执行计划和目录快照引用，执行器只需要
 * 读取这个对象即可开始执行。列表在构造时复制，避免调用方之后修改编译结果。</p>
 */
public record CompiledSql(String sql, List<Statement> statements, List<Plan> plans, Catalog catalog) { // 用不可变记录封装一次编译结果。
    public CompiledSql { // 进入记录的紧凑构造器。
        statements = List.copyOf(statements); // 复制语句列表并阻止外部修改。
        plans = List.copyOf(plans); // 复制计划列表并阻止外部修改。
    } // 结束当前代码块。
} // 结束当前代码块。
