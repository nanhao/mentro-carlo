package com.metrocarlo.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.metrocarlo.common.Constants;

/**
 * trace 导出写盘：目录组织与命名规则见 design/3/README.md §二（sarsa 算法段增量见
 * design/12/README.md §二），序列化见 {@link TraceJsonWriter}。
 * <p>
 * 每次运行写入一个新 run 目录（data/trace/[算法段/]map-&lt;N&gt;/run-&lt;yyyyMMdd-HHmmss&gt;/trace.json），
 * 不覆盖旧运行；同一秒内多次运行时目录名追加 -2、-3…。算法段由 meta.algorithm 决定：
 * montecarlo 省略（兼容既有布局与 viewer 发现规则），其余算法取 algorithm 值作段名。
 * 写入前补全 meta 中属导出上下文的字段（mapPath / exportedAt）。
 */
public final class TraceFileWriter {

    /** trace 根目录（相对项目根；运行产物，已加入 .gitignore） */
    private static final Path TRACE_ROOT = Constants.TRACE_ROOT;
    /** 运行目录时间戳格式：本地时间到秒，字典序 = 时间序（GUI 取最新运行无需索引文件） */
    private static final DateTimeFormatter RUN_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** exportedAt 时间格式：ISO-8601 到秒 */
    private static final DateTimeFormatter EXPORTED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    /** 数据文件固定名 */
    private static final String TRACE_FILE_NAME = Constants.TRACE_FILE_NAME;

    private TraceFileWriter() {
    }

    /**
     * 补全导出上下文字段并把 trace 序列化写入新 run 目录。
     *
     * @param trace   待导出的 trace（meta 中算法上下文字段已由各算法采集时填写）
     * @param mapPath 源地图相对路径，如 map/1/map.csv（既写入 meta.mapPath，也用于推导 map-&lt;N&gt; 分组目录）
     * @return 写入的 trace.json 完整路径
     */
    public static Path write(Trace trace, String mapPath) throws IOException {
        TraceMeta meta = trace.getMeta();
        meta.setMapPath(mapPath);
        meta.setExportedAt(LocalDateTime.now().format(EXPORTED_AT));

        Path runDirectory = createRunDirectory(meta.getAlgorithm(), mapPath);
        Path traceFile = runDirectory.resolve(TRACE_FILE_NAME);
        Files.writeString(traceFile, TraceJsonWriter.toJson(trace), StandardCharsets.UTF_8);
        return traceFile;
    }

    /**
     * 逐级创建 data/trace/[算法段/]map-&lt;N&gt;/run-&lt;时间戳&gt;，同秒冲突时追加 -2、-3…，返回 run 目录。
     * 算法段：montecarlo 省略（兼容既有布局），其余取 meta.algorithm 值（design/12 §二）。
     */
    private static Path createRunDirectory(String algorithm, String mapPath) throws IOException {
        Path base = TRACE_ROOT;
        if (algorithm != null && !TraceMeta.ALGORITHM_MONTECARLO.equals(algorithm)) {
            base = base.resolve(algorithm);
        }
        Path groupDirectory = base.resolve(mapGroupName(mapPath));
        Files.createDirectories(groupDirectory);

        String baseName = "run-" + LocalDateTime.now().format(RUN_TIMESTAMP);
        Path runDirectory = groupDirectory.resolve(baseName);
        int suffix = 2;
        while (Files.exists(runDirectory)) {
            runDirectory = groupDirectory.resolve(baseName + "-" + suffix++);
        }
        Files.createDirectories(runDirectory);
        return runDirectory;
    }

    /**
     * 由源地图路径推导地图分组目录名：取源目录名加 map- 前缀。
     * 约定源地图位于 map/&lt;N&gt;/map.csv：如 map/1/map.csv → map-1；
     * 前缀避免与 map/&lt;N&gt; 纯数字目录混淆（design/3 §2.2）。
     */
    private static String mapGroupName(String mapPath) {
        Path parent = Path.of(mapPath).getParent();
        if (parent == null || parent.getFileName() == null) {
            throw new IllegalArgumentException("无法从地图路径推导 map-<N> 分组名: " + mapPath);
        }
        return "map-" + parent.getFileName();
    }
}
