package com.metrocarlo.world.history;

import java.util.List;

/**
 * trace 数据的手写轻量 JSON 序列化器（零新依赖），输出格式满足
 * design/3/README.md §六 的文件格式约束与 §3.2 结构约定：
 * 2 空格缩进、LF 换行；double 原样输出、整数语义字段不带 .0；
 * 字段顺序与 §3.1 字段字典一致（meta → episodes → visits）。
 * <p>
 * 本类只做序列化（Trace → JSON 字符串），目录组织与写盘见 {@link TraceFileWriter}。
 */
public final class TraceJsonWriter {

    /** 单级缩进（2 空格） */
    private static final String INDENT = "  ";
    /** 换行符：统一 LF（文件按 UTF-8 无 BOM 写出） */
    private static final String LF = "\n";

    private TraceJsonWriter() {
    }

    /** 把整份 trace 序列化为 JSON 字符串（以换行结尾） */
    public static String toJson(Trace trace) {
        StringBuilder sb = new StringBuilder();
        sb.append("{").append(LF);
        appendMeta(sb, trace.getMeta());
        appendEpisodes(sb, trace.getEpisodes());
        sb.append("}").append(LF);
        return sb.toString();
    }

    /** meta 对象：字段顺序与 §3.1 字典一致（version 起、exportedAt 止） */
    private static void appendMeta(StringBuilder sb, TraceMeta m) {
        sb.append(INDENT).append("\"meta\": {").append(LF);
        appendIntField(sb, 2, "version", m.getVersion());
        appendNullableStringField(sb, 2, "mapPath", m.getMapPath());
        appendIntField(sb, 2, "width", m.getWidth());
        appendIntField(sb, 2, "height", m.getHeight());
        appendIntMatrixField(sb, 2, "initialMap", m.getInitialMap());
        appendIntField(sb, 2, "startX", m.getStartX());
        appendIntField(sb, 2, "startY", m.getStartY());
        appendDoubleField(sb, 2, "alpha", m.getAlpha());
        appendDoubleField(sb, 2, "gamma", m.getGamma());
        appendDoubleField(sb, 2, "epsilon", m.getEpsilon());
        appendIntField(sb, 2, "maxEpisode", m.getMaxEpisode());
        appendIntField(sb, 2, "maxStepsPerEpisode", m.getMaxStepsPerEpisode());
        appendNullableLongField(sb, 2, "seed", m.getSeed());
        appendStringField(sb, 2, "exportedAt", m.getExportedAt());
        endLastField(sb); // exportedAt 为 meta 最后一个字段：去掉行尾逗号（JSON 禁止尾随逗号）
        sb.append(INDENT).append("},").append(LF);
    }

    /** episodes 数组：每元素一个 episode 对象 */
    private static void appendEpisodes(StringBuilder sb, List<EpisodeTrace> episodes) {
        sb.append(INDENT).append("\"episodes\": [").append(LF);
        for (int i = 0; i < episodes.size(); i++) {
            appendEpisode(sb, episodes.get(i));
            sb.append(i == episodes.size() - 1 ? LF : ",").append(LF);
        }
        sb.append(INDENT).append("]").append(LF);
    }

    /** episode 对象：index / totalReward / endedBy / visits（visits 为最后字段，天然无尾随逗号） */
    private static void appendEpisode(StringBuilder sb, EpisodeTrace episode) {
        sb.append(INDENT).append(INDENT).append("{").append(LF);
        appendIntField(sb, 3, "index", episode.getIndex());
        appendIntField(sb, 3, "totalReward", episode.getTotalReward());
        appendStringField(sb, 3, "endedBy", episode.getEndedBy());
        appendVisits(sb, episode.getVisits());
        sb.append(INDENT).append(INDENT).append("}");
    }

    /** visits 数组：结算循环顺序（回合倒序）；每元素一次状态访问 */
    private static void appendVisits(StringBuilder sb, List<VisitRecord> visits) {
        sb.append(indent(3)).append("\"visits\": [").append(LF);
        for (int i = 0; i < visits.size(); i++) {
            appendVisit(sb, visits.get(i));
            sb.append(i == visits.size() - 1 ? LF : ",").append(LF);
        }
        sb.append(indent(3)).append("]").append(LF);
    }

    /** visit 对象：字段顺序与 §3.1 visits 字典一致（no 起、qNew 止） */
    private static void appendVisit(StringBuilder sb, VisitRecord v) {
        sb.append(indent(4)).append("{").append(LF);
        appendIntField(sb, 5, "no", v.getNo());
        appendIntField(sb, 5, "x", v.getX());
        appendIntField(sb, 5, "y", v.getY());
        appendStringField(sb, 5, "action", v.getAction());
        appendIntField(sb, 5, "reward", v.getReward());
        appendIntField(sb, 5, "totalReward", v.getTotalReward());
        appendIntField(sb, 5, "nextX", v.getNextX());
        appendIntField(sb, 5, "nextY", v.getNextY());
        appendIntField(sb, 5, "fromType", v.getFromType());
        appendIntField(sb, 5, "toType", v.getToType());
        appendDoubleField(sb, 5, "discountedNextG", v.getDiscountedNextG());
        appendDoubleField(sb, 5, "G", v.getG());
        appendDoubleField(sb, 5, "sOld", v.getSOld());
        appendDoubleField(sb, 5, "sNew", v.getSNew());
        appendDoubleField(sb, 5, "qOld", v.getQOld());
        appendDoubleField(sb, 5, "qNew", v.getQNew());
        endLastField(sb); // qNew 为 visit 最后一个字段：去掉行尾逗号（JSON 禁止尾随逗号）
        sb.append(indent(4)).append("}");
    }

    /** 字符串字段；值为 null 时输出 JSON null（mapPath 在导出入口补充前可能为空） */
    private static void appendNullableStringField(StringBuilder sb, int depth, String name, String value) {
        if (value == null) {
            appendRawField(sb, depth, name, "null");
            return;
        }
        appendStringField(sb, depth, name, value);
    }

    /** 可空整数（Long）字段：seed 未固定时为 null */
    private static void appendNullableLongField(StringBuilder sb, int depth, String name, Long value) {
        appendRawField(sb, depth, name, value == null ? "null" : value.toString());
    }

    /** 整型字段：整数书写不带 .0 */
    private static void appendIntField(StringBuilder sb, int depth, String name, int value) {
        appendRawField(sb, depth, name, Integer.toString(value));
    }

    /** double 字段：原样输出，不四舍五入 */
    private static void appendDoubleField(StringBuilder sb, int depth, String name, double value) {
        appendRawField(sb, depth, name, Double.toString(value));
    }

    /** 二维整型网格字段：CSV 行序；内层每个数组一行、元素以 ", " 分隔 */
    private static void appendIntMatrixField(StringBuilder sb, int depth, String name, int[][] matrix) {
        String in = indent(depth);
        sb.append(in).append('"').append(name).append("\": [").append(LF);
        for (int row = 0; row < matrix.length; row++) {
            sb.append(INDENT.repeat(depth + 1)).append('[');
            for (int col = 0; col < matrix[row].length; col++) {
                if (col > 0) {
                    sb.append(", ");
                }
                sb.append(matrix[row][col]);
            }
            sb.append(']').append(row == matrix.length - 1 ? "" : ",").append(LF);
        }
        sb.append(in).append("],").append(LF);
    }

    /** 字符串字段：JSON 转义后带引号写出 */
    private static void appendStringField(StringBuilder sb, int depth, String name, String value) {
        sb.append(indent(depth)).append('"').append(name).append("\": ");
        appendQuoted(sb, value);
        sb.append(',').append(LF);
    }

    /** 数值等原始字面量字段（调用方负责拼好值本身，本方法统一补逗号换行） */
    private static void appendRawField(StringBuilder sb, int depth, String name, String rawValue) {
        sb.append(indent(depth)).append('"').append(name).append("\": ").append(rawValue).append(',').append(LF);
    }

    /**
     * 结束对象内最后一个字段：字段方法统一以 ",\n" 结尾，而 JSON 对象最后一个字段
     * 不允许尾随逗号——移除该后缀并补回行尾换行（需在对象闭合行之前调用）。
     */
    private static void endLastField(StringBuilder sb) {
        sb.setLength(sb.length() - 2);
        sb.append(LF);
    }

    /** 写带引号字符串并做最小 JSON 转义（引号/反斜杠/控制字符） */
    private static void appendQuoted(StringBuilder sb, String value) {
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

    /** 返回 depth 级缩进（每级 2 空格） */
    private static String indent(int depth) {
        return INDENT.repeat(depth);
    }
}
