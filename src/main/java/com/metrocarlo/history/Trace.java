package com.metrocarlo.history;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 一次蒙特卡洛运行的完整 trace（JSON 文档的顶层结构），
 * 组织方式对应 design/3/README.md §三：meta 头 + episodes 数组。
 * <p>
 * 由 {@link com.metrocarlo.montecarlo.MonteCarlo} 在 buildQValuesAndSValues() 中采集生成，
 * 供 GUI 回放与 S/Q 表重建使用（重建契约见 design/3 §四：S/Q 从全 0 出发，
 * 按文件序遍历各行覆盖写 sNew/qNew 即可与 Java 端演进完全一致）。
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
