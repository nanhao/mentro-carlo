package com.metrocarlo.common;

/**
 * 手写 JSON 序列化的底层拼接工具（零新依赖），统一序列化外观约定：
 * 2 空格缩进、LF 换行；double 原样输出、整数语义字段不带 .0；
 * 字段一律以「逗号 + 换行」结尾，对象最后一个字段调用 {@link #endLastField}
 * 去掉尾随逗号（JSON 禁止尾随逗号）。
 * <p>
 * 各序列化器（如 history.TraceJsonWriter、experiment.CompareResultWriter）
 * 负责各自 JSON 的结构与字段顺序，本类只提供单个字段/数组的拼接原语与最小转义。
 */
public final class JsonAppender {

    /** 单级缩进（2 空格） */
    public static final String INDENT = "  ";
    /** 换行符：统一 LF（文件按 UTF-8 无 BOM 写出，由写盘方保证） */
    public static final String LF = "\n";

    private JsonAppender() {
    }

    /** 返回 depth 级缩进（每级 2 空格） */
    public static String indent(int depth) {
        return INDENT.repeat(depth);
    }

    /** 整型字段：整数书写不带 .0 */
    public static void appendIntField(StringBuilder sb, int depth, String name, int value) {
        appendRawField(sb, depth, name, Integer.toString(value));
    }

    /** double 字段：原样输出，不四舍五入 */
    public static void appendDoubleField(StringBuilder sb, int depth, String name, double value) {
        appendRawField(sb, depth, name, Double.toString(value));
    }

    /** 可空整数（Long）字段：值未定（如未固定的 seed）时输出 JSON null */
    public static void appendNullableLongField(StringBuilder sb, int depth, String name, Long value) {
        appendRawField(sb, depth, name, value == null ? "null" : value.toString());
    }

    /** 可空 double 字段：无值语义（如 random 策略的 epsilon）时输出 JSON null */
    public static void appendNullableDoubleField(StringBuilder sb, int depth, String name, Double value) {
        appendRawField(sb, depth, name, value == null ? "null" : Double.toString(value));
    }

    /** 字符串字段；值为 null 时输出 JSON null（如导出入口补充前的 mapPath） */
    public static void appendNullableStringField(StringBuilder sb, int depth, String name, String value) {
        if (value == null) {
            appendRawField(sb, depth, name, "null");
            return;
        }
        appendStringField(sb, depth, name, value);
    }

    /** 字符串字段：JSON 转义后带引号写出 */
    public static void appendStringField(StringBuilder sb, int depth, String name, String value) {
        sb.append(indent(depth)).append('"').append(name).append("\": ");
        appendQuoted(sb, value);
        sb.append(',').append(LF);
    }

    /** 一维 int 数组字段：单行书写、元素以 ", " 分隔（如重复奖励列表） */
    public static void appendIntArrayField(StringBuilder sb, int depth, String name, int[] values) {
        sb.append(indent(depth)).append('"').append(name).append("\": [");
        appendIntElements(sb, values);
        sb.append("],").append(LF);
    }

    /** 一维 double 数组字段：单行书写、元素以 ", " 分隔（如 ε 扫描序列） */
    public static void appendDoubleArrayField(StringBuilder sb, int depth, String name, double[] values) {
        sb.append(indent(depth)).append('"').append(name).append("\": [");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values[i]);
        }
        sb.append("],").append(LF);
    }

    /**
     * 二维整型网格字段（如按 CSV 行序排列的初始地图）：内层每个数组一行、
     * 元素以 ", " 分隔；末行不带逗号。
     */
    public static void appendIntMatrixField(StringBuilder sb, int depth, String name, int[][] matrix) {
        String in = indent(depth);
        sb.append(in).append('"').append(name).append("\": [").append(LF);
        for (int row = 0; row < matrix.length; row++) {
            sb.append(INDENT.repeat(depth + 1)).append('[');
            appendIntElements(sb, matrix[row]);
            sb.append(']').append(row == matrix.length - 1 ? "" : ",").append(LF);
        }
        sb.append(in).append("],").append(LF);
    }

    /** 数值等原始字面量字段（调用方负责拼好值本身，本方法统一补逗号换行） */
    public static void appendRawField(StringBuilder sb, int depth, String name, String rawValue) {
        sb.append(indent(depth)).append('"').append(name).append("\": ").append(rawValue).append(',').append(LF);
    }

    /**
     * 结束对象内最后一个字段：字段方法统一以 ",\n" 结尾，而 JSON 对象最后一个字段
     * 不允许尾随逗号——移除该后缀并补回行尾换行（需在对象闭合行之前调用）。
     */
    public static void endLastField(StringBuilder sb) {
        sb.setLength(sb.length() - 2);
        sb.append(LF);
    }

    /** 写带引号字符串并做最小 JSON 转义（引号/反斜杠/控制字符） */
    public static void appendQuoted(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /** 拼接一维 int 数组元素（不写方括号），元素以 ", " 分隔 */
    private static void appendIntElements(StringBuilder sb, int[] values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values[i]);
        }
    }
}
