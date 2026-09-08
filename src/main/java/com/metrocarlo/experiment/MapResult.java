package com.metrocarlo.experiment;

import java.util.List;

/**
 * 单张地图的完整对比结果：地图标识信息 + 各实验配置的结果列表。
 * <p>
 * entries 元素类型由实验决定，两个批量对比实验入口共用本容器：
 * PolicyComparison 装入 {@link PolicyResult}（策略配置，intention-7），
 * AlgorithmComparison 装入 {@link AlgorithmResult}（算法，intention-16）。
 *
 * @param mapPath    源地图相对路径（如 map/1/map.csv）
 * @param width      地图宽度（列数）
 * @param height     地图高度（行数）
 * @param initialMap 初始地图网格（CSV 行序，首行 = 最上排，与 trace meta.initialMap 约定一致）
 * @param entries    各配置结果，顺序与实验运行顺序一致
 */
public record MapResult<E>(String mapPath, int width, int height, int[][] initialMap,
                           List<E> entries) {
}
