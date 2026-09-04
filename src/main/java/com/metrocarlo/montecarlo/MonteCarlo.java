package com.metrocarlo.montecarlo;

import java.util.List;
import java.util.Map;

import com.metrocarlo.agent.Policy;
import com.metrocarlo.agent.StateActionPair;
import com.metrocarlo.common.Constants;
import com.metrocarlo.world.Cell;
import com.metrocarlo.world.World;
import com.metrocarlo.world.history.ActionRecord;

public class MonteCarlo extends QAndSBuilder {

    public MonteCarlo(World world, Policy policy) {
        super(world, policy);
    }

    public void buildQValuesAndSValues() {
        qValues.clear();
        stateValues.clear();
        initQAndSValues();
        episodeCount = 0;

        while (episodeCount < maxEpisode) {
            episodeCount++;

            World tempWorld = new World(world);
            // 从起点干净开始，避免带入原世界的当前位置与历史
            tempWorld.reset();
            // 让策略基于本回合实际移动的世界做决策
            policy.setWorld(tempWorld);

            int steps = 0;
            while (!tempWorld.isTerminated() && steps++ < maxStepsPerEpisode) {
                int direction = policy.determineAction();
                tempWorld.move(direction);
            }

            double lastG = 0.0;
            List<ActionRecord> history = tempWorld.getHistory();
            // 倒序遍历并覆盖写：同一状态多次访问时保留最早一次（首访问）的回报，即 first-visit 语义
            for (int i = history.size() - 1; i >= 0; i--) {
                ActionRecord actionRecord = history.get(i);
                Cell cell = actionRecord.getCell();

                // 折扣回报：当步即时奖励 + gamma * 后继状态的回报
                double G = actionRecord.getReward() + gamma * lastG;

                double originalSValue = stateValues.get(cell);
                double newSValue = originalSValue + alpha * (G - originalSValue);
                stateValues.put(cell, newSValue);

                StateActionPair stateActionPair = new StateActionPair(cell, actionRecord.getAction());
                double originalQValue = qValues.get(stateActionPair);
                double newQValue = originalQValue + alpha * (G - originalQValue);
                qValues.put(stateActionPair, newQValue);
            }

            policy.setQValues(qValues);
            policy.setStateValues(stateValues);
        }
    }

    private void initQAndSValues() {
        for (Cell cell : world.getCells()) {
            stateValues.put(cell, 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_UP), 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_RIGHT), 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_DOWN), 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_LEFT), 0.0);
        }
    }
}
