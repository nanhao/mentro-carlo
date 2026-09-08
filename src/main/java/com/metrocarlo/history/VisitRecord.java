package com.metrocarlo.history;

import com.metrocarlo.common.Constants;

import lombok.Builder;
import lombok.Data;

/**
 * trace 中一行更新记录（JSON 中 visits 数组的一行），
 * 字段与 design/3/README.md §3.1「visits[] 字段字典」及 design/12/README.md §3.3 增量一一对应。
 * <p>
 * 行语义与行序按算法区分（见 {@link EpisodeTrace} 说明）：
 * montecarlo 行 = 回合结束后结算循环中的一次处理（先算 G 随即更新 S/Q，行序倒序）；
 * sarsa 行 = 一步在线 TD 更新（行内 td 携带明细，行序正序）。
 * 两种行共用公共 12 字段与 qOld/qNew；算法专属字段互不设置、JSON 互不输出。
 */
@Data
@Builder
public class VisitRecord {

    /** 动作方向字符串取值：up（对应 Constants.DIRECTION_UP） */
    public static final String ACTION_UP = "up";
    /** 动作方向字符串取值：right（对应 Constants.DIRECTION_RIGHT） */
    public static final String ACTION_RIGHT = "right";
    /** 动作方向字符串取值：down（对应 Constants.DIRECTION_DOWN） */
    public static final String ACTION_DOWN = "down";
    /** 动作方向字符串取值：left（对应 Constants.DIRECTION_LEFT） */
    public static final String ACTION_LEFT = "left";

    /**
     * 方向常量 → trace 动作字符串（取值见 ACTION_*，与 JSON 格式约定一致）。
     * 供各算法采集点复用（原为 MonteCarlo 私有方法，随 sarsa 采集入 design/12 提升到此处）。
     *
     * @param direction 方向常量（见 Constants.DIRECTION_*）
     * @return 动作字符串 up | right | down | left
     * @throws IllegalArgumentException 方向非法时抛出
     */
    public static String actionName(int direction) {
        switch (direction) {
            case Constants.DIRECTION_UP:
                return ACTION_UP;
            case Constants.DIRECTION_RIGHT:
                return ACTION_RIGHT;
            case Constants.DIRECTION_DOWN:
                return ACTION_DOWN;
            case Constants.DIRECTION_LEFT:
                return ACTION_LEFT;
            default:
                throw new IllegalArgumentException("Invalid direction: " + direction);
        }
    }

    /** episode 内正序步号（1 起）；GUI 回放顺序 = 按 no 升序 */
    private int no;
    /** 动作前所在格 x（World 坐标系） */
    private int x;
    /** 动作前所在格 y（World 坐标系） */
    private int y;
    /** 动作方向：up | right | down | left */
    private String action;
    /** 即时奖励（撞墙/越界 = -1、炸弹 = -5、苹果 = +2、空/终点 = 0） */
    private int reward;
    /** 该步后的累计奖励 */
    private int totalReward;
    /** 到达格 x（撞墙/越界时与 x 相同，原地不动） */
    private int nextX;
    /** 到达格 y（撞墙/越界时与 y 相同，原地不动） */
    private int nextY;
    /** 移动前所在格 (x, y) 的类型（该步开始时的格子状态，含此前各步的消耗结果） */
    private int fromType;
    /** 移动后所在格 (nextX, nextY) 的类型（道具被本步消耗后已为 0；进入终点为 4；撞墙/越界原地时 == fromType） */
    private int toType;
    /** γ × G(后继)，即折扣回报中来自后续状态的贡献（回合最后一步为 0） */
    private double discountedNextG;
    /** 本行回报 = reward + discountedNextG */
    private double G;
    /** 该格 stateValue 更新前取值（montecarlo 行；sarsa 无 stateValue 不设置） */
    private double sOld;
    /** 该格 stateValue 更新后取值 = sOld + alpha * (G - sOld)（montecarlo 行） */
    private double sNew;
    /** 该 (状态, 动作) 的 qValue 更新前取值 */
    private double qOld;
    /** 该 (状态, 动作) 的 qValue 更新后取值（montecarlo：qOld + alpha*(G - qOld)；sarsa：qOld + td.alpha*td.delta） */
    private double qNew;
    /** sarsa 行的本步 TD 更新明细（design/12 §3.3）；montecarlo 行为 null 且 JSON 不输出 */
    private TdUpdateRecord td;
}
