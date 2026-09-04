package com.metrocarlo.common;
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
}
