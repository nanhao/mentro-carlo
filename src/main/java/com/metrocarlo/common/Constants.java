package com.metrocarlo.common;

import java.nio.file.Path;

public class Constants {
    public static final int CELL_TYPE_EMPTY = 0;
    public static final int CELL_TYPE_WALL = 1;
    public static final int CELL_TYPE_BOMB = 2;
    public static final int CELL_TYPE_APPLE = 3;
    public static final int CELL_TYPE_TERMINAL = 4;

    public static final int REWARD_APPLE = 2;
    public static final int REWARD_WALL = -1;
    public static final int REWARD_BOMB = -5;

    public static final int DIRECTION_UP = 0;
    public static final int DIRECTION_RIGHT = 1;
    public static final int DIRECTION_DOWN = 2;
    public static final int DIRECTION_LEFT = 3;
    public static final int DIRECTION_COUNT = 4;

    public static final double DEFAULT_EPSILON = 0.1;
    public static final double DEFAULT_ALPHA = 0.5;
    public static final double DEFAULT_GAMMA = 0.9;

    public static final int MAX_STEP_PER_EPISODE = 1000;
    public static final int DEFAULT_MAX_EPISODE = 1000;

    public static final Path TRACE_ROOT = Path.of("data", "trace");
    public static final String TRACE_FILE_NAME = "trace.json";
}
