package com.metrocarlo.experiment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.metrocarlo.agent.EpsilonGreedyPolicy;
import com.metrocarlo.agent.StateActionPair;
import com.metrocarlo.common.Constants;
import com.metrocarlo.history.TraceMeta;
import com.metrocarlo.montecarlo.MonteCarlo;
import com.metrocarlo.montecarlo.QLearning;
import com.metrocarlo.montecarlo.Sarsa;
import com.metrocarlo.world.World;

/**
 * intention-16 实验入口：在 map/ 下全部地图上对比 MonteCarlo / Sarsa / Q-learning
 * 三种算法在固定探索率 ε = 1 下的学习效果（行为策略全程随机探索）。
 * <p>
 * 对每张地图 × 每种算法：
 * <ol>
 *   <li>重复 {@code repetitions}（默认 50）次：用 ε=1 的 ε-贪婪策略从零训练
 *       {@code episodes}（默认 1000）个 episode 得到 Q 表 → 用学到的 Q 表按全程
 *       贪婪走一遍 → 记录单次奖励；</li>
 *   <li>汇总 50 次奖励的均值、标准差与抵达终点次数。</li>
 * </ol>
 * 三种算法在同一探索策略下比较（ε=1 时三种算法的训练回合行为分布一致，
 * 差异仅来自各自的更新机制：回合末回报结算 / on-policy TD / off-policy TD）。
 * 结果写入 data/algorithm-compare/run-&lt;时间戳&gt;/results.json
 * （结构见 AlgorithmCompareWriter），可视化交给 scripts/compare_algorithms.py。
 * <p>
 * 运行：{@code mvn compile exec:java -Dexec.mainClass=com.metrocarlo.experiment.AlgorithmComparison}
 * （可选 --episodes N / --repetitions N 缩小规模冒烟）。
 */
public final class AlgorithmComparison {

    /** 默认每次训练的 episode 数（intention-16：1000） */
    private static final int DEFAULT_EPISODES = Constants.DEFAULT_MAX_EPISODE;
    /** 默认每种算法的重复次数（intention-16：50） */
    private static final int DEFAULT_REPETITIONS = 50;
    /** 行为策略探索率（intention-16 固定 1.0：每步决策都随机探索） */
    private static final double EXPLORATION_EPSILON = 1.0;
    /** 参与对比的算法标识（顺序 = 结果 algorithms 数组顺序；取值 = TraceMeta.ALGORITHM_*） */
    private static final List<String> ALGORITHMS = List.of(
            TraceMeta.ALGORITHM_MONTECARLO, TraceMeta.ALGORITHM_SARSA, TraceMeta.ALGORITHM_QLEARNING);
    /** 命令行参数名：覆盖每次训练的 episode 数 */
    private static final String ARG_EPISODES = "--episodes";
    /** 命令行参数名：覆盖每种算法的重复次数 */
    private static final String ARG_REPETITIONS = "--repetitions";

    private AlgorithmComparison() {
    }

    /**
     * 解析可选参数（--episodes N / --repetitions N，缺省用 intention-16 规模）、
     * 发现地图并逐图跑完三种算法，最后写出 results.json。
     */
    public static void main(String[] args) throws IOException {
        int episodes = DEFAULT_EPISODES;
        int repetitions = DEFAULT_REPETITIONS;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case ARG_EPISODES -> episodes = Integer.parseInt(args[++i]);
                case ARG_REPETITIONS -> repetitions = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException(
                        "未知参数: " + args[i] + "（支持 " + ARG_EPISODES + " N / " + ARG_REPETITIONS + " N）");
            }
        }

        List<String> mapPaths = MapDiscovery.discoverMaps();
        System.out.printf("算法对比实验：%d 张地图 × %d 种算法（ε=1.0）× %d 次重复 × 每次 %d episode%n",
                mapPaths.size(), ALGORITHMS.size(), repetitions, episodes);

        AlgorithmMeta meta = new AlgorithmMeta(episodes, repetitions,
                Constants.DEFAULT_ALPHA, Constants.DEFAULT_GAMMA,
                Constants.MAX_STEP_PER_EPISODE, EXPLORATION_EPSILON, ALGORITHMS, null);
        List<MapResult<AlgorithmResult>> maps = new ArrayList<>();
        for (String mapPath : mapPaths) {
            maps.add(runOnMap(mapPath, meta));
        }

        Path output = AlgorithmCompareWriter.write(meta, maps);
        System.out.println("对比结果已导出: " + output.toAbsolutePath().normalize());
        System.out.println("可视化: python scripts/compare_algorithms.py");
    }

    /**
     * 在单张地图上跑全部三种算法（顺序与 meta.algorithms 一致），
     * 每种算法独立完成「训练 + 贪婪评估」的全部重复。
     */
    private static MapResult<AlgorithmResult> runOnMap(String mapPath, AlgorithmMeta meta) {
        World world = new World(mapPath);
        System.out.println("[" + mapPath + "]");
        List<AlgorithmResult> results = new ArrayList<>(ALGORITHMS.size());
        for (String algorithm : ALGORITHMS) {
            results.add(runAlgorithm(world, algorithm, meta));
        }
        return new MapResult<>(mapPath, world.getWidth(), world.getHeight(),
                world.getInitialMapInCsvRowOrder(), results);
    }

    /**
     * 单个算法的完整实验：重复 meta.repetitions 次「从零训练 → 全程贪婪走图」，
     * 汇总每次奖励的均值与总体标准差，以及贪婪走图抵达终点的次数。
     */
    private static AlgorithmResult runAlgorithm(World world, String algorithm, AlgorithmMeta meta) {
        List<Integer> repRewards = new ArrayList<>(meta.repetitions());
        int terminatedCount = 0;
        for (int rep = 1; rep <= meta.repetitions(); rep++) {
            Map<StateActionPair, Double> qValues = trainOnce(world, algorithm, meta);
            GreedyOutcome outcome = GreedyEvaluator.evaluate(
                    world, qValues, meta.maxStepsPerEpisode());
            repRewards.add(outcome.totalReward());
            if (outcome.terminated()) {
                terminatedCount++;
            }
        }

        double mean = repRewards.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double std = Math.sqrt(repRewards.stream().mapToInt(Integer::intValue)
                .mapToDouble(reward -> (reward - mean) * (reward - mean)).sum()
                / repRewards.size());
        System.out.printf("  %-12s 完成 %2d 次重复：平均贪婪奖励 %8.2f（std %6.2f，抵达终点 %d/%d）%n",
                algorithm, meta.repetitions(), mean, std,
                terminatedCount, meta.repetitions());
        return new AlgorithmResult(algorithm, repRewards, mean, std, terminatedCount);
    }

    /**
     * 按算法标识做一次从零训练并返回学到的 Q 表：episode 数 / 单回合步数上限取 meta，
     * 行为策略 = ε=1 的 ε-贪婪策略（每次训练新建策略，探索随机序列互不相关）；
     * 关闭 trace 采集——批量实验只关心学到的 Q 表，免去每步构建 trace 对象的内存开销。
     */
    private static Map<StateActionPair, Double> trainOnce(World world, String algorithm,
                                                          AlgorithmMeta meta) {
        switch (algorithm) {
            case TraceMeta.ALGORITHM_MONTECARLO -> {
                MonteCarlo monteCarlo = new MonteCarlo(world, buildBehaviorPolicy(meta));
                monteCarlo.setMaxEpisode(meta.episodes());
                monteCarlo.setMaxStepsPerEpisode(meta.maxStepsPerEpisode());
                monteCarlo.setCollectTrace(false);
                monteCarlo.buildQValuesAndSValues();
                return monteCarlo.getQValues();
            }
            case TraceMeta.ALGORITHM_SARSA -> {
                Sarsa sarsa = new Sarsa(world, buildBehaviorPolicy(meta));
                sarsa.setMaxEpisode(meta.episodes());
                sarsa.setMaxStepsPerEpisode(meta.maxStepsPerEpisode());
                sarsa.setCollectTrace(false);
                sarsa.buildQValues();
                return sarsa.getQValues();
            }
            case TraceMeta.ALGORITHM_QLEARNING -> {
                QLearning qLearning = new QLearning(world, buildBehaviorPolicy(meta));
                qLearning.setMaxEpisode(meta.episodes());
                qLearning.setMaxStepsPerEpisode(meta.maxStepsPerEpisode());
                qLearning.setCollectTrace(false);
                qLearning.buildQValues();
                return qLearning.getQValues();
            }
            default -> throw new IllegalArgumentException("未知算法: " + algorithm
                    + "（支持 " + ALGORITHMS + "）");
        }
    }

    /** 组装一次训练用的行为策略：ε-贪婪策略，探索率固定取 meta.epsilon（intention-16：1.0） */
    private static EpsilonGreedyPolicy buildBehaviorPolicy(AlgorithmMeta meta) {
        EpsilonGreedyPolicy policy = new EpsilonGreedyPolicy();
        policy.setEpsilon(meta.epsilon());
        return policy;
    }
}
