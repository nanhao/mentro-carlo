# -*- coding: utf-8 -*-
"""算法对比实验可视化脚本（intention-16）：读取 Java 端 AlgorithmComparison 导出的
results.json，在每张地图上比较 MonteCarlo / Sarsa / Q-learning 三种算法在
ε=1（行为策略全程随机探索）下的学习效果。

用法（在项目根目录执行）：
    python scripts/compare_algorithms.py                  # 自动取 data/algorithm-compare 下最新一次运行
    python scripts/compare_algorithms.py --results <路径>/results.json   # 指定结果文件

结果文件由 Java 端产出（data/algorithm-compare/run-<时间戳>/results.json，需先运行：
mvn compile exec:java -Dexec.mainClass=com.metrocarlo.experiment.AlgorithmComparison）。
图表输出到 results.json 所在的 run 目录：
    compare_per_map.png   每张地图一个子图：三种算法的平均贪婪奖励
                          （误差棒 = 标准差，灰点 = 50 次重复的原始奖励散点）
    compare_summary.png   全部地图的汇总对比：每张地图三条细线 + 全地图平均（加粗）

依赖 matplotlib（未安装时执行 pip install matplotlib）。
"""

import argparse
import json
import math
import sys
from pathlib import Path
from typing import Dict, List, Optional

import matplotlib
import numpy as np

matplotlib.use("Agg")  # 无窗口环境直接出图，只写文件不弹窗
import matplotlib.pyplot as plt

PROJECT_ROOT = Path(__file__).resolve().parent.parent
COMPARE_ROOT = PROJECT_ROOT / "data" / "algorithm-compare"

# ---- 绘图样式（全局统一） ----
RAW_POINT_COLOR = "#9e9e9e"   # 重复奖励散点：灰
ZERO_LINE_COLOR = "#bbbbbb"   # 零奖励参考线：浅灰
THIN_LINE_ALPHA = 0.45        # 汇总图中单地图细线的透明度
JITTER_SPAN = 0.16            # 散点横向抖动幅度（相对刻度间距）
SUBPLOT_COLS = 5              # 每行子图数
# 算法 → 展示名（与 GUI 措辞一致：蒙特卡洛 / Sarsa / Q-learning）
ALGORITHM_LABELS = {
    "montecarlo": "蒙特卡洛",
    "sarsa": "Sarsa",
    "qlearning": "Q-learning",
}
# 算法 → 主线颜色（均值/误差棒/汇总线按算法着色，便于跨图对照）
ALGORITHM_COLORS = {
    "montecarlo": "#1565c0",  # 蓝
    "sarsa": "#e65100",       # 橙
    "qlearning": "#2e7d32",   # 绿
}


def setup_style() -> None:
    """配置中文字体与负号显示（Windows 常见字体优先，缺失时退回默认）。"""
    plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "Noto Sans CJK SC", "sans-serif"]
    plt.rcParams["axes.unicode_minus"] = False


def latest_results(compare_root: Path) -> Optional[Path]:
    """扫描 compare_root 下的 run-*/results.json，按目录名（时间戳）取最新一次运行。"""
    if not compare_root.is_dir():
        return None
    candidates: List[Path] = sorted(compare_root.glob("run-*/results.json"))
    return candidates[-1] if candidates else None


def load_results(results_path: Path) -> Dict:
    """读取 results.json；文件必须存在且为合法 JSON（由 Java 端 AlgorithmCompareWriter 产出）。"""
    with results_path.open("r", encoding="utf-8") as f:
        return json.load(f)


def algorithm_label(algorithm: str) -> str:
    """算法标识的展示名：未知标识（异常数据）时原样输出，便于暴露 schema 变化。"""
    return ALGORITHM_LABELS.get(algorithm, algorithm)


def algorithm_color(algorithm: str) -> str:
    """算法的绘图主色：未知标识时退回蓝色。"""
    return ALGORITHM_COLORS.get(algorithm, "#1565c0")


def algorithm_values(map_result: Dict) -> Dict[str, Dict]:
    """提取单张地图各算法的统计量，键 = 算法标识（保持 JSON 内顺序）。"""
    return {entry["algorithm"]: entry for entry in map_result["algorithms"]}


def map_title(map_result: Dict) -> str:
    """子图标题：从 mapPath（如 map/3/map.csv）提取地图编号。"""
    return f"地图 {Path(map_result['mapPath']).parent.name}"


def draw_algorithm_points(ax: plt.Axes, map_result: Dict) -> None:
    """在单个子图上画一张地图的三种算法：散点 + 均值误差棒 + 零参考线。"""
    entries: Dict[str, Dict] = algorithm_values(map_result)
    x = np.arange(len(entries))
    rng = np.random.default_rng(42)  # 固定种子：同一数据两次出图散点位置一致

    for i, (algorithm, entry) in enumerate(entries.items()):
        rewards = entry["repRewards"]
        color = algorithm_color(algorithm)
        jitter = rng.uniform(-JITTER_SPAN, JITTER_SPAN, size=len(rewards))
        ax.scatter(np.full(len(rewards), i) + jitter, rewards, s=12,
                   color=color, alpha=0.35, zorder=2)
        ax.errorbar([i], [entry["meanReward"]], yerr=[entry["stdReward"]],
                    fmt="o-", color=color, ecolor=color, capsize=4, linewidth=1.8,
                    markersize=5, zorder=3, label=f"{algorithm_label(algorithm)}（均值±σ）")

    ax.axhline(0, color=ZERO_LINE_COLOR, linewidth=0.8, zorder=1)
    ax.set_xticks(x)
    ax.set_xticklabels([algorithm_label(a) for a in entries], fontsize=9)
    ax.set_ylabel("贪婪回合总奖励")
    ax.grid(axis="y", alpha=0.3)
    ax.legend(fontsize=7, loc="lower left")


def plot_per_map(results: Dict, out_path: Path) -> None:
    """图 1：每张地图一个子图（每行 5 个），统一样式比较三种算法。"""
    maps: List[Dict] = results["maps"]
    rows = math.ceil(len(maps) / SUBPLOT_COLS)
    fig, axes = plt.subplots(rows, SUBPLOT_COLS,
                             figsize=(SUBPLOT_COLS * 3.4, rows * 3.2),
                             squeeze=False, dpi=110)
    meta = results["meta"]
    for ax, map_result in zip(axes.ravel(), maps):
        draw_algorithm_points(ax, map_result)
        ax.set_title(map_title(map_result), fontsize=10)
    for ax in axes.ravel()[len(maps):]:  # 末行多余子图隐藏
        ax.set_visible(False)

    fig.suptitle(
        f"MonteCarlo / Sarsa / Q-learning 对比（ε=1，每算法 {meta['repetitions']} 次重复 × "
        f"{meta['episodes']} episode 训练后全程贪婪走一遍）",
        fontsize=12)
    fig.tight_layout(rect=(0, 0, 1, 0.95))
    fig.savefig(out_path)
    plt.close(fig)


def plot_summary(results: Dict, out_path: Path) -> None:
    """图 2：全部地图的汇总对比——每张地图每个算法一条细线，叠加全地图平均（加粗）。"""
    maps: List[Dict] = results["maps"]
    first_map_entries: Dict[str, Dict] = algorithm_values(maps[0])
    algorithms: List[str] = list(first_map_entries)  # 三类算法，顺序 = JSON 顺序
    x = np.arange(len(algorithms))

    fig, ax = plt.subplots(figsize=(11, 6), dpi=110)
    for map_result in maps:  # 每张地图：三种算法各一条细线（不参与图例，颜色已可辨）
        entries = algorithm_values(map_result)
        for i, algorithm in enumerate(algorithms):
            ax.plot(x[i], entries[algorithm]["meanReward"], color=algorithm_color(algorithm),
                    linewidth=0.9, alpha=THIN_LINE_ALPHA, zorder=2)

    # 全地图平均：按算法着色加粗，带图例
    matrix = np.array([[algorithm_values(m)[a]["meanReward"] for a in algorithms] for m in maps])
    for i, algorithm in enumerate(algorithms):
        ax.plot(x[i], matrix[:, i].mean(), color=algorithm_color(algorithm), marker="o",
                linewidth=2.8, zorder=3, label=f"{algorithm_label(algorithm)}（全地图平均）")

    ax.axhline(0, color=ZERO_LINE_COLOR, linewidth=0.8)
    ax.set_xticks(x)
    ax.set_xticklabels([algorithm_label(a) for a in algorithms])
    ax.set_ylabel("平均贪婪回合总奖励")
    ax.set_title("三种算法在全部地图上的平均贪婪奖励对比（ε=1，行为策略全程随机探索）")
    ax.grid(axis="y", alpha=0.3)
    ax.legend()
    fig.tight_layout()
    fig.savefig(out_path)
    plt.close(fig)


def print_best_per_map(results: Dict) -> None:
    """控制台摘要：每张地图按平均贪婪奖励给出最优算法，并汇总全地图平均。"""
    print("\n各地图最优算法（按平均贪婪奖励）:")
    all_means: Dict[str, List[float]] = {}
    for map_result in results["maps"]:
        entries = list(map_result["algorithms"])
        best = max(entries, key=lambda e: e["meanReward"])
        for entry in entries:
            all_means.setdefault(entry["algorithm"], []).append(entry["meanReward"])
        print(f"  {map_result['mapPath']:<16} {algorithm_label(best['algorithm']):<8} "
              f"mean={best['meanReward']:>8.2f}  抵达终点 {best['terminatedCount']}"
              f"/{len(best['repRewards'])}")

    print("\n全地图平均贪婪奖励:")
    for algorithm, means in all_means.items():
        print(f"  {algorithm_label(algorithm):<10} mean={sum(means) / len(means):>8.2f}")


def main() -> None:
    """解析参数、加载最新结果、出图并打印摘要。"""
    parser: argparse.ArgumentParser = argparse.ArgumentParser(
        description="算法对比实验结果可视化（intention-16）")
    parser.add_argument("--results", help="指定 results.json 路径（缺省取 data/algorithm-compare 下最新一次运行）")
    args: argparse.Namespace = parser.parse_args()

    results_path: Optional[Path] = Path(args.results) if args.results else latest_results(COMPARE_ROOT)
    if results_path is None or not results_path.is_file():
        print("未找到 data/algorithm-compare/run-*/results.json，请先运行 Java 端实验：\n"
              "  mvn compile exec:java -Dexec.mainClass=com.metrocarlo.experiment.AlgorithmComparison",
              file=sys.stderr)
        raise SystemExit(1)

    setup_style()
    results: Dict = load_results(results_path)
    out_dir: Path = results_path.parent
    per_map_png = out_dir / "compare_per_map.png"
    summary_png = out_dir / "compare_summary.png"
    plot_per_map(results, per_map_png)
    plot_summary(results, summary_png)
    print(f"已渲染: {per_map_png}")
    print(f"已渲染: {summary_png}")
    print_best_per_map(results)


if __name__ == "__main__":
    main()
