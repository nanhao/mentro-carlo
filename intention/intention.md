# intention-1
1，我需要你帮我在 map 目录下生成 10 张不同的地图，分别放在名为 1-10 的十个子目录下。地图是 csv 格式。
2，我需要一个地图的可视化方案。要求每个子目录下都生成一张地图的 png 或 jpg 图片，格式由你决定，但要统一。可视化不一定要用 java 来实现，也可以用其他语言编写脚本放在 scripts 目录下使用

# intention-2
我需要一个 montecarlo 评估和控制过程每一步的可视化方案。要求：
- 需要有一个 gui 界面
- 界面上有以下按钮：
    - 执行一次 action
    - 执行完当前 episode
    - 执行完100个 episode（以当前episode为第1个）
    - 执行到所有 episode 结束
    - reset
- 界面上展示：
    - 地图，包括地图上每一个格子的类型
    - 在地图上标出 agent 当前位置
    - 在地图上标出 stateValues 和 qValues，并且附带颜色，正负用不同的颜色。正的用蓝色，蓝色越深越大。负的用红色，红色越深越小
    - 最近一个 action 详细信息，包括奖励和累计奖励
    - 最近一个 episode 结束后，每个格子的 G 计算过程
    - 最近一个 G 计算之后，stateValues 和 qValues 的更新过程
    - 当前 action 是当前 episode 的第几个 action
    - 当前 episode 是第几个 episode
为了实现这个可视化，需要在 MonteCarlo 里面增加 trace 数据。需要设计 trace 数据的格式。
入口放在 runMonteCarloOnOneMap 方法中，地图先写为 map/1/map.csv。执行后导出 trace 数据到 data 目录下。
然后在 scripts 下面新增一个 python 脚本，用来基于 trace 数据做可视化。java本身不做 gui，由 python 来做。

以上是完整需求，需要分解为几个小任务来做。本次先为界面做出原型设计。原型放在 requiements/2

# intention-3
基于 intention-2 的完整需求，以及已经完成的原型。本次要来设计和实现 trace 数据的生成。首先需要基于原型设计出 trace 数据的格式和文件组织方式。trace 数据保存在 data/trace 目录下。请你帮忙设计 trace 数据的格式和文件组织方式，但先不进行实现。

# intention-4
本次任务为实现 intentino-3 关于 trace 数据生成的设计。要求 trace 数据结构定义放在 world/history 下。MonteCarlo 中进行引用，实现 trace 数据生成。此次任务无需编写测试

# intention-5
本次任务实现 intention-2 完整需求中的可视化部分。参照 requirements\2\README.md 中 T4 的部分。这部分不需要写测试。

# intention-6
这是个重构任务，把 scripts 目录的非脚本入口文件挪到 domain 子目录下面去，这样不会混淆。本次任务不需要写测试。

# intention-7
编写一个新的脚本，针对地图1-10，每张地图做如下处理：
1，使用 RandomPolicy 的蒙特卡洛方法运行 1000个 episode，得到状态价值函数和动作价值函数，然后按照全程贪婪走一遍，得到单次奖励 rri。重复这个过程 50 次，得到每个地图的平均奖励 rri'
2，使用 EpsilonGreedyPolicy 的蒙特卡洛方法运行 1000 个 episode，epsilon 取 0.1，得到状态价值函数和动作价值函数，然后按照全程贪婪走一遍，得到单次奖励 rei-0.1。重复这个过程 50 次，得到每个地图的平均奖励 rei'-0.1
3，将 epsilon 取 0.2，重复2，得到 rei'-0.2
4，继续增大 epsilon，直到 0.9，得到 rei'-0.3  ~  rei'-0.9
然后对数据做可视化，比较在每个地图上 RandomPolicy 和 不同 epsilon 的 EpsilonGreedyPolicy 的效果
本次任务不用写测试。

# intention-8
为了避免策略成环，增加一个设定：每采取一个行动就扣一分，同时增加苹果的奖励分数和炸弹的惩罚分数。

# intention-9
尝试了 alpha 衰减，发现效果并不稳定。

# intention-10
我已手动实现了 sarsa，现在要像 montecarlo 一样的回放功能。不同的是，sarsa 是走一步更新一次，而且算法有些许不同，所以 ui 上的布局需要做些微调。
你帮我把做个 sarsa 回放的原型，不写代码，不写测试。只做原型

# intention-11
这是一个重构任务，把 history 包从 world 包里提出来，放到跟 world 同级。不需要写测试。重构完跑 mvn test 通过就行

# intention-12
这个任务是设计并实现 sarsa 的 trace 数据生成。
原型已经准备好，在 intention-10 中，你需要按照原型设计trace数据，尽可能复用 mentecarlo 的 trace 机制。

# intention-13
本次任务要实现一个新的脚本，功能是 sarsa 的回放 ui，参考 montecarlo 的实现。sarsa 的trace 数据已在上一个 intention 准备好了，不需要写测试。