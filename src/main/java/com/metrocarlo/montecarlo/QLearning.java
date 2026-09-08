package com.metrocarlo.montecarlo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.metrocarlo.agent.Policy;
import com.metrocarlo.agent.StateActionPair;
import com.metrocarlo.common.Constants;
import com.metrocarlo.history.EpisodeTrace;
import com.metrocarlo.history.TdUpdateRecord;
import com.metrocarlo.history.Trace;
import com.metrocarlo.history.TraceMeta;
import com.metrocarlo.history.VisitRecord;
import com.metrocarlo.world.Cell;
import com.metrocarlo.world.World;

import lombok.Getter;
import lombok.Setter;

/**
 * Q-learning 时序差分控制：off-policy 走一步更新一次
 * Q(s,a) ← Q(s,a) + α·(r + γ·max_a′Q(s′,a′) − Q(s,a))，自举取下一状态的最大 Q，
 * 与行为策略在 s′ 的实际动作解耦（行为动作仅用于下一步移动，不参与更新）。
 * <p>
 * 主循环同步采集 trace 数据（结构见 history 包，格式约定见 design/14/README.md §三）：
 * 每行 = 一步在线 TD 更新，按执行正序记录 argmax 动作 a* 与 max Q(s′,a′)、TD 目标/δ/实际 α
 * 与该行 Q 更新前后值（td 字段语义按 algorithm 区分，见 design/14 §3.3），
 * 供 GUI 回放与 Q 表重建（无回合末结算，GUI 逐行覆盖写 qNew）。
 * 采集只读引用循环内既有局部量，不改动算法内容与循环顺序；
 * 批量实验可通过 {@link #setCollectTrace(boolean)} 整体关闭采集。
 */
public class QLearning {
    @Getter @Setter
    private double alpha = Constants.DEFAULT_ALPHA;

    @Getter @Setter
    private double gamma = Constants.DEFAULT_GAMMA;

    @Getter
    private World world;

    @Getter
    private Map<StateActionPair, Double> qValues = new java.util.HashMap<>();

    @Getter
    private Map<StateActionPair, Double> qAlphas = new java.util.HashMap<>();

    @Getter @Setter
    private int maxEpisode = Constants.DEFAULT_MAX_EPISODE;

    /** 单个回合的最大步数上限，防止策略成环导致回合永不终止 */
    @Getter @Setter
    private int maxStepsPerEpisode = Constants.MAX_STEP_PER_EPISODE;

    private int episodeCount = 0;

    private Policy policy;

    /** 最近一次 buildQValues() 采集的 trace；运行前或关闭采集后为 null */
    @Getter
    private Trace trace;

    /** 是否采集 trace：批量实验关闭后不构建任何 trace 对象，算法内容不变 */
    @Getter @Setter
    private boolean collectTrace = true;

    public QLearning(World world, Policy policy) {
        this.world = world;
        this.policy = policy;
    }

    public void buildQValues() {
        qValues.clear();
        initQValues();
        initQAlphas();
        episodeCount = 0;
        trace = collectTrace
                ? TraceFactory.createEmptyTrace(world, alpha, gamma, policy, maxEpisode, maxStepsPerEpisode,
                        TraceMeta.ALGORITHM_QLEARNING)
                : null;

        while (episodeCount < maxEpisode) {
            episodeCount++;

            World tempWorld = new World(world);
            // 从起点干净开始，避免带入原世界的当前位置与历史
            tempWorld.reset();
            // 让策略基于本回合实际移动的世界做决策
            policy.setWorld(tempWorld);

            int steps = 0;
            int direction = policy.determineAction();
            // trace：本回合各行按执行正序采集（在线更新粒度，见 design/14/README.md §三）
            List<VisitRecord> visits = collectTrace ? new ArrayList<>() : null;
            while (!tempWorld.isTerminated() && steps++ < maxStepsPerEpisode) {
                StateActionPair stateActionPair = new StateActionPair(tempWorld.getCurrentCell(), direction);
                double q = qValues.get(stateActionPair);
                Cell fromCell = stateActionPair.getState();
                tempWorld.move(direction);
                int reward = tempWorld.getLastReward();
                // off-policy 自举：对下一状态 s′ 四方向查表取 max 与 argmax（Q 表已全初始化，无 null）；
                // 严格 > 保留首个，天然实现上→右→下→左平局规则；
                // 必须在 qValues.put 之前计算：撞墙 s′==s 时读到本步更新前的表（含 Q(s,a) 旧值，见 design/14 §3.5）
                Cell nextCell = tempWorld.getCurrentCell();
                double maxNextQ = Double.NEGATIVE_INFINITY;
                int bestDirection = Constants.DIRECTION_UP;
                for (int d = 0; d < Constants.DIRECTION_COUNT; d++) {
                    double candidate = qValues.get(new StateActionPair(nextCell, d));
                    if (candidate > maxNextQ) {
                        maxNextQ = candidate;
                        bestDirection = d;
                    }
                }
                // 等价改写：提取本行实际使用的 α 与更新结果（值与求值顺序不变），供更新与 trace 采集共用
                double alphaUsed = qAlphas.get(stateActionPair);
                double tdTarget = reward + gamma * maxNextQ;
                double delta = tdTarget - q;
                double newQ = q + alphaUsed * delta;
                qValues.put(stateActionPair, newQ);
                if (Constants.DECAY_ALPHA) {
                    qAlphas.put(stateActionPair, alphaUsed * Constants.DECAY_ALPHA_RATE);
                }
                // off-policy：行为策略选下一步动作，仅用于移动，不参与本步更新
                direction = policy.determineAction();

                // trace 采集：只读引用上述局部量与格子快照，不改动算法行为（字段来源见 design/14 §四）
                if (collectTrace) {
                    visits.add(VisitRecord.builder()
                            .no(steps)
                            .x(fromCell.getX())
                            .y(fromCell.getY())
                            .action(VisitRecord.actionName(stateActionPair.getAction()))
                            .reward(reward)
                            .totalReward(tempWorld.getTotalReward())
                            .nextX(nextCell.getX())
                            .nextY(nextCell.getY())
                            .fromType(fromCell.getType())
                            .toType(nextCell.getType())
                            .td(TdUpdateRecord.builder()
                                    .nextAction(VisitRecord.actionName(bestDirection))
                                    .nextQ(maxNextQ)
                                    .tdTarget(tdTarget)
                                    .delta(delta)
                                    .alpha(alphaUsed)
                                    .build())
                            .qOld(q)
                            .qNew(newQ)
                            .build());
                }
            }

            // trace：本回合元信息（序号、累计奖励、结束原因），各行已按执行正序采集完毕
            if (collectTrace) {
                trace.getEpisodes().add(EpisodeTrace.builder()
                        .index(episodeCount)
                        .totalReward(tempWorld.getTotalReward())
                        .endedBy(tempWorld.isTerminated()
                                ? EpisodeTrace.ENDED_BY_TERMINAL
                                : EpisodeTrace.ENDED_BY_MAX_STEPS)
                        .visits(visits)
                        .build());
            }

            policy.setQValues(qValues);
        }
    }

    private void initQValues() {
        for (Cell cell : world.getCells()) {
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_UP), 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_RIGHT), 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_DOWN), 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_LEFT), 0.0);
        }
    }

    private void initQAlphas() {
        for (Cell cell : world.getCells()) {
            qAlphas.put(new StateActionPair(cell, Constants.DIRECTION_UP), alpha);
            qAlphas.put(new StateActionPair(cell, Constants.DIRECTION_RIGHT), alpha);
            qAlphas.put(new StateActionPair(cell, Constants.DIRECTION_DOWN), alpha);
            qAlphas.put(new StateActionPair(cell, Constants.DIRECTION_LEFT), alpha);
        }
    }
}
