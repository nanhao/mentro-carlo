package com.metrocarlo;

import java.io.IOException;
import java.nio.file.Path;

import com.metrocarlo.agent.EpsilonGreedyPolicy;
import com.metrocarlo.agent.Policy;
import com.metrocarlo.history.TraceFileWriter;
import com.metrocarlo.montecarlo.MonteCarlo;
import com.metrocarlo.montecarlo.Sarsa;
import com.metrocarlo.world.World;

public class Main {
    public static void main(String[] args) throws IOException {
        // 约定：先运行 map/1/map.csv，trace 导出到 data/trace 目录（intention-2；sarsa 见 intention-12）
        runSarsaOnOneMap("map/1/map.csv");
    }

    /**
     * 在指定地图上执行一轮完整蒙特卡洛并导出 trace：加载地图 → 构建 MonteCarlo →
     * 跑完全部 episode → 按 design/3 §二 的文件组织写入
     * data/trace/map-&lt;N&gt;/run-&lt;时间戳&gt;/trace.json。
     *
     * @param mapPath 源地图相对路径（如 map/1/map.csv）
     */
    private static void runMonteCarloOnOneMap(String mapPath) throws IOException {
        World world = new World(mapPath);
        Policy policy = new EpsilonGreedyPolicy();
        //Policy policy = new RandomPolicy();
        MonteCarlo monteCarlo = new MonteCarlo(world, policy);
        monteCarlo.buildQValuesAndSValues();

        Path output = TraceFileWriter.write(monteCarlo.getTrace(), mapPath);
        System.out.println("trace 已导出: " + output.toAbsolutePath().normalize());
    }

    /**
     * 在指定地图上执行一轮完整 Sarsa 并导出 trace：加载地图 → 构建 Sarsa →
     * 跑完全部 episode → 写入 data/trace/sarsa/map-&lt;N&gt;/run-&lt;时间戳&gt;/trace.json
     * （目录组织见 design/12/README.md §二，格式见 §三）。
     *
     * @param mapPath 源地图相对路径（如 map/1/map.csv）
     */
    private static void runSarsaOnOneMap(String mapPath) throws IOException {
        World world = new World(mapPath);
        Policy policy = new EpsilonGreedyPolicy();
        Sarsa sarsa = new Sarsa(world, policy);
        sarsa.buildQValues();

        Path output = TraceFileWriter.write(sarsa.getTrace(), mapPath);
        System.out.println("sarsa trace 已导出: " + output.toAbsolutePath().normalize());
    }
}
