package com.metrocarlo.agent;

import com.metrocarlo.world.Cell;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StateActionPair {
    private Cell state;
    private int action;
}
