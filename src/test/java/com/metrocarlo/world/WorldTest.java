package com.metrocarlo.world;

import com.metrocarlo.common.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link World} 的单元测试。
 * <p>
 * 坐标系约定与 README 一致：(0,0) 为左下角，y 自下向上；
 * 因此 CSV 的第一行对应地图最上方一行（y = height - 1），最后一行对应 y = 0。
 * CSV 读取与解析的细节行为（容错、校验）由 {@link CsvParserTest} 覆盖。
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
        assertEquals(Constants.CELL_TYPE_TERMINAL, world.getCell(4, 3), "首行末列应为终点");
        assertEquals(Constants.CELL_TYPE_APPLE, world.getCell(1, 3), "首行第 2 列应为苹果");
        assertEquals(Constants.CELL_TYPE_APPLE, world.getCell(3, 2));
        assertEquals(Constants.CELL_TYPE_WALL, world.getCell(2, 1));
        assertEquals(Constants.CELL_TYPE_BOMB, world.getCell(4, 1));
        assertEquals(Constants.CELL_TYPE_EMPTY, world.getCell(0, 0), "起点格应为空");
    }
}
