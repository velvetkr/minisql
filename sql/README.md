# MiniSQL 编译器（前端）

「大型平台软件设计实习」小型数据库系统的**前端编译器**：把 SQL 文本一步步翻译成**优化后的逻辑执行计划**。纯 Java、手写、零第三方依赖，`javac` 直接编译。

完整链路（PPT 第 43 页主线）：

```
SQL 文本
  → ① Lexer（词法分析）      → Token 流
  → ② Parser（语法分析）     → AST
  → ③ SemanticAnalyzer（语义）→ 名字绑定 + 类型检查 + Catalog
  → ④ Planner（计划生成）    → Logical Plan（算子树）
  → ⑤ Optimizer（规则优化）  → Optimized Plan
  → ⑥ JsonPrinter（JSON 序列化）→ AST/Plan 的 JSON 文本（扩展加分项）
```

---

## 一、项目结构

```
minisql/
├── build.bat / run.bat        # 一键编译 / 运行脚本
├── grammar.md                 # 完整文法（词法 + 语法 + 类型系统）
├── README.md                  # 本文件
├── src/minisql/
│   ├── Main.java              # 流水线入口（跑通 ①~⑥ 并打印各层结果）
│   ├── error/SqlError.java    # 统一错误类型（Lexer/Syntax/Semantic + 行列定位）
│   ├── lexer/                 # ① 词法：Token、TokenType、Lexer
│   ├── ast/                   # ② AST：12 个节点 + AstPrinter + ExprPrinter
│   ├── parser/Parser.java     # ② 语法：递归下降
│   ├── catalog/               # ③ 符号表：Catalog、Table
│   ├── semantic/              # ③ 语义：SemanticAnalyzer
│   ├── plan/                  # ④ 计划：Plan + 5 节点 + PlanPrinter + Planner
│   ├── optimizer/             # ⑤ 优化：Optimizer（5 条规则）
│   └── serialize/             # ⑥ JSON 序列化：JsonPrinter（扩展加分项）
├── tests/                     # 测试用例（正常 + 各类错误 + 边界）
└── docs/答辩手册.md           # 分模块讲解稿 + 常见答辩提问
```

---

## 二、快速开始

**编译**（双击或在命令行执行）：

```bat
build.bat
```

输出字节码到 `out\` 目录。

**运行**（跑一条 SQL 文件，打印五层结果）：

```bat
run.bat tests\demo.sql
```

或不带参数从键盘输入 SQL（空行结束）：

```bat
run.bat
```

> 两个脚本都做了 UTF-8 编码处理（`chcp 65001` + `-Dfile.encoding=UTF-8`），中文不会乱码。

---

## 三、支持范围（必做核心 SQL 子集）

| 语句 | 语法 | 说明 |
|------|------|------|
| 建表 | `CREATE TABLE 表名(列 类型, ...);` | 类型：`INT / FLOAT / VARCHAR / BOOL` |
| 插入 | `INSERT INTO 表名 VALUES (...);` 或 `INSERT INTO 表名(列...) VALUES (...);` | |
| 查询 | `SELECT 列 | * FROM 表名 WHERE 条件;` | 支持 `AS` 别名、`NOT`、比较、算术、`AND/OR` |
| 删除 | `DELETE FROM 表名 WHERE 条件;` | |

语言扩展（`UPDATE` / `ORDER BY` / `GROUP BY` / `JOIN` / `DISTINCT` / `LIMIT`）**暂不实现**（PPT 第 35 页：扩展用于区分度，不追求 SQL 越多越好）。已做的扩展：AST/Plan 的 **JSON 序列化输出**（PPT 第 30 页「树形 / JSON / S-expression 任选其一」）。

---

## 四、各阶段输出示例

以 `SELECT name FROM student WHERE 1 = 1 AND age > 10 + 8;` 为例（优化前后，PPT 第 34 页示例）：

```
④ 逻辑执行计划（AST → Plan）
Project[name]
└─ Filter[1 = 1 AND age > 10 + 8]
   └─ SeqScan[student]

⑤ 规则优化：优化前 vs 优化后
优化前:
Project[name]
└─ Filter[1 = 1 AND age > 10 + 8]
   └─ SeqScan[student]
优化后:
Project[name]
└─ Filter[age > 18]
   └─ SeqScan[student]
```

`1 = 1` 被常量折叠成 `TRUE` 再被布尔化简消掉，`10 + 8` 折叠成 `18`。

同一棵优化后的计划，JSON 形式（第 ⑥ 步输出）：

```json
{
  "kind": "Project",
  "columns": ["name"],
  "child": {
    "kind": "Filter",
    "predicate": {"kind":"BinaryExpr","op":">","left":{"kind":"ColumnRef","name":"age"},"right":{"kind":"Literal","type":"INT","value":18}},
    "child": { "kind": "SeqScan", "table": "student", "purpose": "SELECT" }
  }
}
```

---

## 五、测试

`tests/` 目录（详见 `tests/README.md`）：

- **正常测试**：`demo.sql`（端到端四语句）
- **词法错误**：`lexer_illegal_char.sql`、`lexer_unclosed_string.sql`
- **语法错误**：`parser_missing_semicolon.sql`、`parser_bad_paren.sql`
- **语义错误**：`semantic_table_not_exist.sql`、`semantic_column_not_exist.sql`、`semantic_type_mismatch.sql`、`semantic_insert_count.sql`、`semantic_dup_table.sql`、`semantic_arith_type.sql`
- **边界测试**：`edge_empty.sql`、`edge_case_sensitive.sql`、`edge_multi_stmt.sql`、`edge_long_ident.sql`

非法输入一律输出 `LexerError / SyntaxError / SemanticError at line x, column y: ...` 并以退出码 1 结束，绝不崩溃。

---

## 六、交付物清单（对应 PPT 第 42 页）

- ✅ 源码：模块化源码 + README + 构建/运行说明
- ✅ 设计：`grammar.md` + AST / Catalog / Plan 结构说明（各源码类的 Javadoc）
- ✅ 测试：测试用例 + 运行结果 + 失败案例分析（`tests/README.md`）
- ✅ 输出：Token → AST → Semantic → Plan → Optimized Plan → JSON（`Main` 一次性打印）
- ✅ 报告：关键设计决策见 `docs/答辩手册.md`；AI 辅助使用说明见下文

### 关于 AI 辅助（供报告参考）

本项目代码在 AI 辅助下完成，但遵循 PPT 第 39/40 页的原则：**AI 可以写代码，不能替人理解、验证、集成、讲解**。具体的理解、逐模块讲解、测试验证与现场答辩，均需本人完成。
