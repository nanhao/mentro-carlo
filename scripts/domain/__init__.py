# -*- coding: utf-8 -*-
"""scripts/domain：viewer 入口的非脚本业务模块包。

本包内模块不作为命令行入口，仅被 scripts/ 下的入口脚本导入。模块按算法成组，
TD 算法组继承上一算法组（回放引擎 / 主窗口），画布差异小时直接复用：
- trace_replay.py / map_canvas.py / viewer_app.py —— 蒙特卡洛基线（montecarlo_viewer.py 消费）
- sarsa_trace_replay.py / sarsa_map_canvas.py / sarsa_viewer_app.py —— Sarsa（sarsa_viewer.py 消费）
- qlearning_trace_replay.py / qlearning_viewer_app.py —— Q-learning（qlearning_viewer.py 消费；
  画布与 sarsa 无差异，复用 sarsa_map_canvas.py）
"""
