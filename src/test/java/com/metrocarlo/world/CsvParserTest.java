package com.metrocarlo.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CsvParser#parse(String)} 的单元测试：CSV 读取与解析行为。
 * <p>
 * 注意：本类只验证"CSV 文本 → 矩形网格"的解析规则；
 * 行序与游戏坐标轴的映射（首行 = 最上排）属于 World 的行为，由 {@link WorldTest} 覆盖。
 */
class CsvParserTest {

    /** JUnit 临时目录：测试输出统一写入临时目录，结束后自动删除，避免触碰真实数据文件 */
    @TempDir
    Path tempDir;

    /** 在临时目录中写入一份 CSV 地图文件并返回其路径 */
    private Path writeCsv(String content) throws IOException {
        Path csv = tempDir.resolve("map.csv");
        Files.writeString(csv, content, StandardCharsets.UTF_8);
        return csv;
    }

    /** 正常解析：返回按 CSV 行序组织的矩形数组，单元格解析为对应整数 */
    @Test
    void parseShouldReturnRowsAsRectangularArray() throws IOException {
        Path csv = writeCsv("0,3,0,0,4\n0,0,1,0,2\n");

        int[][] grid = CsvParser.parse(csv.toString());

        assertEquals(2, grid.length, "行数应等于 CSV 有效行数");
        assertEquals(5, grid[0].length, "列数应等于 CSV 列数");
        assertArrayEquals(new int[]{0, 3, 0, 0, 4}, grid[0]);
        assertArrayEquals(new int[]{0, 0, 1, 0, 2}, grid[1]);
    }

    /** 单元格两侧空格与夹杂空行应被容忍 */
    @Test
    void parseShouldTolerateSpacesAndBlankLines() throws IOException {
        Path csv = writeCsv("\n0, 3, 0 \n\n0,0,0\n");

        int[][] grid = CsvParser.parse(csv.toString());

        assertEquals(2, grid.length, "空行应被跳过");
        assertEquals(3, grid[0].length);
        assertEquals(3, grid[0][1], "单元格两侧空格应被去除");
    }

    /** UTF-8 BOM 开头的文件（Windows 记事本常见格式）应能正确解析 */
    @Test
    void parseShouldHandleUtf8Bom() throws IOException {
        Path csv = writeCsv("\uFEFF0,0,4\n0,0,0\n");

        int[][] grid = CsvParser.parse(csv.toString());

        assertEquals(2, grid.length);
        assertEquals(4, grid[0][2]);
    }

    /** 文件不存在时应抛出异常，消息包含路径信息 */
    @Test
    void parseShouldThrowWhenFileMissing() {
        Path missing = tempDir.resolve("no-such-map.csv");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> CsvParser.parse(missing.toString()));

        assertTrue(ex.getMessage().contains("no-such-map.csv"), "异常信息应包含出错文件路径");
    }

    /** 各行列数不一致（锯齿 CSV）应抛出异常 */
    @Test
    void parseShouldThrowWhenRowsNotRectangular() throws IOException {
        Path csv = writeCsv("0,0,0\n0,1\n"); // 第二行少一列

        assertThrows(IllegalArgumentException.class, () -> CsvParser.parse(csv.toString()));
    }

    /** 空文件（无任何有效行）应抛出异常 */
    @Test
    void parseShouldThrowWhenFileBlank() throws IOException {
        Path csv = writeCsv("   \n  \n");

        assertThrows(IllegalArgumentException.class, () -> CsvParser.parse(csv.toString()));
    }

    /** 含非整数单元格时应抛出异常，并指出出错单元格的行列位置 */
    @Test
    void parseShouldThrowWhenCellNotInteger() throws IOException {
        Path csv = writeCsv("0,abc\n");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CsvParser.parse(csv.toString()));

        assertTrue(ex.getMessage().contains("第 1 行第 2 列"), "异常信息应指出出错单元格位置");
    }
}
