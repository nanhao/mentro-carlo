package com.metrocarlo.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.metrocarlo.common.JsonAppender;

import static com.metrocarlo.common.JsonAppender.INDENT;
import static com.metrocarlo.common.JsonAppender.LF;
import static com.metrocarlo.common.JsonAppender.appendDoubleArrayField;
import static com.metrocarlo.common.JsonAppender.appendDoubleField;
import static com.metrocarlo.common.JsonAppender.appendIntArrayField;
import static com.metrocarlo.common.JsonAppender.appendIntField;
import static com.metrocarlo.common.JsonAppender.appendIntMatrixField;
import static com.metrocarlo.common.JsonAppender.appendNullableDoubleField;
import static com.metrocarlo.common.JsonAppender.appendStringField;
import static com.metrocarlo.common.JsonAppender.endLastField;
import static com.metrocarlo.common.JsonAppender.indent;

/**
 * 策略对比实验结果（results.json）的写盘：目录组织沿用 trace 的 run 目录惯例，
 * 每次运行写入新目录 data/compare/run-&lt;yyyyMMdd-HHmmss&gt;/results.json，不覆盖旧运行；
 * 同秒冲突时目录名追加 -2、-3…。JSON 为手写轻量序列化（零新依赖），
 * 底层拼接原语统一由 {@link JsonAppender} 提供（2 空格缩进、LF 换行、无尾随逗号）。
 * <p>
 * 文件结构：meta（实验参数）→ maps（每张地图 → 各策略配置的重复奖励与统计量）。
 */
public final class CompareResultWriter {

    /** 对比结果根目录（相对项目根；运行产物，已加入 .gitignore） */
    private static final Path COMPARE_ROOT = Path.of("data", "compare");
    /** 结果文件固定名 */
    private static final String RESULT_FILE_NAME = "results.json";
    /** run 目录时间戳格式：本地时间到秒，字典序 = 时间序（Python 端取最新运行无需索引文件） */
    private static final DateTimeFormatter RUN_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** exportedAt 时间格式：ISO-8601 到秒（与 trace meta 约定一致） */
    private static final DateTimeFormatter EXPORTED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private CompareResultWriter() {
    }

    /**
     * 补充 exportedAt 并把结果写入新 run 目录。
     *
     * @param meta 实验参数（exportedAt 字段由本方法写盘时补充）
     * @param maps 各地图结果，顺序与实验运行顺序一致
     * @return 写入的 results.json 完整路径
     */
    public static Path write(ExperimentMeta meta, List<MapResult<PolicyResult>> maps) throws IOException {
        Path runDirectory = createRunDirectory();
        Path resultFile = runDirectory.resolve(RESULT_FILE_NAME);
        String exportedAt = LocalDateTime.now().format(EXPORTED_AT);
        Files.writeString(resultFile, toJson(meta, maps, exportedAt), StandardCharsets.UTF_8);
        return resultFile;
    }

    /** 逐级创建 data/compare/run-&lt;时间戳&gt;，同秒冲突时追加 -2、-3…，返回 run 目录 */
    private static Path createRunDirectory() throws IOException {
        Files.createDirectories(COMPARE_ROOT);
        String baseName = "run-" + LocalDateTime.now().format(RUN_TIMESTAMP);
        Path runDirectory = COMPARE_ROOT.resolve(baseName);
        int suffix = 2;
        while (Files.exists(runDirectory)) {
            runDirectory = COMPARE_ROOT.resolve(baseName + "-" + suffix++);
        }
        Files.createDirectories(runDirectory);
        return runDirectory;
    }

    /** 把整份结果序列化为 JSON 字符串（以换行结尾）；exportedAt 由写盘入口补充 */
    static String toJson(ExperimentMeta meta, List<MapResult<PolicyResult>> maps, String exportedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{").append(LF);
        appendMeta(sb, meta, exportedAt);
        appendMaps(sb, maps);
        sb.append("}").append(LF);
        return sb.toString();
    }

    /** meta 对象：version 起、exportedAt 止（exportedAt 为最后字段，需去尾随逗号） */
    private static void appendMeta(StringBuilder sb, ExperimentMeta m, String exportedAt) {
        sb.append(INDENT).append("\"meta\": {").append(LF);
        appendIntField(sb, 2, "version", ExperimentMeta.VERSION);
        appendIntField(sb, 2, "episodes", m.episodes());
        appendIntField(sb, 2, "repetitions", m.repetitions());
        appendDoubleField(sb, 2, "alpha", m.alpha());
        appendDoubleField(sb, 2, "gamma", m.gamma());
        appendIntField(sb, 2, "maxStepsPerEpisode", m.maxStepsPerEpisode());
        appendDoubleArrayField(sb, 2, "epsilons", toArray(m.epsilons()));
        appendStringField(sb, 2, "exportedAt", exportedAt);
        endLastField(sb);
        sb.append(INDENT).append("},").append(LF);
    }

    /** maps 数组：每元素一张地图 */
    private static void appendMaps(StringBuilder sb, List<MapResult<PolicyResult>> maps) {
        sb.append(INDENT).append("\"maps\": [").append(LF);
        for (int i = 0; i < maps.size(); i++) {
            appendMap(sb, maps.get(i));
            sb.append(i == maps.size() - 1 ? LF : ",").append(LF);
        }
        sb.append(INDENT).append("]").append(LF);
    }

    /** 单张地图对象：mapPath 起、policies 止（policies 为最后字段，天然无尾随逗号） */
    private static void appendMap(StringBuilder sb, MapResult<PolicyResult> map) {
        sb.append(INDENT).append(INDENT).append("{").append(LF);
        appendStringField(sb, 3, "mapPath", map.mapPath());
        appendIntField(sb, 3, "width", map.width());
        appendIntField(sb, 3, "height", map.height());
        appendIntMatrixField(sb, 3, "initialMap", map.initialMap());
        appendPolicies(sb, map.entries());
        sb.append(INDENT).append(INDENT).append("}");
    }

    /** policies 数组：每元素一个策略配置结果 */
    private static void appendPolicies(StringBuilder sb, List<PolicyResult> policies) {
        sb.append(indent(3)).append("\"policies\": [").append(LF);
        for (int i = 0; i < policies.size(); i++) {
            appendPolicy(sb, policies.get(i));
            sb.append(i == policies.size() - 1 ? LF : ",").append(LF);
        }
        sb.append(indent(3)).append("]").append(LF);
    }

    /** 单个策略配置对象：policy 起、terminatedCount 止（最后字段需去尾随逗号） */
    private static void appendPolicy(StringBuilder sb, PolicyResult p) {
        sb.append(indent(4)).append("{").append(LF);
        appendStringField(sb, 5, "policy", p.policy());
        appendNullableDoubleField(sb, 5, "epsilon", p.epsilon());
        appendIntArrayField(sb, 5, "repRewards", toIntArray(p.repRewards()));
        appendDoubleField(sb, 5, "meanReward", p.meanReward());
        appendDoubleField(sb, 5, "stdReward", p.stdReward());
        appendIntField(sb, 5, "terminatedCount", p.terminatedCount());
        endLastField(sb);
        sb.append(indent(4)).append("}");
    }

    /** List&lt;Double&gt; → double[]（JsonAppender 的数组字段按基本类型数组拼接） */
    private static double[] toArray(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** List&lt;Integer&gt; → int[]（同上） */
    private static int[] toIntArray(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }
}
