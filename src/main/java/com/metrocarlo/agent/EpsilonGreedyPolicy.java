package com.metrocarlo.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.metrocarlo.common.Constants;
import com.metrocarlo.world.Cell;
import com.metrocarlo.world.World;

import lombok.Getter;
import lombok.Setter;

public class EpsilonGreedyPolicy extends Policy {

    private final Random random = new Random();

    @Getter @Setter
    protected double epsilon = Constants.DEFAULT_EPSILON;

    public EpsilonGreedyPolicy(World world, Map<StateActionPair, Double> qValues, Map<Cell, Double> stateValues) {
        this.world = world;
        this.qValues = qValues;
        this.stateValues = stateValues;
    }

    @Override
    public int determineAction() {
        // 以 epsilon 概率随机探索，其余情况贪心选择 Q 值最大的动作
        if (random.nextDouble() < epsilon) {
            return random.nextInt(Constants.DIRECTION_COUNT);
        }

        // 在当前格子的 4 个方向中找 Q 值最大的动作，并列时随机取其一，避免初期固定偏向同一方向
        Cell currentCell = world.getCurrentCell();
        List<Integer> bestActions = new ArrayList<>();
        double bestValue = Double.NEGATIVE_INFINITY;
        for (int action = 0; action < Constants.DIRECTION_COUNT; action++) {
            Double value = qValues.get(new StateActionPair(currentCell, action));
            if (value == null) {
                continue;
            }
            if (value > bestValue) {
                bestValue = value;
                bestActions.clear();
                bestActions.add(action);
            } else if (value == bestValue) {
                bestActions.add(action);
            }
        }

        // Q 表尚未初始化时退化为随机动作
        if (bestActions.isEmpty()) {
            return random.nextInt(Constants.DIRECTION_COUNT);
        }
        return bestActions.get(random.nextInt(bestActions.size()));
    }
}