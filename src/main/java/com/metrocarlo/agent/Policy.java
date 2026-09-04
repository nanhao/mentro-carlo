package com.metrocarlo.agent;

import java.util.Map;

import com.metrocarlo.world.Cell;
import com.metrocarlo.world.World;

import lombok.Getter;
import lombok.Setter;

public abstract class Policy {

    @Getter @Setter
    protected World world;

    @Getter @Setter
    protected Map<StateActionPair, Double> qValues = new java.util.HashMap<>();

    @Getter @Setter
    protected Map<Cell, Double> stateValues = new java.util.HashMap<>();

    public abstract int determineAction();
}
