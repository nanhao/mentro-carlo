package com.metrocarlo.world.history;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * trace 中单个 episode 的记录（JSON 中 episodes 数组的元素），
 * 字段与 design/3/README.md §3.1「episodes[] 字段字典」一一对应。
 * <p>
 * visits 按结算循环顺序存放（回合倒序，第 1 行 = 最后一步），
 * 行内 {@link VisitRecord#getNo()} 为 episode 内正序步号，GUI 如需正序回放按 no 重排即可。
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
    /** 状态访问记录，结算循环顺序（回合倒序）；每行 = 一次状态访问 */
    @Builder.Default
    private List<VisitRecord> visits = new ArrayList<>();
}
