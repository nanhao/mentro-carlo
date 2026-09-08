package com.metrocarlo.montecarlo;

import java.util.Map;

import com.metrocarlo.agent.Policy;
import com.metrocarlo.agent.StateActionPair;
import com.metrocarlo.common.Constants;
import com.metrocarlo.world.Cell;
import com.metrocarlo.world.World;

import lombok.Getter;
import lombok.Setter;

public class Sarsa {
    @Getter @Setter
    private double alpha = Constants.DEFAULT_ALPHA;

    @Getter @Setter
    private double gamma = Constants.DEFAULT_GAMMA;

    @Getter
    private World world;

    @Getter
    private Map<StateActionPair, Double> qValues = new java.util.HashMap<>();

    @Getter
    private Map<StateActionPair, Double> qAlphas = new java.util.HashMap<>();

    @Getter @Setter
    private int maxEpisode = Constants.DEFAULT_MAX_EPISODE;

    /** 单个回合的最大步数上限，防止策略成环导致回合永不终止 */
    @Getter @Setter
    private int maxStepsPerEpisode = Constants.MAX_STEP_PER_EPISODE;

    private int episodeCount = 0;

    private Policy policy;

    public Sarsa(World world, Policy policy) {
        this.world = world;
        this.policy = policy;
    }

    public void buildQValues() {
        qValues.clear();
        initQValues();
        initQAlphas();
        episodeCount = 0;

        while (episodeCount < maxEpisode) {
            episodeCount++;

            World tempWorld = new World(world);
            // 从起点干净开始，避免带入原世界的当前位置与历史
            tempWorld.reset();
            // 让策略基于本回合实际移动的世界做决策
            policy.setWorld(tempWorld);

            int steps = 0;
            int direction = policy.determineAction();
            while (!tempWorld.isTerminated() && steps++ < maxStepsPerEpisode) {
                StateActionPair stateActionPair = new StateActionPair(tempWorld.getCurrentCell(), direction);
                double q = qValues.get(stateActionPair);
                tempWorld.move(direction);
                int reward = tempWorld.getLastReward();
                direction = policy.determineAction();
                StateActionPair nextStateActionPair = new StateActionPair(tempWorld.getCurrentCell(), direction);
                double nextQ = qValues.get(nextStateActionPair);
                qValues.put(stateActionPair, q + qAlphas.get(stateActionPair) * (reward + gamma * nextQ - q));
                if (Constants.DECAY_ALPHA) {
                    qAlphas.put(stateActionPair, qAlphas.get(stateActionPair) * Constants.DECAY_ALPHA_RATE);
                }
            }

            policy.setQValues(qValues);
        }
    }

    private void initQValues() {
        for (Cell cell : world.getCells()) {
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_UP), 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_RIGHT), 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_DOWN), 0.0);
            qValues.put(new StateActionPair(cell, Constants.DIRECTION_LEFT), 0.0);
        }
    }

    private void initQAlphas() {
        for (Cell cell : world.getCells()) {
            qAlphas.put(new StateActionPair(cell, Constants.DIRECTION_UP), alpha);
            qAlphas.put(new StateActionPair(cell, Constants.DIRECTION_RIGHT), alpha);
            qAlphas.put(new StateActionPair(cell, Constants.DIRECTION_DOWN), alpha);
            qAlphas.put(new StateActionPair(cell, Constants.DIRECTION_LEFT), alpha);
        }
    }
}
