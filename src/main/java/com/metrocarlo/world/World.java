package com.metrocarlo.world;

import lombok.Getter;
import lombok.Setter;

public class World {

    @Getter @Setter
    private final int width;
    @Getter @Setter
    private final int height;
    @Getter @Setter
    private final int startX = 0;
    @Getter @Setter
    private final int startY = 0;

    private int[][] map; // 值域定义在 com.metrocarlo.common.Constants.CELL_TYPE_* 常量组

    public World(int width, int height) {
        this.width = width;
        this.height = height;
        map = new int[width][height];
    }

    /**
     * 从 CSV 文件加载地图并初始化 width / height / map。
     * <p>
     * CSV 的读取、解析与校验委托给 {@link CsvParser}（异常规则见其说明）；
     * 单元格取值为格子类型编码（见 {@link com.metrocarlo.common.Constants}）：
     * 0=空、1=墙、2=炸弹、3=苹果、4=终点。
     * 坐标系与 README 一致：(0,0) 为左下角，y 自下向上，
     * 因此 CSV 的第一行对应地图最上方一行（y = height - 1），最后一行对应 y = 0。
     * 宽度取自 CSV 列数，高度取自 CSV 行数。
     *
     * @param csvPath 地图 CSV 文件路径
     */
    public World(String csvPath) {
        int[][] grid = CsvParser.parse(csvPath);
        this.width = grid[0].length;
        this.height = grid.length;
        map = new int[width][height];
        // 逐行填充：CSV 第 rowIndex 行对应 y = height - 1 - rowIndex（首行在最上方）
        for (int rowIndex = 0; rowIndex < height; rowIndex++) {
            int y = height - 1 - rowIndex;
            for (int x = 0; x < width; x++) {
                map[x][y] = grid[rowIndex][x];
            }
        }
    }

    /**
     * 返回 (x, y) 处的格子类型，取值为 Constants 中 CELL_TYPE_* 常量之一。
     *
     * @param x 横向坐标（0 ~ width-1，自左向右）
     * @param y 纵向坐标（0 ~ height-1，自下向上）
     */
    public int getCell(int x, int y) {
        return map[x][y];
    }

    /**
     * 设置 (x, y) 处的格子类型，取值应为 Constants 中 CELL_TYPE_* 常量之一。
     *
     * @param x     横向坐标（0 ~ width-1，自左向右）
     * @param y     纵向坐标（0 ~ height-1，自下向上）
     * @param value 格子类型编码
     * @return 设置后的值（即 value）
     */
    public int setCell(int x, int y, int value) {
        map[x][y] = value;
        return value;
    }

}
