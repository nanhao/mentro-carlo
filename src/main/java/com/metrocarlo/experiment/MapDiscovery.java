package com.metrocarlo.experiment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 批量对比实验入口共用的地图发现工具：扫描 map/ 下全部内置地图。
 * <p>
 * 目录约定见 map 目录结构：子目录名为纯数字编号（1 ~ 10），各含 map.csv。
 * 按编号升序返回地图相对路径，保证两次运行、两个实验（PolicyComparison 与
 * AlgorithmComparison）的跑图顺序一致。
 */
public final class MapDiscovery {

    /** 地图根目录（相对项目根；子目录名为纯数字编号，各含 map.csv） */
    private static final Path MAP_ROOT = Path.of("map");

    private MapDiscovery() {
    }

    /**
     * 扫描 map/ 下纯数字编号且含 map.csv 的子目录，按编号升序返回地图相对路径。
     *
     * @return 如 [map/1/map.csv, map/2/map.csv, ...]
     * @throws IOException          目录读取失败时抛出
     * @throws IllegalStateException map/ 下未发现任何地图（应先运行 python scripts/generate_maps.py）
     */
    public static List<String> discoverMaps() throws IOException {
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
}
