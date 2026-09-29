package minisql;

import minisql.ast.AstPrinter;
import minisql.ast.Statement;
import minisql.catalog.Catalog;
import minisql.catalog.Table;
import minisql.error.SqlError;
import minisql.lexer.Lexer;
import minisql.lexer.Token;
import minisql.optimizer.Optimizer;
import minisql.parser.Parser;
import minisql.plan.Plan;
import minisql.plan.PlanPrinter;
import minisql.plan.Planner;
import minisql.semantic.SemanticAnalyzer;
import minisql.serialize.JsonPrinter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * MiniSQL 编译器入口。
 *
 * <p>用法：</p>
 * <pre>
 *   java -cp out minisql.Main               # 从标准输入读取 SQL
 *   java -cp out minisql.Main tests/xx.sql  # 从文件读取 SQL
 * </pre>
 *
 * <p>本类依次跑完整条流水线（PPT 第 43 页主线的编译器部分），
 * 并打印每一层的中间结果：</p>
 * <pre>
 *   SQL → ① Token → ② AST → ③ Semantic(Catalog) → ④ Plan → ⑤ Optimized Plan → ⑥ JSON
 * </pre>
 *
 * <p>其中 ⑥ 是「扩展加分项」：把 AST 和优化后的 Plan 用 JSON 形式再输出一份
 * （对应 PPT 第 30 页「Plan 输出形式：树形 / JSON / S-expression 任选其一」）。</p>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String sql = readSql(args);

        try {
            // ---------- 输入 ----------
            System.out.println("========== 输入 SQL ==========");
            System.out.println(sql);
            System.out.println();

            // ① 词法分析：字符流 → Token 流
            Lexer lexer = new Lexer(sql);
            List<Token> tokens = lexer.tokenize();

            System.out.println("========== ① 词法分析：Token 流 ==========");
            for (Token t : tokens) {
                System.out.println("  " + t);
            }
            System.out.println();

            // ② 语法分析：Token 流 → AST
            Parser parser = new Parser(tokens);
            List<Statement> statements = parser.parse();

            System.out.println("========== ② 语法分析：AST（抽象语法树） ==========");
            for (int i = 0; i < statements.size(); i++) {
                System.out.println("-- 语句 " + (i + 1) + " --");
                System.out.println(AstPrinter.print(statements.get(i)));
            }
            System.out.println();

            // ③ 语义分析：检查表/列存在性、类型一致性，并注册表到 Catalog
            Catalog catalog = new Catalog();
            SemanticAnalyzer analyzer = new SemanticAnalyzer(catalog);
            analyzer.analyze(statements);

            System.out.println("========== ③ 语义分析：Catalog（已注册的表结构） ==========");
            for (Table table : catalog.allTables()) {
                StringBuilder sb = new StringBuilder("  表 " + table.name + " (");
                for (int i = 0; i < table.columns.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(table.columns.get(i));
                }
                sb.append(")");
                System.out.println(sb);
            }
            System.out.println("  语义分析通过：表/列存在性、类型检查均正常");
            System.out.println();

            // ④ 执行计划：AST → 逻辑算子树
            Planner planner = new Planner(catalog);
            List<Plan> plans = planner.plan(statements);

            System.out.println("========== ④ 逻辑执行计划（AST → Plan） ==========");
            for (int i = 0; i < plans.size(); i++) {
                System.out.println("-- 语句 " + (i + 1) + " --");
                System.out.print(PlanPrinter.print(plans.get(i)));
            }
            System.out.println();

            // ⑤ 规则优化：展示优化前后对比
            Optimizer optimizer = new Optimizer();
            List<Plan> optimizedPlans = new ArrayList<>();
            System.out.println("========== ⑤ 规则优化：优化前 vs 优化后 ==========");
            for (int i = 0; i < plans.size(); i++) {
                Plan before = plans.get(i);
                Plan after = optimizer.optimize(before);
                optimizedPlans.add(after);
                System.out.println("-- 语句 " + (i + 1) + " --");
                System.out.println("优化前:");
                System.out.print(PlanPrinter.print(before));
                System.out.println("优化后:");
                System.out.print(PlanPrinter.print(after));
            }

            // ⑥ JSON 序列化输出（扩展加分项：Plan 的第二种表示形式，PPT 第 30 页）
            System.out.println("========== ⑥ JSON 序列化（扩展加分项） ==========");
            for (int i = 0; i < statements.size(); i++) {
                System.out.println("-- 语句 " + (i + 1) + " 的 AST（JSON） --");
                System.out.println(JsonPrinter.ofStatement(statements.get(i)));
                System.out.println("-- 语句 " + (i + 1) + " 的优化后 Plan（JSON） --");
                System.out.println(JsonPrinter.ofPlan(optimizedPlans.get(i)));
                System.out.println();
            }

            // 把优化后的 Plan 序列化成 JSON 数组写到 plan.json，供 Python 后端读取（跨语言接口）
            StringBuilder planJson = new StringBuilder("[\n");
            for (int i = 0; i < optimizedPlans.size(); i++) {
                if (i > 0) planJson.append(",\n");
                planJson.append(JsonPrinter.ofPlan(optimizedPlans.get(i)));
            }
            planJson.append("\n]\n");
            Files.writeString(Path.of("plan.json"), planJson.toString(), StandardCharsets.UTF_8);
            System.out.println("已将 " + optimizedPlans.size() + " 条优化后 Plan 写入 plan.json（供 Python 后端读取）");

        } catch (SqlError e) {
            // 统一捕获编译错误（词法/语法/语义），打印整齐的错误信息而不崩溃
            System.err.println("编译失败：" + e);
            System.exit(1);
        }
    }

    /** 从文件或标准输入读取 SQL 文本。 */
    private static String readSql(String[] args) throws Exception {
        if (args.length >= 1) {
            return new String(Files.readAllBytes(Path.of(args[0])), StandardCharsets.UTF_8);
        }
        // 没有文件参数时，从标准输入读（用空行结束）
        System.out.println("请输入 SQL（输入空行结束）：");
        Scanner sc = new Scanner(System.in);
        StringBuilder sb = new StringBuilder();
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.trim().isEmpty()) break;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
