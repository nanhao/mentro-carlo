package com.metrocarlo.montecarlo;

import java.util.Map;

import com.metrocarlo.agent.Policy;
import com.metrocarlo.agent.StateActionPair;
import com.metrocarlo.common.Constants;
import com.metrocarlo.world.Cell;
import com.metrocarlo.world.World;

import lombok.Getter;
import lombok.Setter;

public abstract class QAndSBuilder {
    @Getter @Setter
    protected double alpha = Constants.DEFAULT_ALPHA;

    @Getter @Setter
    protected double gamma = Constants.DEFAULT_GAMMA;

    @Getter
    protected World world;

    @Getter
    protected Map<StateActionPair, Double> qValues = new java.util.HashMap<>();

    @Getter
    protected Map<Cell, Double> stateValues = new java.util.HashMap<>();

    @Getter @Setter
    protected int maxEpisode = Constants.DEFAULT_MAX_EPISODE;

    /** 单个回合的最大步数上限，防止策略成环导致回合永不终止 */
    @Getter @Setter
    protected int maxStepsPerEpisode = Constants.MAX_STEP_PER_EPISODE;

    protected int episodeCount = 0;

    protected Policy policy;

    public QAndSBuilder(World world, Policy policy) {
        this.world = world;
        this.policy = policy;
    }

    public abstract void buildQValuesAndSValues();
}
