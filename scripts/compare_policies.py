# -*- coding: utf-8 -*-
"""策略对比实验可视化脚本（intention-7）：读取 Java 端 PolicyComparison 导出的
results.json，对每张地图比较 RandomPolicy 与不同 ε 的 EpsilonGreedyPolicy 效果。

用法（在项目根目录执行）：
    python scripts/compare_policies.py                   # 自动取 data/compare 下最新一次运行
    python scripts/compare_policies.py --results <路径>/results.json   # 指定结果文件

结果文件由 Java 端产出（data/compare/run-<时间戳>/results.json，需先运行：
mvn compile exec:java -Dexec.mainClass=com.metrocarlo.experiment.PolicyComparison）。
图表输出到 results.json 所在的 run 目录：
    compare_per_map.png   每张地图一个子图：各策略配置的平均贪婪奖励
                          （误差棒 = 标准差，灰点 = 50 次重复的原始奖励散点）
    compare_summary.png   全部地图的趋势对比：每张地图一条细线 + 全地图平均（加粗）

依赖 matplotlib（未安装时执行 pip install matplotlib）。
"""

import argparse
import json
import math
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import matplotlib
import numpy as np

matplotlib.use("Agg")  # 无窗口环境直接出图，只写文件不弹窗
import matplotlib.pyplot as plt

PROJECT_ROOT = Path(__file__).resolve().parent.parent
COMPARE_ROOT = PROJECT_ROOT / "data" / "compare"

# ---- 绘图样式（全局统一） ----
RAW_POINT_COLOR = "#9e9e9e"   # 重复奖励散点：灰
MEAN_COLOR = "#1565c0"        # 均值折线/误差棒：蓝（与 viewer 正值色一致）
MEAN_ERROR_COLOR = "#64b5f6"  # 误差棒：浅蓝
ZERO_LINE_COLOR = "#bbbbbb"   # 零奖励参考线：浅灰
THIN_LINE_COLOR = "#90caf9"   # 汇总图中单地图细线：浅蓝
AVG_LINE_COLOR = "#0d47a1"    # 汇总图中全地图平均线：深蓝
JITTER_SPAN = 0.18            # 散点横向抖动幅度（相对刻度间距）
SUBPLOT_COLS = 5              # 每行子图数


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
    """读取 results.json；文件必须存在且为合法 JSON（由 Java 端 CompareResultWriter 产出）。"""
    with results_path.open("r", encoding="utf-8") as f:
        return json.load(f)


def policy_label(policy: Dict) -> str:
    """策略配置的展示名：Random 或 ε=0.1 形式。"""
    if policy["policy"] == "random":
        return "Random"
    return f"ε={policy['epsilon']:.1f}"


def policy_values(map_result: Dict) -> Tuple[List[str], np.ndarray, np.ndarray, List[List[int]]]:
    """提取单张地图各策略配置的标签 / 平均奖励 / 标准差 / 重复奖励原始值。"""
    labels: List[str] = []
    means: List[float] = []
    stds: List[float] = []
    raws: List[List[int]] = []
    for policy in map_result["policies"]:
        labels.append(policy_label(policy))
        means.append(policy["meanReward"])
        stds.append(policy["stdReward"])
        raws.append(policy["repRewards"])
    return labels, np.array(means), np.array(stds), raws


def map_title(map_result: Dict) -> str:
    """子图标题：从 mapPath（如 map/3/map.csv）提取地图编号。"""
    return f"地图 {Path(map_result['mapPath']).parent.name}"


def draw_policy_points(ax: plt.Axes, map_result: Dict) -> None:
    """在单个子图上画一种地图的全部策略配置：散点 + 均值误差棒 + 零参考线。"""
    labels, means, stds, raws = policy_values(map_result)
    x = np.arange(len(labels))

    # 50 次重复的原始奖励散点（横向抖动），展示均值背后的分布
    rng = np.random.default_rng(42)  # 固定种子：同一数据两次出图散点位置一致
    for i, rewards in enumerate(raws):
        jitter = rng.uniform(-JITTER_SPAN, JITTER_SPAN, size=len(rewards))
        ax.scatter(np.full(len(rewards), i) + jitter, rewards, s=12,
                   color=RAW_POINT_COLOR, alpha=0.45, zorder=2)

    ax.errorbar(x, means, yerr=stds, fmt="o-", color=MEAN_COLOR,
                ecolor=MEAN_ERROR_COLOR, capsize=4, linewidth=1.8,
                markersize=5, zorder=3, label="平均贪婪奖励 ± 标准差")
    ax.axhline(0, color=ZERO_LINE_COLOR, linewidth=0.8, zorder=1)
    ax.set_xticks(x)
    ax.set_xticklabels(labels, rotation=45, ha="right", fontsize=8)
    ax.set_ylabel("贪婪回合总奖励")
    ax.grid(axis="y", alpha=0.3)
    ax.legend(fontsize=7, loc="lower left")


def plot_per_map(results: Dict, out_path: Path) -> None:
    """图 1：每张地图一个子图（每行 5 个），统一样式比较各策略配置。"""
    maps: List[Dict] = results["maps"]
    rows = math.ceil(len(maps) / SUBPLOT_COLS)
    fig, axes = plt.subplots(rows, SUBPLOT_COLS,
                             figsize=(SUBPLOT_COLS * 3.4, rows * 3.2),
                             squeeze=False, dpi=110)
    meta = results["meta"]
    for ax, map_result in zip(axes.ravel(), maps):
        draw_policy_points(ax, map_result)
        ax.set_title(map_title(map_result), fontsize=10)
    for ax in axes.ravel()[len(maps):]:  # 末行多余子图隐藏
        ax.set_visible(False)

    fig.suptitle(
        f"RandomPolicy vs EpsilonGreedyPolicy（每配置 {meta['repetitions']} 次重复 × "
        f"{meta['episodes']} episode 训练后全程贪婪走一遍）",
        fontsize=12)
    fig.tight_layout(rect=(0, 0, 1, 0.95))
    fig.savefig(out_path)
    plt.close(fig)


def plot_summary(results: Dict, out_path: Path) -> None:
    """图 2：全部地图的趋势对比——每张地图一条细线，叠加全地图平均（加粗）。"""
    maps: List[Dict] = results["maps"]
    labels, _, _, _ = policy_values(maps[0])
    matrix = np.array([policy_values(m)[1] for m in maps])  # shape = (地图数, 策略数)
    x = np.arange(len(labels))

    fig, ax = plt.subplots(figsize=(11, 6), dpi=110)
    for i, row in enumerate(matrix):  # 每张地图一条细线（首条带图例）
        ax.plot(x, row, color=THIN_LINE_COLOR, linewidth=1.0, alpha=0.55, zorder=2,
                label="每张地图" if i == 0 else None)
    ax.plot(x, matrix.mean(axis=0), color=AVG_LINE_COLOR, linewidth=2.8, marker="o",
            zorder=3, label="全地图平均")

    ax.axhline(0, color=ZERO_LINE_COLOR, linewidth=0.8)
    ax.set_xticks(x)
    ax.set_xticklabels(labels, rotation=45, ha="right")
    ax.set_ylabel("平均贪婪回合总奖励")
    ax.set_title("探索率 ε 对蒙特卡洛控制效果的影响（各地图平均贪婪奖励）")
    ax.grid(axis="y", alpha=0.3)
    ax.legend()
    fig.tight_layout()
    fig.savefig(out_path)
    plt.close(fig)


def print_best_per_map(results: Dict) -> None:
    """控制台摘要：每张地图按平均贪婪奖励给出最优策略配置。"""
    print("\n各地图最优策略（按平均贪婪奖励）:")
    for map_result in results["maps"]:
        best = max(map_result["policies"], key=lambda p: p["meanReward"])
        print(f"  {map_result['mapPath']:<16} {policy_label(best):<8} "
              f"mean={best['meanReward']:>8.2f}  抵达终点 {best['terminatedCount']}"
              f"/{len(best['repRewards'])}")


def main() -> None:
    """解析参数、加载最新结果、出图并打印摘要。"""
    parser: argparse.ArgumentParser = argparse.ArgumentParser(
        description="策略对比实验结果可视化（intention-7）")
    parser.add_argument("--results", help="指定 results.json 路径（缺省取 data/compare 下最新一次运行）")
    args: argparse.Namespace = parser.parse_args()

    results_path: Optional[Path] = Path(args.results) if args.results else latest_results(COMPARE_ROOT)
    if results_path is None or not results_path.is_file():
        print("未找到 data/compare/run-*/results.json，请先运行 Java 端实验：\n"
              "  mvn compile exec:java -Dexec.mainClass=com.metrocarlo.experiment.PolicyComparison",
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
