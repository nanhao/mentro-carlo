package com.metrocarlo.world;

import lombok.Getter;
import lombok.Setter;

/**
 * 格子：网格世界中单个格子的坐标与类型的值对象。
 * <p>
 * 由 {@link World#getCell(int, int)} 返回的是该格子的快照：修改快照属性不会影响世界，
 * 需调用 {@link World#setCell(Cell)} 写回后生效。type 的取值见
 * {@link com.metrocarlo.common.Constants} 的 CELL_TYPE_* 常量组。
 */
public class Cell {
    @Getter @Setter
    private int x; // 横向坐标（0 ~ width-1，自左向右）
    @Getter @Setter
    private int y; // 纵向坐标（0 ~ height-1，自下向上）
    @Getter @Setter
    private int type; // 值域定义在 com.metrocarlo.common.Constants.CELL_TYPE_* 常量组

    public Cell(int x, int y, int type) {
        this.x = x;
        this.y = y;
        this.type = type;
    }
}
