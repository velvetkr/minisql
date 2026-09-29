package com.minimysql.engine; // 声明会话编译器所在的引擎包。

import minisql.ast.Statement; // 引入抽象语法树语句类型。
import minisql.catalog.Catalog; // 引入共享的表目录。
import minisql.lexer.Lexer; // 引入词法分析器。
import minisql.lexer.Token; // 引入词法单元类型。
import minisql.optimizer.Optimizer; // 引入计划优化器。
import minisql.parser.Parser; // 引入语法分析器。
import minisql.plan.Plan; // 引入执行计划类型。
import minisql.plan.Planner; // 引入逻辑计划生成器。
import minisql.semantic.SemanticAnalyzer; // 引入语义检查器。

import java.util.List; // 使用 List 保存编译流水线中间结果。

/**
 * 引擎会话：串联词法分析、语法分析、语义检查、计划生成和优化。
 *
 * <p>该类只负责把 SQL 转换成可执行计划，不直接读写数据；数据读写由执行器
 * 和存储引擎完成，从而保持编译器与存储层之间的边界清晰。</p>
 */
public final class EngineSession { // 表示一次可复用的 SQL 编译会话。
    private final Catalog catalog; // 保存语义分析和计划生成共享的目录。
    private final Planner planner; // 保存绑定到该目录的计划生成器。
    private final Optimizer optimizer = new Optimizer(); // 创建计划优化器实例。

    /** 使用一个新的空目录创建会话。 */
    public EngineSession() { // 提供无参数的默认构造方式。
        this(new Catalog()); // 创建空目录后委托给主构造器。
    } // 结束当前代码块。

    /** 使用指定目录创建会话，允许多个组件共享同一份表定义。 */
    public EngineSession(Catalog catalog) { // 接收需要共享的目录对象。
        this.catalog = java.util.Objects.requireNonNull(catalog); // 校验目录不能为 null。
        this.planner = new Planner(catalog); // 创建使用同一目录的计划生成器。
    } // 结束当前代码块。

    /**
     * 编译一条或多条 SQL 语句，并返回优化后的计划。
     *
     * <p>语句必须依次通过每个编译阶段；任何阶段抛出的 SQL 错误都会直接交给
     * 调用方处理，不会被包装成难以定位的通用异常。</p>
     */
    public CompiledSql compile(String sql) { // 接收原始 SQL 并返回编译结果。
        List<Token> tokens = new Lexer(sql).tokenize(); // 第一步：把字符流切分成 token。
        List<Statement> statements = new Parser(tokens).parse(); // 第二步：把 token 解析成语法树。
        new SemanticAnalyzer(catalog).analyze(statements); // 第三步：依据目录执行语义合法性检查。
        List<Plan> plans = planner.plan(statements).stream() // 第四步：为每条语句生成逻辑计划。
                .map(optimizer::optimize) // 对每个逻辑计划应用优化规则。
                .toList(); // 收集为不可变计划列表。
        return new CompiledSql(sql, statements, plans, catalog); // 封装并返回所有编译阶段结果。
    } // 结束当前代码块。

    /** 返回当前会话使用的目录，供存储恢复和执行阶段共享。 */
    public Catalog catalog() { // 提供目录访问方法。
        return catalog; // 返回当前会话持有的目录对象。
    } // 结束当前代码块。
} // 结束当前代码块。
