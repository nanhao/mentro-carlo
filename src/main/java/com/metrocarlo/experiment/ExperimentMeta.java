package com.metrocarlo.experiment;

import java.util.List;

/**
 * 策略对比实验的参数集合（results.json 的 meta 部分，intention-7）。
 *
 * @param episodes           每次训练运行的 episode 数（默认 1000）
 * @param repetitions        每个策略配置的重复次数（默认 50）
 * @param alpha              蒙特卡洛更新步长 α（Constants 默认值）
 * @param gamma              折扣因子 γ（Constants 默认值）
 * @param maxStepsPerEpisode 单回合步数上限（训练与贪婪走图共用，防策略成环不终止）
 * @param epsilons           ε-贪婪探索率扫描序列（intention-7：0.1 递增到 0.9）
 * @param exportedAt         导出时间；内存期为 null，由 CompareResultWriter 写盘时补充
 */
public record ExperimentMeta(int episodes, int repetitions, double alpha, double gamma,
                             int maxStepsPerEpisode, List<Double> epsilons, String exportedAt) {

    /** results.json 格式版本（结构变更时递增） */
    public static final int VERSION = 1;
}
