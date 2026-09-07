package com.metrocarlo.experiment;

import java.util.List;

/**
 * 单张地图的完整对比结果：地图标识信息 + 各策略配置的实验结果。
 *
 * @param mapPath    源地图相对路径（如 map/1/map.csv）
 * @param width      地图宽度（列数）
 * @param height     地图高度（行数）
 * @param initialMap 初始地图网格（CSV 行序，首行 = 最上排，与 trace meta.initialMap 约定一致）
 * @param policies   各策略配置结果，顺序固定：random 在前，其后按 ε 自小到大
 */
public record MapResult(String mapPath, int width, int height, int[][] initialMap,
                        List<PolicyResult> policies) {
}
