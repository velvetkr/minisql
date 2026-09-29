package com.minimysql.engine.storage_engine; // 声明当前 Java 文件所属的包。

import java.util.Collections; // 引入当前行使用的外部类型或工具。
import java.util.LinkedHashMap; // 引入当前行使用的外部类型或工具。
import java.util.Map; // 引入当前行使用的外部类型或工具。

/**
 * 一条带列名的数据库记录。
 *
 * <p>使用 LinkedHashMap 保留列的插入顺序，既方便稳定地打印查询结果，也保证
 * 序列化时的字段顺序与表定义一致。构造后通过不可变视图暴露，避免记录被意外修改。</p>
 */
public record Row(Map<String, Object> values) { // 定义当前文件对外提供的核心类型。
    public Row { // 声明当前方法或成员，并限定其访问范围。
        // 复制调用方传入的 Map，防止外部后续修改影响当前记录。
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); // 执行当前语句，更新状态或调用下层组件。
    } // 结束当前代码块。

    /** 大小写不敏感地读取指定列；列不存在时返回 null。 */
    public Object get(String column) { // 声明当前方法或成员，并限定其访问范围。
        for (var entry : values.entrySet()) { // 遍历集合或重复执行当前循环体。
            if (entry.getKey().equalsIgnoreCase(column)) { // 根据条件决定是否执行下面的分支。
                return entry.getValue(); // 返回当前方法计算出的结果。
            } // 结束当前代码块。
        } // 结束当前代码块。
        return null; // 返回当前方法计算出的结果。
    } // 结束当前代码块。
} // 结束当前代码块。
