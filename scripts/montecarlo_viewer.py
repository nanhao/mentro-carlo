# -*- coding: utf-8 -*-
"""蒙特卡洛 trace 可视化查看器（cli 入口）。

用法：
    python montecarlo_viewer.py              # 自动发现 data/trace 下各 map-N 的最新一次运行
    python montecarlo_viewer.py --map 1      # 只看 map-1（同 --map map-1 / 1）
    python montecarlo_viewer.py --trace <路径>/trace.json   # 直接加载指定 trace

发现规则：data/trace/map-*/run-*/trace.json 中按目录名（时间戳）取每个 map 的最新 run；
若一个 map 都未发现，仍会打开空窗口（可通过「浏览…」手动加载）。入口脚本按
code-rule 规范允许在模块级定义 cli 函数与 main 装配；业务逻辑全部在 viewer_app.py。
"""

import argparse
import sys
import tkinter as tk
from pathlib import Path
from typing import Dict

from viewer_app import ViewerApp


def _latest_runs(trace_root: Path) -> Dict[str, Path]:
    """扫描 trace_root 下的 map-*/run-*/trace.json，返回 {map-id: 该 map 最新 run 的 trace}。"""
    found: Dict[str, Path] = {}
    if not trace_root.is_dir():
        return found
    for run_dir in sorted(trace_root.glob("map-*/run-*")):
        map_id: str = run_dir.parent.name          # map-1 … map-N
        candidate: Path = run_dir / "trace.json"
        if candidate.is_file():
            found[map_id] = candidate              # 目录名排序 = 时间序，后者覆盖前者 → 最新
    return found


def _pick_trace(trace_maps: Dict[str, Path], map_arg: str,
                trace_arg: str) -> Path:
    """按命令行参数挑选初始 trace：--trace 优先；其次 --map；缺省取最后一个 map。"""
    if trace_arg:
        return Path(trace_arg)
    if map_arg:
        key: str = map_arg if map_arg.startswith("map-") else f"map-{map_arg}"
        if key not in trace_maps:
            print(f"错误：未找到 {key} 的 trace（data/trace 下现有：{', '.join(trace_maps) or '无'}）",
                  file=sys.stderr)
            raise SystemExit(1)
        return trace_maps[key]
    return trace_maps[max(trace_maps)] if trace_maps else Path("")


def main() -> None:
    """解析参数、发现 trace、创建窗口并进入主循环。"""
    parser: argparse.ArgumentParser = argparse.ArgumentParser(
        description="蒙特卡洛评估与控制过程 trace 可视化查看器")
    parser.add_argument("--map", help="初始加载的 map-id（如 1 或 map-1；缺省为最新发现的一个）")
    parser.add_argument("--trace", help="直接指定 trace.json 路径（优先于 --map）")
    args: argparse.Namespace = parser.parse_args()
    trace_root: Path = Path(__file__).resolve().parent.parent / "data" / "trace"
    trace_maps: Dict[str, Path] = _latest_runs(trace_root)
    initial: Path = _pick_trace(trace_maps, args.map or "", args.trace or "")
    root: tk.Tk = tk.Tk()
    ViewerApp(root, trace_maps, initial)
    root.mainloop()


if __name__ == "__main__":
    main()
