package com.metrocarlo.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.metrocarlo.common.JsonAppender.INDENT;
import static com.metrocarlo.common.JsonAppender.LF;
import static com.metrocarlo.common.JsonAppender.appendDoubleField;
import static com.metrocarlo.common.JsonAppender.appendIntArrayField;
import static com.metrocarlo.common.JsonAppender.appendIntField;
import static com.metrocarlo.common.JsonAppender.appendIntMatrixField;
import static com.metrocarlo.common.JsonAppender.appendStringArrayField;
import static com.metrocarlo.common.JsonAppender.appendStringField;
import static com.metrocarlo.common.JsonAppender.endLastField;
import static com.metrocarlo.common.JsonAppender.indent;

/**
 * 算法对比实验结果（results.json）的写盘（intention-16）：目录组织沿用对比实验的
 * run 目录惯例，每次运行写入新目录 data/algorithm-compare/run-&lt;yyyyMMdd-HHmmss&gt;/results.json，
 * 不覆盖旧运行；同秒冲突时目录名追加 -2、-3…。JSON 为手写轻量序列化（零新依赖），
 * 底层拼接原语统一由 {@link JsonAppender} 提供（2 空格缩进、LF 换行、无尾随逗号）。
 * <p>
 * 文件结构：meta（实验参数，含固定探索率 epsilon 与算法标识序列）→ maps
 * （每张地图 → 各算法的重复奖励与统计量）。与策略对比实验（intention-7，
 * data/compare/run-*）分属不同 run 根目录，两套 schema 互不影响。
 */
public final class AlgorithmCompareWriter {

    /** 算法对比结果根目录（相对项目根；运行产物，已加入 .gitignore） */
    private static final Path COMPARE_ROOT = Path.of("data", "algorithm-compare");
    /** 结果文件固定名 */
    private static final String RESULT_FILE_NAME = "results.json";
    /** run 目录时间戳格式：本地时间到秒，字典序 = 时间序（Python 端取最新运行无需索引文件） */
    private static final DateTimeFormatter RUN_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** exportedAt 时间格式：ISO-8601 到秒（与 trace meta 约定一致） */
    private static final DateTimeFormatter EXPORTED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private AlgorithmCompareWriter() {
    }

    /**
     * 补充 exportedAt 并把结果写入新 run 目录。
     *
     * @param meta 实验参数（exportedAt 字段由本方法写盘时补充）
     * @param maps 各地图结果，顺序与实验运行顺序一致
     * @return 写入的 results.json 完整路径
     */
    public static Path write(AlgorithmMeta meta, List<MapResult<AlgorithmResult>> maps) throws IOException {
        Path runDirectory = createRunDirectory();
        Path resultFile = runDirectory.resolve(RESULT_FILE_NAME);
        String exportedAt = LocalDateTime.now().format(EXPORTED_AT);
        Files.writeString(resultFile, toJson(meta, maps, exportedAt), StandardCharsets.UTF_8);
        return resultFile;
    }

    /** 逐级创建 data/algorithm-compare/run-&lt;时间戳&gt;，同秒冲突时追加 -2、-3…，返回 run 目录 */
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
    static String toJson(AlgorithmMeta meta, List<MapResult<AlgorithmResult>> maps, String exportedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{").append(LF);
        appendMeta(sb, meta, exportedAt);
        appendMaps(sb, maps);
        sb.append("}").append(LF);
        return sb.toString();
    }

    /** meta 对象：version 起、exportedAt 止（exportedAt 为最后字段，需去尾随逗号） */
    private static void appendMeta(StringBuilder sb, AlgorithmMeta m, String exportedAt) {
        sb.append(INDENT).append("\"meta\": {").append(LF);
        appendIntField(sb, 2, "version", AlgorithmMeta.VERSION);
        appendIntField(sb, 2, "episodes", m.episodes());
        appendIntField(sb, 2, "repetitions", m.repetitions());
        appendDoubleField(sb, 2, "alpha", m.alpha());
        appendDoubleField(sb, 2, "gamma", m.gamma());
        appendIntField(sb, 2, "maxStepsPerEpisode", m.maxStepsPerEpisode());
        appendDoubleField(sb, 2, "epsilon", m.epsilon());
        appendStringArrayField(sb, 2, "algorithms", toArray(m.algorithms()));
        appendStringField(sb, 2, "exportedAt", exportedAt);
        endLastField(sb);
        sb.append(INDENT).append("},").append(LF);
    }

    /** maps 数组：每元素一张地图 */
    private static void appendMaps(StringBuilder sb, List<MapResult<AlgorithmResult>> maps) {
        sb.append(INDENT).append("\"maps\": [").append(LF);
        for (int i = 0; i < maps.size(); i++) {
            appendMap(sb, maps.get(i));
            sb.append(i == maps.size() - 1 ? LF : ",").append(LF);
        }
        sb.append(INDENT).append("]").append(LF);
    }

    /** 单张地图对象：mapPath 起、algorithms 止（algorithms 为最后字段，天然无尾随逗号） */
    private static void appendMap(StringBuilder sb, MapResult<AlgorithmResult> map) {
        sb.append(INDENT).append(INDENT).append("{").append(LF);
        appendStringField(sb, 3, "mapPath", map.mapPath());
        appendIntField(sb, 3, "width", map.width());
        appendIntField(sb, 3, "height", map.height());
        appendIntMatrixField(sb, 3, "initialMap", map.initialMap());
        appendAlgorithms(sb, map.entries());
        sb.append(INDENT).append(INDENT).append("}");
    }

    /** algorithms 数组：每元素一种算法的结果（顺序与 meta.algorithms 一致） */
    private static void appendAlgorithms(StringBuilder sb, List<AlgorithmResult> algorithms) {
        sb.append(indent(3)).append("\"algorithms\": [").append(LF);
        for (int i = 0; i < algorithms.size(); i++) {
            appendAlgorithm(sb, algorithms.get(i));
            sb.append(i == algorithms.size() - 1 ? LF : ",").append(LF);
        }
        sb.append(indent(3)).append("]").append(LF);
    }

    /** 单个算法结果对象：algorithm 起、terminatedCount 止（最后字段需去尾随逗号） */
    private static void appendAlgorithm(StringBuilder sb, AlgorithmResult a) {
        sb.append(indent(4)).append("{").append(LF);
        appendStringField(sb, 5, "algorithm", a.algorithm());
        appendIntArrayField(sb, 5, "repRewards", toIntArray(a.repRewards()));
        appendDoubleField(sb, 5, "meanReward", a.meanReward());
        appendDoubleField(sb, 5, "stdReward", a.stdReward());
        appendIntField(sb, 5, "terminatedCount", a.terminatedCount());
        endLastField(sb);
        sb.append(indent(4)).append("}");
    }

    /** List&lt;String&gt; → String[]（JsonAppender 的数组字段按基本类型/字符串数组拼接） */
    private static String[] toArray(List<String> values) {
        return values.toArray(new String[0]);
    }

    /** List&lt;Integer&gt; → int[]（同上） */
    private static int[] toIntArray(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }
}
