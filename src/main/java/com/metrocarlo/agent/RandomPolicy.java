package com.metrocarlo.agent;

import java.util.Random;

import com.metrocarlo.common.Constants;

public class RandomPolicy extends Policy {

    private final Random random = new Random();

    @Override
    public int determineAction() {
        return random.nextInt(Constants.DIRECTION_COUNT);
    }
}