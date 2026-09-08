package com.metrocarlo.history;

import java.util.List;

import static com.metrocarlo.common.JsonAppender.INDENT;
import static com.metrocarlo.common.JsonAppender.LF;
import static com.metrocarlo.common.JsonAppender.appendDoubleField;
import static com.metrocarlo.common.JsonAppender.appendIntField;
import static com.metrocarlo.common.JsonAppender.appendIntMatrixField;
import static com.metrocarlo.common.JsonAppender.appendNullableLongField;
import static com.metrocarlo.common.JsonAppender.appendNullableStringField;
import static com.metrocarlo.common.JsonAppender.appendStringField;
import static com.metrocarlo.common.JsonAppender.endLastField;
import static com.metrocarlo.common.JsonAppender.indent;

/**
 * trace 数据的手写轻量 JSON 序列化器（零新依赖），输出格式满足
 * design/3/README.md §六 的文件格式约束与 §3.2 结构约定（sarsa 增量见 design/12/README.md §五）：
 * 2 空格缩进、LF 换行；double 原样输出、整数语义字段不带 .0；
 * 字段顺序与字段字典一致（meta → episodes → visits）。
 * 底层拼接原语（缩进/字段/转义/去尾随逗号）由 {@link com.metrocarlo.common.JsonAppender} 提供。
 * <p>
 * visits 行按算法分派：montecarlo 行输出 G/sOld/sNew 组（与 v1 逐字节一致）；
 * sarsa 行输出嵌套 td 对象。本类只做序列化（Trace → JSON 字符串），
 * 目录组织与写盘见 {@link TraceFileWriter}。
 */
public final class TraceJsonWriter {

    /** 把整份 trace 序列化为 JSON 字符串（以换行结尾） */
    public static String toJson(Trace trace) {
        StringBuilder sb = new StringBuilder();
        sb.append("{").append(LF);
        appendMeta(sb, trace.getMeta());
        appendEpisodes(sb, trace.getEpisodes());
        sb.append("}").append(LF);
        return sb.toString();
    }

    /** meta 对象：字段顺序与字段字典一致（version/algorithm 起、exportedAt 止） */
    private static void appendMeta(StringBuilder sb, TraceMeta m) {
        sb.append(INDENT).append("\"meta\": {").append(LF);
        appendIntField(sb, 2, "version", m.getVersion());
        appendNullableStringField(sb, 2, "algorithm", m.getAlgorithm());
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

    /** visits 数组：行序按算法（montecarlo 结算倒序 / sarsa 执行正序）；每元素一行更新记录 */
    private static void appendVisits(StringBuilder sb, List<VisitRecord> visits) {
        sb.append(indent(3)).append("\"visits\": [").append(LF);
        for (int i = 0; i < visits.size(); i++) {
            appendVisit(sb, visits.get(i));
            sb.append(i == visits.size() - 1 ? LF : ",").append(LF);
        }
        sb.append(indent(3)).append("]").append(LF);
    }

    /** visit 对象：公共字段起，按算法分派算法专属字段组（montecarlo: G/sOld/sNew；sarsa: td），qOld/qNew 收尾 */
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
        if (v.getTd() != null) {
            appendTd(sb, v.getTd()); // sarsa 行：本步 TD 更新明细（design/12 §3.3）
        } else {
            appendDoubleField(sb, 5, "discountedNextG", v.getDiscountedNextG());
            appendDoubleField(sb, 5, "G", v.getG());
            appendDoubleField(sb, 5, "sOld", v.getSOld());
            appendDoubleField(sb, 5, "sNew", v.getSNew());
        }
        appendDoubleField(sb, 5, "qOld", v.getQOld());
        appendDoubleField(sb, 5, "qNew", v.getQNew());
        endLastField(sb); // qNew 为 visit 最后一个字段：去掉行尾逗号（JSON 禁止尾随逗号）
        sb.append(indent(4)).append("}");
    }

    /** td 嵌套对象：字段顺序 nextAction → nextQ → tdTarget → delta → alpha（design/12 §3.3） */
    private static void appendTd(StringBuilder sb, TdUpdateRecord td) {
        sb.append(indent(5)).append("\"td\": {").append(LF);
        appendNullableStringField(sb, 6, "nextAction", td.getNextAction());
        appendDoubleField(sb, 6, "nextQ", td.getNextQ());
        appendDoubleField(sb, 6, "tdTarget", td.getTdTarget());
        appendDoubleField(sb, 6, "delta", td.getDelta());
        appendDoubleField(sb, 6, "alpha", td.getAlpha());
        endLastField(sb); // alpha 为 td 最后一个字段：去掉行尾逗号
        sb.append(indent(5)).append("},").append(LF);
    }
}
