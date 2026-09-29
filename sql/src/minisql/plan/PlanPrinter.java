package minisql.plan;

import minisql.ast.ExprPrinter;

/**
 * 把逻辑计划树打印成"带缩进的树形结构"，风格与 {@code AstPrinter} 一致。
 *
 * <p>因为计划是单链（每节点最多一个子节点），打印出来就是一条从上到下的链：</p>
 * <pre>
 *   Project[name]
 *   └─ Filter[age > 18]
 *      └─ SeqScan[student]
 * </pre>
 *
 * <p>这正是 PPT 第 32 页要求的输出形式。</p>
 */
public class PlanPrinter {

    /** 打印一棵计划树。 */
    public static String print(Plan plan) {
        StringBuilder sb = new StringBuilder();
        printNode(plan, sb, "");
        return sb.toString();
    }

    /** {@code indent} 是当前节点前面已累积的缩进（含 {@code └─ } 连接符）。 */
    private static void printNode(Plan p, StringBuilder sb, String indent) {
        sb.append(indent).append(nodeText(p)).append("\n");
        if (p.child != null) {
            // 进入下一层：把本层的 "└─ " 换成等宽空白，再接上下一层的 "└─ "
            String childIndent = indent.replace("└─ ", "   ");
            printNode(p.child, sb, childIndent + "└─ ");
        }
    }

    /** 每个节点显示的一行文字，例如 {@code Filter[age > 18]}。 */
    private static String nodeText(Plan p) {
        if (p instanceof SeqScanPlan) {
            SeqScanPlan s = (SeqScanPlan) p;
            return s.purpose.equals("DELETE")
                    ? "SeqScan[" + s.table.name + "] (DELETE)"
                    : "SeqScan[" + s.table.name + "]";
        }
        if (p instanceof FilterPlan) {
            FilterPlan f = (FilterPlan) p;
            return "Filter[" + ExprPrinter.print(f.predicate) + "]";
        }
        if (p instanceof ProjectPlan) {
            ProjectPlan proj = (ProjectPlan) p;
            return "Project[" + String.join(", ", proj.columns) + "]";
        }
        if (p instanceof CreateTablePlan) {
            return "CreateTable[" + ((CreateTablePlan) p).tableName + "]";
        }
        if (p instanceof InsertPlan) {
            return "Insert[" + ((InsertPlan) p).table.name + "]";
        }
        return p.getClass().getSimpleName();
    }
}
