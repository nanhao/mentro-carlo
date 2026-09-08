package com.metrocarlo.experiment;

import java.util.Map;

import com.metrocarlo.agent.EpsilonGreedyPolicy;
import com.metrocarlo.agent.StateActionPair;
import com.metrocarlo.world.World;

/**
 * 批量对比实验共用的评估步骤：用训练好的 Q 表做一次全程贪婪走图。
 * <p>
 * MonteCarlo / Sarsa / QLearning 训练产出的 Q 表同构（StateActionPair → double，
 * 全状态 × 四方向已初始化），因此三种算法的贪婪评估走同一套逻辑：
 * ε=0 的 ε-贪婪策略即纯贪婪（random.nextDouble() &lt; 0 恒不成立，平局随机取其一），
 * 从干净起点出发走到终点或步数上限为止。PolicyComparison 与 AlgorithmComparison
 * 均引用本工具，保证「训练 → 评估」口径一致。
 */
public final class GreedyEvaluator {

    private GreedyEvaluator() {
    }

    /**
     * 从 {@code sourceWorld} 复制一份干净世界，用 Q 表全程贪婪走一遍并返回结果。
     * <p>
     * 复制而非原地走：评估不得污染训练用的原世界（原世界仅作地图模板）。
     *
     * @param sourceWorld 地图模板（构造副本用；不修改其状态）
     * @param qValues     训练好的 Q 表（各状态 × 四方向均须有值）
     * @param maxSteps    单回合步数上限（与训练口径一致，防止成环不终止）
     * @return 累计奖励与是否抵达终点
     */
    public static GreedyOutcome evaluate(World sourceWorld,
                                         Map<StateActionPair, Double> qValues, int maxSteps) {
        World evalWorld = new World(sourceWorld);
        // 从起点干净开始，与训练回合的环境口径一致
        evalWorld.reset();
        EpsilonGreedyPolicy greedyPolicy = new EpsilonGreedyPolicy();
        greedyPolicy.setEpsilon(0.0);
        greedyPolicy.setQValues(qValues);
        greedyPolicy.setWorld(evalWorld);

        int steps = 0;
        while (!evalWorld.isTerminated() && steps++ < maxSteps) {
            evalWorld.move(greedyPolicy.determineAction());
        }
        return new GreedyOutcome(evalWorld.getTotalReward(), evalWorld.isTerminated());
    }
}
