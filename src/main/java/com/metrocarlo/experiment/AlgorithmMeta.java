package com.metrocarlo.experiment;

import java.util.List;

/**
 * 算法对比实验的参数集合（results.json 的 meta 部分，intention-16）。
 *
 * @param episodes           每次训练运行的 episode 数（默认 1000）
 * @param repetitions        每种算法的重复次数（默认 50）
 * @param alpha              学习率 α（Constants 默认值，三种算法训练共用）
 * @param gamma              折扣因子 γ（Constants 默认值）
 * @param maxStepsPerEpisode 单回合步数上限（训练与贪婪走图共用，防策略成环不终止）
 * @param epsilon            行为策略 ε-贪婪探索率（intention-16 固定 1.0：训练全程随机探索）
 * @param algorithms         参与对比的算法标识列表（取值 TraceMeta.ALGORITHM_*）
 * @param exportedAt         导出时间；内存期为 null，由 AlgorithmCompareWriter 写盘时补充
 */
public record AlgorithmMeta(int episodes, int repetitions, double alpha, double gamma,
                            int maxStepsPerEpisode, double epsilon, List<String> algorithms,
                            String exportedAt) {

    /** results.json 格式版本（结构变更时递增） */
    public static final int VERSION = 1;
}
