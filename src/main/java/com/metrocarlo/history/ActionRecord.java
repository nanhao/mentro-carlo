package com.metrocarlo.history;

import com.metrocarlo.world.Cell;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ActionRecord {
    private Cell cell;
    private int action; // 方向常量，见 com.metrocarlo.common.Constants.DIRECTION_*
    private int reward;
    private int totalReward;
    private Cell nextCell;
}
