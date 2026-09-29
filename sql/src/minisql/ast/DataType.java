package minisql.ast;

/**
 * 列的数据类型。
 *
 * <p>PPT 第 23 页的简化类型系统里，值分四种类型。
 * 语义分析阶段就靠它来判断"两个类型能不能做某种运算"。</p>
 */
public enum DataType {
    INT,      // 整数，例如 20
    FLOAT,    // 小数，例如 3.14
    VARCHAR,  // 字符串，例如 'Alice'
    BOOL      // 布尔，例如 TRUE / FALSE
}
