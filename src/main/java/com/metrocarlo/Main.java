package com.metrocarlo;

import java.io.IOException;
import java.nio.file.Path;

import com.metrocarlo.agent.EpsilonGreedyPolicy;
import com.metrocarlo.agent.Policy;
import com.metrocarlo.montecarlo.MonteCarlo;
import com.metrocarlo.world.World;
import com.metrocarlo.world.history.TraceFileWriter;

public class Main {
    public static void main(String[] args) throws IOException {
        // 约定：先运行 map/1/map.csv，trace 导出到 data/trace 目录（intention-2）
        runMonteCarloOnOneMap("map/1/map.csv");
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
        MonteCarlo monteCarlo = new MonteCarlo(world, policy);
        monteCarlo.buildQValuesAndSValues();

        Path output = TraceFileWriter.write(monteCarlo.getTrace(), mapPath);
        System.out.println("trace 已导出: " + output.toAbsolutePath().normalize());
    }
}
