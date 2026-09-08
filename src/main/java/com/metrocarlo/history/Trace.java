package com.metrocarlo.history;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 一次算法运行的完整 trace（JSON 文档的顶层结构），
 * 组织方式对应 design/3/README.md §三：meta 头 + episodes 数组。
 * <p>
 * 由各算法采集生成（MonteCarlo / Sarsa，采集算法见 {@link TraceMeta#getAlgorithm()}，
 * meta 由 {@code com.metrocarlo.montecarlo.TraceFactory} 统一构建），
 * 供 GUI 回放与 S/Q 表重建使用（montecarlo 重建契约见 design/3 §四：S/Q 从全 0 出发，
 * 按文件序遍历各行覆盖写 sNew/qNew；sarsa 无回合末结算，逐行覆盖写 qNew，见 design/12 §六）。
 */
@Data
@Builder
public class Trace {

    /** 元信息：运行参数、地图与导出上下文 */
    private TraceMeta meta;
    /** 全部 episode 记录，列表顺序即 episode 执行顺序（index 从 1 连续递增） */
    @Builder.Default
    private List<EpisodeTrace> episodes = new ArrayList<>();
}
