package com.metrocarlo.history;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * trace 中单个 episode 的记录（JSON 中 episodes 数组的元素），
 * 字段与 design/3/README.md §3.1「episodes[] 字段字典」一一对应。
 * <p>
 * visits 行序按算法区分：montecarlo 按结算循环顺序存放（回合倒序，第 1 行 = 最后一步）；
 * sarsa 按执行正序存放（第 1 行 = 第 1 步，在线更新粒度，见 design/12/README.md §三）。
 * 行内 {@link VisitRecord#getNo()} 均为 episode 内正序步号；
 * montecarlo 回放如需正序按 no 重排，sarsa 文件序即回放序。
 */
@Data
@Builder
public class EpisodeTrace {

    /** 结束原因取值：进入终点（terminal） */
    public static final String ENDED_BY_TERMINAL = "terminal";
    /** 结束原因取值：达单回合步数上限（maxSteps） */
    public static final String ENDED_BY_MAX_STEPS = "maxSteps";

    /** episode 序号，从 1 起（与 UI 展示一致） */
    private int index;
    /** 该回合累计奖励（= visits 最后一行 totalReward，冗余便于直读） */
    private int totalReward;
    /** 结束原因：terminal | maxSteps */
    private String endedBy;
    /** 状态访问/更新记录，行序按算法（montecarlo 结算倒序 / sarsa 执行正序）；每行 = 一步更新 */
    @Builder.Default
    private List<VisitRecord> visits = new ArrayList<>();
}
