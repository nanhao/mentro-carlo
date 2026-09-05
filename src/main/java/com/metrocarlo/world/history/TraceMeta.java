package com.metrocarlo.world.history;

import lombok.Builder;
import lombok.Data;

/**
 * trace 元信息（JSON 中 meta 对象），
 * 字段与 design/3/README.md §3.1「顶层 / meta 字段字典」一一对应。
 * <p>
 * 字段按生成时机拆分：地图与算法上下文（width/height/initialMap/start/alpha/gamma/epsilon/
 * maxEpisode/maxStepsPerEpisode/version）由 MonteCarlo 采集时填写；
 * mapPath/seed/exportedAt 属导出上下文，由导出入口（后续任务）补充，未填写时为 null。
 */
@Data
@Builder
public class TraceMeta {

    /** 当前 trace 格式版本号 */
    public static final int VERSION = 1;

    /** 格式版本号（schema 演进时递增，旧文件据此识别） */
    @Builder.Default
    private int version = VERSION;
    /** 源地图相对路径，如 map/1/map.csv（导出入口补充） */
    private String mapPath;
    /** 网格尺寸：列数 */
    private int width;
    /** 网格尺寸：行数 */
    private int height;
    /**
     * 初始类型网格，CSV 行序（首行 = y = height - 1，Python 端可按行绘制，零转换）；
     * 取值见 Constants.CELL_TYPE_*
     */
    private int[][] initialMap;
    /** agent 出发点 x（当前恒为 0，GUI 起始渲染与校验需要） */
    private int startX;
    /** agent 出发点 y（当前恒为 0） */
    private int startY;
    /** 学习率 α（实际取值） */
    private double alpha;
    /** 折扣因子 γ（实际取值） */
    private double gamma;
    /** 探索率 ε（实际取值；策略非 ε-贪婪时为默认值） */
    private double epsilon;
    /** 回合数上限 */
    private int maxEpisode;
    /** 单回合步数上限 */
    private int maxStepsPerEpisode;
    /** 随机种子（策略支持固定种子后填入；未固定为 null，导出入口补充） */
    private Long seed;
    /** 导出时间，ISO-8601 格式如 2026-09-05T10:30:00（导出入口补充） */
    private String exportedAt;
}
