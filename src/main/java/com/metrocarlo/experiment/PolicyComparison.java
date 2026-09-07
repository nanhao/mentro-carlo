package com.metrocarlo.experiment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.metrocarlo.agent.EpsilonGreedyPolicy;
import com.metrocarlo.agent.Policy;
import com.metrocarlo.agent.RandomPolicy;
import com.metrocarlo.agent.StateActionPair;
import com.metrocarlo.common.Constants;
import com.metrocarlo.montecarlo.MonteCarlo;
import com.metrocarlo.world.World;

/**
 * intention-7 实验入口：在 map/ 下全部地图上对比 RandomPolicy 与
 * EpsilonGreedyPolicy（ε = 0.1 ~ 0.9）的蒙特卡洛学习效果。
 * <p>
 * 对每张地图 × 每种策略配置（random 与 ε=0.1~0.9 共 10 种）：
 * <ol>
 *   <li>重复 {@code repetitions}（默认 50）次：从零训练 {@code episodes}（默认 1000）个
 *       episode 得到状态/动作价值函数 → 用学到的 Q 表按全程贪婪走一遍 →
 *       记录单次奖励 rri / rei-ε；</li>
 *   <li>汇总 50 次奖励的均值（rri' / rei'-ε）、标准差与抵达终点次数。</li>
 * </ol>
 * 结果写入 data/compare/run-&lt;时间戳&gt;/results.json（结构见 CompareResultWriter），
 * 可视化交给 scripts/compare_policies.py。
 * <p>
 * 运行：{@code mvn compile exec:java -Dexec.mainClass=com.metrocarlo.experiment.PolicyComparison}
 * （可选 --episodes N / --repetitions N 缩小规模冒烟）。
 */
public final class PolicyComparison {

    /** 默认每次训练的 episode 数（intention-7：1000） */
    private static final int DEFAULT_EPISODES = Constants.DEFAULT_MAX_EPISODE;
    /** 默认每个策略配置的重复次数（intention-7：50） */
    private static final int DEFAULT_REPETITIONS = 50;
    /** ε-贪婪探索率扫描序列（intention-7：0.1 递增到 0.9） */
    private static final List<Double> EPSILONS =
            List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9);
    /** 地图根目录（相对项目根；子目录名为纯数字编号，各含 map.csv） */
    private static final Path MAP_ROOT = Path.of("map");
    /** 命令行参数名：覆盖每次训练的 episode 数 */
    private static final String ARG_EPISODES = "--episodes";
    /** 命令行参数名：覆盖每个策略配置的重复次数 */
    private static final String ARG_REPETITIONS = "--repetitions";

    private PolicyComparison() {
    }

    /**
     * 解析可选参数（--episodes N / --repetitions N，缺省用 intention-7 规模）、
     * 发现地图并逐图跑完全部策略配置，最后写出 results.json。
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

        List<String> mapPaths = discoverMaps();
        System.out.printf("策略对比实验：%d 张地图 × %d 种策略（random + ε=0.1~0.9）× %d 次重复 × 每次 %d episode%n",
                mapPaths.size(), EPSILONS.size() + 1, repetitions, episodes);

        ExperimentMeta meta = new ExperimentMeta(episodes, repetitions,
                Constants.DEFAULT_ALPHA, Constants.DEFAULT_GAMMA,
                Constants.MAX_STEP_PER_EPISODE, EPSILONS, null);
        List<MapResult> maps = new ArrayList<>();
        for (String mapPath : mapPaths) {
            maps.add(runOnMap(mapPath, meta));
        }

        Path output = CompareResultWriter.write(meta, maps);
        System.out.println("对比结果已导出: " + output.toAbsolutePath().normalize());
        System.out.println("可视化: python scripts/compare_policies.py");
    }

    /**
     * 扫描 map/ 下纯数字编号且含 map.csv 的子目录，按编号升序返回地图相对路径。
     */
    private static List<String> discoverMaps() throws IOException {
        Map<Integer, String> mapsByNumber = new TreeMap<>();
        try (var entries = Files.list(MAP_ROOT)) {
            entries.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.chars().allMatch(Character::isDigit))
                    .filter(name -> Files.exists(MAP_ROOT.resolve(name).resolve("map.csv")))
                    .forEach(name -> mapsByNumber.put(Integer.parseInt(name), name));
        }
        if (mapsByNumber.isEmpty()) {
            throw new IllegalStateException(
                    "map/ 下未发现任何地图（应先运行 python scripts/generate_maps.py）");
        }
        return mapsByNumber.values().stream()
                .map(name -> MAP_ROOT.resolve(name).resolve("map.csv").toString())
                .toList();
    }

    /**
     * 在单张地图上跑全部策略配置：random 在前，其后按 ε 自小到大，
     * 每种配置独立完成「训练 + 贪婪评估」的全部重复。
     */
    private static MapResult runOnMap(String mapPath, ExperimentMeta meta) {
        World world = new World(mapPath);
        System.out.println("[" + mapPath + "]");
        List<PolicyResult> policies = new ArrayList<>();
        policies.add(runPolicy(world, PolicyResult.RANDOM, null, meta));
        for (double epsilon : meta.epsilons()) {
            policies.add(runPolicy(world, PolicyResult.EPS_GREEDY, epsilon, meta));
        }
        return new MapResult(mapPath, world.getWidth(), world.getHeight(),
                world.getInitialMapInCsvRowOrder(), policies);
    }

    /**
     * 单个策略配置的完整实验：重复 meta.repetitions 次「从零训练 → 全程贪婪走图」，
     * 汇总每次奖励 rri/rei-ε 的均值与总体标准差，以及贪婪走图抵达终点的次数。
     */
    private static PolicyResult runPolicy(World world, String policyName, Double epsilon,
                                          ExperimentMeta meta) {
        List<Integer> repRewards = new ArrayList<>(meta.repetitions());
        int terminatedCount = 0;
        for (int rep = 1; rep <= meta.repetitions(); rep++) {
            MonteCarlo monteCarlo = buildMonteCarlo(world, epsilon, meta.episodes());
            monteCarlo.buildQValuesAndSValues();
            World evalWorld = runGreedyEpisode(world, monteCarlo.getQValues(), meta.maxStepsPerEpisode());
            repRewards.add(evalWorld.getTotalReward());
            if (evalWorld.isTerminated()) {
                terminatedCount++;
            }
        }

        double mean = repRewards.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double std = Math.sqrt(repRewards.stream().mapToInt(Integer::intValue)
                .mapToDouble(reward -> (reward - mean) * (reward - mean)).sum()
                / repRewards.size());
        System.out.printf("  %-10s 完成 %2d 次重复：平均贪婪奖励 %8.2f（std %6.2f，抵达终点 %d/%d）%n",
                describePolicy(policyName, epsilon), meta.repetitions(), mean, std,
                terminatedCount, meta.repetitions());
        return new PolicyResult(policyName, epsilon, repRewards, mean, std, terminatedCount);
    }

    /**
     * 组装一次训练用的蒙特卡洛：episode 数取 meta（可由命令行覆盖），
     * α/γ/单回合步数上限用 Constants 默认值；关闭 trace 采集——批量实验只关心
     * 学到的 Q 表，免去每步构建 VisitRecord 的内存与 GC 开销。
     */
    private static MonteCarlo buildMonteCarlo(World world, Double epsilon, int episodes) {
        MonteCarlo monteCarlo = new MonteCarlo(world, buildPolicy(epsilon));
        monteCarlo.setMaxEpisode(episodes);
        monteCarlo.setCollectTrace(false);
        return monteCarlo;
    }

    /** 按配置构建策略：epsilon 为 null 表示 RandomPolicy，否则为对应 ε 的 ε-贪婪策略 */
    private static Policy buildPolicy(Double epsilon) {
        if (epsilon == null) {
            return new RandomPolicy();
        }
        EpsilonGreedyPolicy policy = new EpsilonGreedyPolicy();
        policy.setEpsilon(epsilon);
        return policy;
    }

    /**
     * 用训练好的 Q 表做一次全程贪婪走图（intention-7 的单次奖励 rri/rei-ε）：
     * ε=0 的 ε-贪婪策略即纯贪婪（random.nextDouble() &lt; 0 恒不成立），
     * 从干净起点出发走到终点或步数上限，返回走完的世界（取总奖励与是否抵达终点）。
     */
    private static World runGreedyEpisode(World world, Map<StateActionPair, Double> qValues,
                                          int maxSteps) {
        World evalWorld = new World(world);
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
        return evalWorld;
    }

    /** 控制台进度用的策略描述：random 或 eps=0.1（ASCII，避免终端编码问题） */
    private static String describePolicy(String policyName, Double epsilon) {
        return PolicyResult.RANDOM.equals(policyName) ? policyName : "eps=" + epsilon;
    }
}
