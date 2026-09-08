package com.metrocarlo.world;

import java.util.ArrayList;
import java.util.List;

import com.metrocarlo.common.Constants;
import com.metrocarlo.history.ActionRecord;

import lombok.Getter;
import lombok.Setter;

public class World {

    @Getter @Setter
    private final int width;
    @Getter @Setter
    private final int height;

    private int[][] map; // 值域定义在 com.metrocarlo.common.Constants.CELL_TYPE_* 常量组
    private int[][] initialMap; // 初始地图副本：reset 时还原被消耗的苹果/炸弹，保证多局环境一致

    @Getter
    private final int startX = 0;
    @Getter
    private final int startY = 0;
    private int currentX = 0;
    private int currentY = 0;

    @Getter
    private int stepCount = 0;

    @Getter
    private int totalReward = 0;

    @Getter
    private int lastReward = 0;

    @Getter
    private List<ActionRecord> history = new ArrayList<>();

    public World(int width, int height) {
        this.width = width;
        this.height = height;
        map = new int[width][height];
        initialMap = deepCopyMap(map);
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
        initialMap = deepCopyMap(map);
    }

    public World(World world) {
        this.width = world.width;
        this.height = world.height;
        this.map = deepCopyMap(world.map);
        this.initialMap = deepCopyMap(world.initialMap);
        this.currentX = world.currentX;
        this.currentY = world.currentY;
        this.stepCount = world.stepCount;
        this.totalReward = world.totalReward;
        this.history = new ArrayList<>(world.history);
    }

    public void reset() {
        restoreInitialMap();
        currentX = startX;
        currentY = startY;
        stepCount = 0;
        totalReward = 0;
        lastReward = 0;
        history.clear();
    }

    /** 把地图还原为初始状态（恢复被消耗的苹果/炸弹） */
    private void restoreInitialMap() {
        for (int x = 0; x < width; x++) {
            System.arraycopy(initialMap[x], 0, map[x], 0, height);
        }
    }

    /** 深拷贝地图数组 */
    private static int[][] deepCopyMap(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int x = 0; x < source.length; x++) {
            copy[x] = source[x].clone();
        }
        return copy;
    }

    /**
     * 返回 (x, y) 处的格子快照，携带坐标与类型。
     * <p>
     * 对快照的修改不会影响世界，需通过 {@link #setCell(Cell)} 写回后生效。
     *
     * @param x 横向坐标（0 ~ width-1，自左向右）
     * @param y 纵向坐标（0 ~ height-1，自下向上）
     */
    public Cell getCell(int x, int y) {
        return new Cell(x, y, map[x][y]);
    }

    /**
     * 设置 (x, y) 处的格子类型，取值应为 Constants 中 CELL_TYPE_* 常量之一。
     *
     * @param x     横向坐标（0 ~ width-1，自左向右）
     * @param y     纵向坐标（0 ~ height-1，自下向上）
     * @param value 格子类型编码
     */
    public void setCell(int x, int y, int value) {
        map[x][y] = value;
    }

    /**
     * 把格子的最新状态写回世界：按 cell 携带的坐标更新对应格子的类型。
     *
     * @param cell 格子快照（通常由 {@link #getCell(int, int)} 取出并修改后传入）
     */
    public void setCell(Cell cell) {
        map[cell.getX()][cell.getY()] = cell.getType();
    }

    public boolean isTerminated() {
        return getCurrentCell().getType() == Constants.CELL_TYPE_TERMINAL;
    }

    public Cell getCurrentCell() {
        return getCell(currentX, currentY);
    }

    public void setCurrentCell(Cell cell) {
        if (!isValidAgentPosition(cell.getX(), cell.getY())) {
            throw new IllegalArgumentException("Invalid agent position: " + cell.getX() + ", " + cell.getY());
        }

        currentX = cell.getX();
        currentY = cell.getY();
    }

    public List<Cell> getCells() {
        List<Cell> cells = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                cells.add(getCell(x, y));
            }
        }
        return cells;
    }

    /**
     * 返回初始地图（道具未被消耗前）按 CSV 行序排列的类型网格深拷贝，修改返回值不影响世界内部状态。
     * <p>
     * 与 {@link #World(String)} 的解析约定一致：第 0 行对应地图最上方一行（y = height - 1），
     * 最后一行对应 y = 0——供 trace 的 meta.initialMap 等需要按行序渲染/序列化的场景直接使用。
     *
     * @return grid[rowIndex][x]，grid.length == height，每行长度 == width
     */
    public int[][] getInitialMapInCsvRowOrder() {
        int[][] grid = new int[height][width];
        for (int rowIndex = 0; rowIndex < height; rowIndex++) {
            int y = height - 1 - rowIndex;
            for (int x = 0; x < width; x++) {
                grid[rowIndex][x] = initialMap[x][y];
            }
        }
        return grid;
    }

    private boolean isValidAgentPosition(int x, int y) {
        if (!(x >= 0 && x < width && y >= 0 && y < height)) {
            return false;
        }

        if (getCell(x, y).getType() == Constants.CELL_TYPE_WALL) {
            return false;
        }

        return true;
    }

    /**
     * 返回 currentCell 指定方向上的相邻格子快照；该方向越出边界时返回 null。
     * <p>
     * 邻居完全基于传入的 currentCell 计算，与 agent 当前位置无关。
     *
     * @param currentCell 参照格子快照
     * @param direction   方向常量（见 Constants.DIRECTION_*）
     */
    public Cell getNeighborCell(Cell currentCell, int direction) {
        switch (direction) {
            case Constants.DIRECTION_UP:
                if (isAtTopBorder(currentCell)) return null;
                return new Cell(currentCell.getX(), currentCell.getY() + 1,
                        map[currentCell.getX()][currentCell.getY() + 1]);
            case Constants.DIRECTION_DOWN:
                if (isAtBottomBorder(currentCell)) return null;
                return new Cell(currentCell.getX(), currentCell.getY() - 1,
                        map[currentCell.getX()][currentCell.getY() - 1]);
            case Constants.DIRECTION_LEFT:
                if (isAtLeftBorder(currentCell)) return null;
                return new Cell(currentCell.getX() - 1, currentCell.getY(),
                        map[currentCell.getX() - 1][currentCell.getY()]);
            case Constants.DIRECTION_RIGHT:
                if (isAtRightBorder(currentCell)) return null;
                return new Cell(currentCell.getX() + 1, currentCell.getY(),
                        map[currentCell.getX() + 1][currentCell.getY()]);
            default:
                throw new IllegalArgumentException("Invalid direction: " + direction);
        }
    }

    public Cell move(int direction) {
        if (isTerminated()) {
            throw new RuntimeException("World is terminated");
        }

        Cell currentCell = getCurrentCell();
        Cell nextCell = getNeighborCell(currentCell, direction);

        stepCount++;
        int reward = Constants.STEP_PUNISH + computeReward(currentCell, nextCell);
        totalReward += reward;
        lastReward = reward;

        if (nextCell == null || nextCell.getType() == Constants.CELL_TYPE_WALL) {
            nextCell = currentCell;
        } else if (nextCell.getType() == Constants.CELL_TYPE_BOMB ||
                   nextCell.getType() == Constants.CELL_TYPE_APPLE) {
            // 只有真实进入道具格才消耗；原地不动（撞墙/越界）时不得误伤脚下格子
            nextCell.setType(Constants.CELL_TYPE_EMPTY);
            setCell(nextCell);
        }

        setCurrentCell(nextCell);
        history.add(new ActionRecord(currentCell, direction, reward, totalReward, nextCell));
        return nextCell;
    }

    private int computeReward(Cell currentCell, Cell nextCell) {
        if (nextCell == null) {
            return Constants.REWARD_WALL;
        }

        if (nextCell.getType() == Constants.CELL_TYPE_WALL) {
            return Constants.REWARD_WALL;
        }

        if (nextCell.getType() == Constants.CELL_TYPE_BOMB) {
            return Constants.REWARD_BOMB;
        }

        if (nextCell.getType() == Constants.CELL_TYPE_APPLE) {
            return Constants.REWARD_APPLE;
        }

        return 0;
    }

    public boolean isAtLeftBorder(Cell cell) {
        return cell.getX() == 0;
    }

    public boolean isAtRightBorder(Cell cell) {
        return cell.getX() == width - 1;
    }

    public boolean isAtTopBorder(Cell cell) {
        return cell.getY() == height - 1;
    }

    public boolean isAtBottomBorder(Cell cell) {
        return cell.getY() == 0;
    }
}
