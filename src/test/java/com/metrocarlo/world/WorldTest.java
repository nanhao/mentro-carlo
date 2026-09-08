package com.metrocarlo.world;

import com.metrocarlo.common.Constants;
import com.metrocarlo.history.ActionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link World} 的单元测试。
 * <p>
 * 坐标系约定与 README 一致：(0,0) 为左下角，y 自下向上；
 * 因此 CSV 的第一行对应地图最上方一行（y = height - 1），最后一行对应 y = 0。
 * CSV 读取与解析的细节行为（容错、校验）由 {@link CsvParserTest} 覆盖；
 * 格子状态通过 {@link Cell} 值对象读写；{@link World#move(int)} 的
 * 移动、奖励与历史记录行为在本文件覆盖。
 */
class WorldTest {

    /** JUnit 临时目录：测试输出统一写入临时目录，结束后自动删除，避免触碰真实数据文件 */
    @TempDir
    Path tempDir;

    /** 在临时目录中写入一份 CSV 地图文件并返回其路径 */
    private Path writeCsv(String content) throws IOException {
        Path csv = tempDir.resolve("map.csv");
        Files.writeString(csv, content, StandardCharsets.UTF_8);
        return csv;
    }

    /** CSV 地图应正确初始化宽高，且各类型格子按坐标系落位 */
    @Test
    void loadCsvShouldInitWidthHeightAndCells() throws IOException {
        Path csv = writeCsv(String.join("\n",
                "0,3,0,0,4", // y=3（最上排）：苹果(1,3)、终点(4,3)
                "0,0,0,3,0", // y=2：苹果(3,2)
                "0,0,1,0,2", // y=1：墙(2,1)、炸弹(4,1)
                "0,0,0,0,0"  // y=0（最下排）：全空，起点 (0,0) 所在行
        ));

        World world = new World(csv.toString());

        assertEquals(5, world.getWidth(), "宽度应等于 CSV 列数");
        assertEquals(4, world.getHeight(), "高度应等于 CSV 行数");
        assertEquals(Constants.CELL_TYPE_TERMINAL, world.getCell(4, 3).getType(), "首行末列应为终点");
        assertEquals(Constants.CELL_TYPE_APPLE, world.getCell(1, 3).getType(), "首行第 2 列应为苹果");
        assertEquals(Constants.CELL_TYPE_APPLE, world.getCell(3, 2).getType());
        assertEquals(Constants.CELL_TYPE_WALL, world.getCell(2, 1).getType());
        assertEquals(Constants.CELL_TYPE_BOMB, world.getCell(4, 1).getType());
        assertEquals(Constants.CELL_TYPE_EMPTY, world.getCell(0, 0).getType(), "起点格应为空");
    }

    /** 基于 Cell 操作：取出快照、修改类型、写回后应生效 */
    @Test
    void cellSnapshotShouldApplyWhenSetBack() throws IOException {
        Path csv = writeCsv("0,0\n0,0\n");

        World world = new World(csv.toString());

        Cell cell = world.getCell(1, 0); // y=0 为最下排（CSV 末行）
        assertEquals(1, cell.getX(), "Cell 应携带取出时的坐标");
        assertEquals(0, cell.getY());
        assertEquals(Constants.CELL_TYPE_EMPTY, cell.getType());

        cell.setType(Constants.CELL_TYPE_APPLE);
        world.setCell(cell);

        assertEquals(Constants.CELL_TYPE_APPLE, world.getCell(1, 0).getType(), "写回后格子类型应更新");
    }

    /** getNeighborCell 应基于传入的格子计算邻居，而不是 agent 当前位置 */
    @Test
    void getNeighborCellShouldUseGivenCell() throws IOException {
        Path csv = writeCsv(String.join("\n",
                "0,0,0",
                "0,0,0",
                "0,0,0"
        ));

        World world = new World(csv.toString());

        // agent 在 (0,0)，但查询的是 (2,0) 的左侧邻居：x=2 非左边界，应返回 (1,0)
        Cell neighbor = world.getNeighborCell(world.getCell(2, 0), Constants.DIRECTION_LEFT);
        assertEquals(1, neighbor.getX(), "应基于传入格子 (2,0) 计算左侧邻居");
        assertEquals(0, neighbor.getY());

        // (0,0) 已在左边界，左侧邻居越界应返回 null
        assertNull(world.getNeighborCell(world.getCell(0, 0), Constants.DIRECTION_LEFT));
    }

    /** 从起点向右移动到空格：agent 移动到 (1,0)，单步奖励 = 每步惩罚 -1，历史记录一条 */
    @Test
    void moveToEmptyShouldUpdatePositionAndRecordHistory() throws IOException {
        Path csv = writeCsv(String.join("\n",
                "0,0,0",
                "0,0,0",
                "0,0,0"
        ));

        World world = new World(csv.toString());

        Cell next = world.move(Constants.DIRECTION_RIGHT);

        assertEquals(1, next.getX(), "应移动到 (1,0)");
        assertEquals(0, next.getY());
        assertEquals(Constants.CELL_TYPE_EMPTY, next.getType());
        assertEquals(1, world.getCurrentCell().getX(), "agent 位置应更新到 (1,0)");
        assertEquals(0, world.getCurrentCell().getY());
        assertEquals(Constants.STEP_PUNISH, world.getTotalReward(), "进入空格应为每步惩罚 -1");
        assertEquals(1, world.getHistory().size(), "move 应记录一条历史");
    }

    /** 方向常量语义：UP 使 y+1、DOWN 使 y-1、RIGHT 使 x+1（y 自下向上） */
    @Test
    void moveDirectionsShouldFollowCoordinateConvention() throws IOException {
        Path csv = writeCsv(String.join("\n",
                "0,0,0",
                "0,0,0",
                "0,0,0"
        ));

        World world = new World(csv.toString());

        world.move(Constants.DIRECTION_UP);    // (0,0) -> (0,1)
        assertEquals(0, world.getCurrentCell().getX());
        assertEquals(1, world.getCurrentCell().getY(), "UP 应使 y+1");

        world.move(Constants.DIRECTION_RIGHT); // (0,1) -> (1,1)
        assertEquals(1, world.getCurrentCell().getX(), "RIGHT 应使 x+1");
        assertEquals(1, world.getCurrentCell().getY());

        world.move(Constants.DIRECTION_DOWN);  // (1,1) -> (1,0)
        assertEquals(1, world.getCurrentCell().getX());
        assertEquals(0, world.getCurrentCell().getY(), "DOWN 应使 y-1");

        assertEquals(3 * Constants.STEP_PUNISH, world.getTotalReward(), "全程空格，累计奖励应为 3 步惩罚");
        assertEquals(3, world.getHistory().size());
    }

    /** 移动进入墙体：agent 留在原地，单步奖励 = 每步惩罚 -1 + 撞墙惩罚 -1，墙不被消耗 */
    @Test
    void moveIntoWallShouldStayAndTakePenalty() throws IOException {
        Path csv = writeCsv(String.join("\n",
                "0,0,0",
                "0,0,0",
                "0,1,0" // y=0：墙 (1,0)
        ));

        World world = new World(csv.toString());

        Cell next = world.move(Constants.DIRECTION_RIGHT);

        assertEquals(0, next.getX(), "撞墙后应停留在原格");
        assertEquals(0, next.getY());
        assertEquals(Constants.STEP_PUNISH + Constants.REWARD_WALL, world.getTotalReward(),
                "撞墙单步奖励应为每步惩罚 + 撞墙惩罚");
        assertEquals(Constants.CELL_TYPE_WALL, world.getCell(1, 0).getType(), "墙不应被消耗");
        assertEquals(Constants.STEP_PUNISH + Constants.REWARD_WALL, world.getHistory().get(0).getReward());
    }

    /** 边界越界移动：视同撞墙，agent 留在原地并扣奖励 */
    @Test
    void moveOutOfBoundsShouldStayAndTakePenalty() throws IOException {
        Path csv = writeCsv(String.join("\n",
                "0,0,0",
                "0,0,0",
                "0,0,0"
        ));

        World world = new World(csv.toString());

        world.move(Constants.DIRECTION_LEFT); // 从 (0,0) 向左越界
        assertEquals(0, world.getCurrentCell().getX());
        assertEquals(0, world.getCurrentCell().getY(), "左越界应停在 (0,0)");

        world.move(Constants.DIRECTION_DOWN); // 从 (0,0) 向下越界
        assertEquals(0, world.getCurrentCell().getX());
        assertEquals(0, world.getCurrentCell().getY(), "下越界应停在 (0,0)");

        world.setCurrentCell(world.getCell(2, 0)); // 移到右边界
        world.move(Constants.DIRECTION_RIGHT);
        assertEquals(2, world.getCurrentCell().getX(), "右越界应停在 (2,0)");
        assertEquals(0, world.getCurrentCell().getY());

        world.setCurrentCell(world.getCell(0, 2)); // 移到上边界
        world.move(Constants.DIRECTION_UP);
        assertEquals(0, world.getCurrentCell().getX());
        assertEquals(2, world.getCurrentCell().getY(), "上越界应停在 (0,2)");

        assertEquals(4 * (Constants.STEP_PUNISH + Constants.REWARD_WALL), world.getTotalReward(),
                "四次越界应累计 4 × (每步惩罚 + 越界惩罚)");
        assertEquals(4, world.getHistory().size());
    }

    /** 移动进入苹果：单步奖励 = 每步惩罚 -1 + 苹果奖励，苹果被消耗（格子变空） */
    @Test
    void moveOntoAppleShouldRewardAndConsume() throws IOException {
        Path csv = writeCsv(String.join("\n",
                "0,0,0",
                "0,0,0",
                "0,3,0" // y=0：苹果 (1,0)
        ));

        World world = new World(csv.toString());

        Cell next = world.move(Constants.DIRECTION_RIGHT);

        assertEquals(1, next.getX());
        assertEquals(0, next.getY());
        assertEquals(Constants.STEP_PUNISH + Constants.REWARD_APPLE, world.getTotalReward(),
                "进入苹果单步奖励应为每步惩罚 + 苹果奖励");
        assertEquals(Constants.CELL_TYPE_EMPTY, next.getType(), "返回的应是消耗后的空格");
        assertEquals(Constants.CELL_TYPE_EMPTY, world.getCell(1, 0).getType(), "地图上的苹果应被消耗");
    }

    /** 移动进入炸弹：奖励 REWARD_BOMB，炸弹被消耗（格子变空） */
    @Test
    void moveOntoBombShouldPenalizeAndConsume() throws IOException {
        Path csv = writeCsv(String.join("\n",
                "0,0,0",
                "0,0,0",
                "0,2,0" // y=0：炸弹 (1,0)
        ));

        World world = new World(csv.toString());

        Cell next = world.move(Constants.DIRECTION_RIGHT);

        assertEquals(1, next.getX());
        assertEquals(0, next.getY());
        assertEquals(Constants.STEP_PUNISH + Constants.REWARD_BOMB, world.getTotalReward(),
                "进入炸弹单步奖励应为每步惩罚 + 炸弹奖励");
        assertEquals(Constants.CELL_TYPE_EMPTY, next.getType(), "返回的应是消耗后的空格");
        assertEquals(Constants.CELL_TYPE_EMPTY, world.getCell(1, 0).getType(), "地图上的炸弹应被消耗");
    }

    /** 移动进入终点：游戏结束，此后再移动应抛异常且不产生副作用 */
    @Test
    void moveOntoTerminalShouldEndGameAndBlockFurtherMoves() throws IOException {
        Path csv = writeCsv(String.join("\n",
                "0,0,0",
                "0,0,0",
                "0,4,0" // y=0：终点 (1,0)
        ));

        World world = new World(csv.toString());

        Cell next = world.move(Constants.DIRECTION_RIGHT);

        assertEquals(1, next.getX());
        assertEquals(0, next.getY());
        assertEquals(Constants.CELL_TYPE_TERMINAL, next.getType());
        assertTrue(world.isTerminated(), "进入终点后 isTerminated 应为 true");
        assertEquals(Constants.STEP_PUNISH, world.getTotalReward(), "进入终点应为每步惩罚，无终点奖励");

        assertThrows(RuntimeException.class, () -> world.move(Constants.DIRECTION_UP),
                "游戏结束后再移动应抛异常");
        assertEquals(1, world.getHistory().size(), "失败的 move 不应追加历史");
        assertEquals(Constants.STEP_PUNISH, world.getTotalReward(), "失败的 move 不应改变累计奖励");
    }

    /** 多步移动：累计奖励与历史记录应逐步累加，历史条目携带 from/action/reward/to */
    @Test
    void multipleMovesShouldAccumulateRewardAndHistory() throws IOException {
        Path csv = writeCsv(String.join("\n",
                "0,0,0",
                "0,3,0", // y=1：苹果 (1,1)
                "0,0,0"
        ));

        World world = new World(csv.toString());

        world.move(Constants.DIRECTION_UP);    // (0,0) -> (0,1)，空格：每步惩罚 -1
        world.move(Constants.DIRECTION_RIGHT); // (0,1) -> (1,1)，苹果：每步惩罚 + 苹果奖励
        world.move(Constants.DIRECTION_LEFT);  // (1,1) -> (0,1)，空格：每步惩罚 -1
        world.move(Constants.DIRECTION_DOWN);  // (0,1) -> (0,0)，空格：每步惩罚 -1

        assertEquals(4 * Constants.STEP_PUNISH + Constants.REWARD_APPLE, world.getTotalReward(),
                "累计奖励应为 4 步惩罚 + 苹果奖励");
        assertEquals(4, world.getHistory().size());

        ActionRecord appleStep = world.getHistory().get(1);
        assertEquals(0, appleStep.getCell().getX(), "历史应记录移动起点 (0,1)");
        assertEquals(1, appleStep.getCell().getY());
        assertEquals(Constants.DIRECTION_RIGHT, appleStep.getAction());
        assertEquals(Constants.STEP_PUNISH + Constants.REWARD_APPLE, appleStep.getReward());
        assertEquals(1, appleStep.getNextCell().getX(), "历史应记录移动终点 (1,1)");
        assertEquals(1, appleStep.getNextCell().getY());
        assertEquals(Constants.CELL_TYPE_EMPTY, appleStep.getNextCell().getType(), "苹果应已被消耗");
    }

    /** 非法方向参数应抛出 IllegalArgumentException */
    @Test
    void moveWithInvalidDirectionShouldThrow() throws IOException {
        Path csv = writeCsv("0,0\n0,0\n");

        World world = new World(csv.toString());

        assertThrows(IllegalArgumentException.class, () -> world.move(99));
    }

    /** reset 应还原被消耗的苹果/炸弹，保证多局采样的环境一致 */
    @Test
    void resetShouldRestoreConsumedCells() throws IOException {
        Path csv = writeCsv(String.join("\n",
                "0,0,0",
                "0,0,0",
                "0,3,0" // y=0：苹果 (1,0)
        ));

        World world = new World(csv.toString());
        world.move(Constants.DIRECTION_RIGHT); // 消耗苹果 (1,0)
        assertEquals(Constants.CELL_TYPE_EMPTY, world.getCell(1, 0).getType());

        world.reset();

        assertEquals(Constants.CELL_TYPE_APPLE, world.getCell(1, 0).getType(), "reset 后苹果应恢复");
        assertEquals(0, world.getCurrentCell().getX(), "reset 后 agent 应回到起点");
        assertEquals(0, world.getCurrentCell().getY());
        assertEquals(0, world.getTotalReward(), "reset 后累计奖励应清零");
        assertEquals(0, world.getHistory().size(), "reset 后历史应清空");
    }

    /** 撞墙原地不动时，不应误消耗 agent 脚下的道具 */
    @Test
    void moveIntoWallShouldNotConsumeItemUnderAgent() throws IOException {
        Path csv = writeCsv(String.join("\n",
                "0,0,0",
                "0,0,0",
                "3,1,0" // y=0：苹果 (0,0)、墙 (1,0)
        ));

        World world = new World(csv.toString());
        world.setCurrentCell(world.getCell(0, 0)); // agent 站在苹果上

        world.move(Constants.DIRECTION_RIGHT); // 撞墙

        assertEquals(Constants.CELL_TYPE_APPLE, world.getCell(0, 0).getType(), "脚下苹果不应被消耗");
        assertEquals(0, world.getCurrentCell().getX());
        assertEquals(0, world.getCurrentCell().getY());
    }
}
