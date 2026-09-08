package com.metrocarlo.montecarlo;

import com.metrocarlo.agent.EpsilonGreedyPolicy;
import com.metrocarlo.agent.Policy;
import com.metrocarlo.common.Constants;
import com.metrocarlo.history.Trace;
import com.metrocarlo.history.TraceMeta;
import com.metrocarlo.world.World;

/**
 * trace 容器工厂：构建带 meta 头的空 trace（episodes 由各算法采集时填充）。
 * <p>
 * meta 中地图与算法上下文字段取自运行参数，MonteCarlo 与 Sarsa 共用本工厂避免重复构建
 * （design/12/README.md §四）；mapPath / seed / exportedAt 属导出上下文，
 * 由导出入口 {@code TraceFileWriter} 补充。
 * <p>
 * 置于 montecarlo 包：唯一消费方 MonteCarlo / Sarsa 均在本包，
 * 避免放入 history 包引入 history→agent 反向依赖（agent→world→history 已成链）。
 */
public final class TraceFactory {

    private TraceFactory() {
    }

    /**
     * 构建本次运行的空 trace 并填充 meta：地图与算法上下文字段取自运行参数；
     * mapPath / seed / exportedAt 属导出上下文，由导出入口补充，暂为 null。
     *
     * @param world               运行地图（取尺寸/初始地图/起点）
     * @param alpha               学习率 α（初始值）
     * @param gamma               折扣因子 γ
     * @param policy              决策策略（ε-贪婪时取其实例 epsilon 记入 meta）
     * @param maxEpisode          回合数上限
     * @param maxStepsPerEpisode  单回合步数上限
     * @param algorithm           采集算法标识（见 TraceMeta.ALGORITHM_*）
     * @return meta 已填充、episodes 为空的 trace
     */
    public static Trace createEmptyTrace(World world, double alpha, double gamma, Policy policy,
                                         int maxEpisode, int maxStepsPerEpisode, String algorithm) {
        TraceMeta meta = TraceMeta.builder()
                .algorithm(algorithm)
                .width(world.getWidth())
                .height(world.getHeight())
                .initialMap(world.getInitialMapInCsvRowOrder())
                .startX(world.getStartX())
                .startY(world.getStartY())
                .alpha(alpha)
                .gamma(gamma)
                .epsilon(resolveEpsilon(policy))
                .maxEpisode(maxEpisode)
                .maxStepsPerEpisode(maxStepsPerEpisode)
                .build();
        return Trace.builder().meta(meta).build();
    }

    /** 策略为 ε-贪婪时取其实例 epsilon，否则退回默认值（trace 中记录实际使用的探索率） */
    private static double resolveEpsilon(Policy policy) {
        if (policy instanceof EpsilonGreedyPolicy) {
            return ((EpsilonGreedyPolicy) policy).getEpsilon();
        }
        return Constants.DEFAULT_EPSILON;
    }
}
