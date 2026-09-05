package com.metrocarlo.montecarlo;

import java.util.List;

import com.metrocarlo.agent.EpsilonGreedyPolicy;
import com.metrocarlo.agent.Policy;
import com.metrocarlo.agent.StateActionPair;
import com.metrocarlo.common.Constants;
import com.metrocarlo.world.Cell;
import com.metrocarlo.world.World;
import com.metrocarlo.world.history.ActionRecord;
import com.metrocarlo.world.history.EpisodeTrace;
import com.metrocarlo.world.history.Trace;
import com.metrocarlo.world.history.TraceMeta;
import com.metrocarlo.world.history.VisitRecord;

import lombok.Getter;

/**
 * 蒙特卡洛评估与控制：对同一策略重复执行完整回合（episode），
 * 回合结束后按倒序单循环结算回报 G 并增量更新 stateValues / qValues。
 * <p>
 * 结算循环同步采集 trace 数据（结构见 world.history，格式约定见 design/3/README.md §三）：
 * 每行 = 一次状态访问，记录 G 计算与该行 S/Q 更新前后值，供 GUI 回放与 S/Q 表重建。
 * 采集只读引用循环内既有局部量，不改动算法内容与循环顺序。
 */
public class MonteCarlo extends QAndSBuilder {

    /** 最近一次 buildQValuesAndSValues() 采集的 trace；运行前为 null */
    @Getter
    private Trace trace;

    public MonteCarlo(World world, Policy policy) {
        super(world, policy);
    }

    public void buildQValuesAndSValues() {
        qValues.clear();
        stateValues.clear();
        initQAndSValues();
        episodeCount = 0;
        trace = createEmptyTrace();

        while (episodeCount < maxEpisode) {
            episodeCount++;

            World tempWorld = new World(world);
            // 从起点干净开始，避免带入原世界的当前位置与历史
            tempWorld.reset();
            // 让策略基于本回合实际移动的世界做决策
            policy.setWorld(tempWorld);

            int steps = 0;
            while (!tempWorld.isTerminated() && steps++ < maxStepsPerEpisode) {
                int direction = policy.determineAction();
                tempWorld.move(direction);
            }

            // trace：本回合元信息（序号、累计奖励、结束原因），visits 在下方结算循环内逐行采集
            EpisodeTrace episodeTrace = EpisodeTrace.builder()
                    .index(episodeCount)
                    .totalReward(tempWorld.getTotalReward())
                    .endedBy(tempWorld.isTerminated()
                            ? EpisodeTrace.ENDED_BY_TERMINAL
                            : EpisodeTrace.ENDED_BY_MAX_STEPS)
                    .build();

            double lastG = 0.0;
            List<ActionRecord> history = tempWorld.getHistory();

            for (int i = history.size() - 1; i >= 0; i--) {
                ActionRecord actionRecord = history.get(i);
                Cell cell = actionRecord.getCell();

                // 折扣回报：当步即时奖励 + gamma * 后继状态的回报（G 自最后一步向回传）
                double G = actionRecord.getReward() + gamma * lastG;

                double originalSValue = stateValues.get(cell);
                double newSValue = originalSValue + alpha * (G - originalSValue);
                stateValues.put(cell, newSValue);

                StateActionPair stateActionPair = new StateActionPair(cell, actionRecord.getAction());
                double originalQValue = qValues.get(stateActionPair);
                double newQValue = originalQValue + alpha * (G - originalQValue);
                qValues.put(stateActionPair, newQValue);

                // trace 采集：与结算循环逐行对应，直接引用上述局部量，不改动算法行为
                episodeTrace.getVisits().add(VisitRecord.builder()
                        .no(i + 1)
                        .x(cell.getX())
                        .y(cell.getY())
                        .action(actionName(actionRecord.getAction()))
                        .reward(actionRecord.getReward())
                        .totalReward(actionRecord.getTotalReward())
                        .nextX(actionRecord.getNextCell().getX())
                        .nextY(actionRecord.getNextCell().getY())
                        .fromType(cell.getType())
                        .toType(actionRecord.getNextCell().getType())
                        .discountedNextG(gamma * lastG)
                        .G(G)
                        .sOld(originalSValue)
                        .sNew(newSValue)
                        .qOld(originalQValue)
                        .qNew(newQValue)
                        .build());

                // 折扣回报回传：作为更早一步（下一轮循环）的后继回报
                lastG = G;
            }

            trace.getEpisodes().add(episodeTrace);

            policy.setQValues(qValues);
            policy.setStateValues(stateValues);
        }
    }

    /**
     * 构建本次运行的 trace 容器并填充 meta：地图与算法上下文字段取自运行参数；
     * mapPath / seed / exportedAt 属导出上下文，由导出入口（后续任务）补充，暂为 null。
     */
    private Trace createEmptyTrace() {
        TraceMeta meta = TraceMeta.builder()
                .width(world.getWidth())
                .height(world.getHeight())
                .initialMap(world.getInitialMapInCsvRowOrder())
                .startX(world.getStartX())
                .startY(world.getStartY())
                .alpha(alpha)
                .gamma(gamma)
                .epsilon(resolveEpsilon())
                .maxEpisode(maxEpisode)
                .maxStepsPerEpisode(maxStepsPerEpisode)
                .build();
        return Trace.builder().meta(meta).build();
    }

    /** 策略为 ε-贪婪时取其实例 epsilon，否则退回默认值（trace 中记录实际使用的探索率） */
    private double resolveEpsilon() {
        if (policy instanceof EpsilonGreedyPolicy) {
            return ((EpsilonGreedyPolicy) policy).getEpsilon();
        }
        return Constants.DEFAULT_EPSILON;
    }

    /** 方向常量 → trace 动作字符串（取值见 VisitRecord.ACTION_*，与 JSON 格式约定一致） */
    private static String actionName(int direction) {
        switch (direction) {
            case Constants.DIRECTION_UP:
                return VisitRecord.ACTION_UP;
            case Constants.DIRECTION_RIGHT:
                return VisitRecord.ACTION_RIGHT;
            case Constants.DIRECTION_DOWN:
                return VisitRecord.ACTION_DOWN;
            case Constants.DIRECTION_LEFT:
                return VisitRecord.ACTION_LEFT;
            default:
                throw new IllegalArgumentException("Invalid direction: " + direction);
        }
    }

    private void initQAndSValues() {
        for (Cell cell : world.getCells()) {
            stateValues.put(cell, 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_UP), 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_RIGHT), 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_DOWN), 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_LEFT), 0.0);
        }
    }
}
