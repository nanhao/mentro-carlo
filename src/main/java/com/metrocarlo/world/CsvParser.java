package com.metrocarlo.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 地图文件读取与解析工具：把地图 CSV 解析为矩形 int[][] 网格。
 * <p>
 * 解析规则：
 * <ul>
 *   <li>每行 CSV 对应网格中的一排，单元格为整数（取值见
 *       {@link com.metrocarlo.common.Constants} 的 CELL_TYPE_* 常量组）</li>
 *   <li>容忍单元格两侧空格、夹杂空行与 UTF-8 BOM（Windows 记事本保存格式常见）</li>
 *   <li>文件为空、各行列数不一致或单元格不是整数时抛出 {@link IllegalArgumentException}；
 *       文件不存在或读取失败时抛出 {@link UncheckedIOException}</li>
 * </ul>
 * 行序与游戏坐标轴的映射（首行是否为最上排）由调用方 {@link World} 决定，本类不翻转行序。
 */
public final class CsvParser {

    /** 纯静态工具类，禁止实例化。 */
    private CsvParser() {
    }

    /**
     * 读取并解析 CSV 地图文件。
     *
     * @param csvPath 地图 CSV 文件路径
     * @return 矩形网格：grid[行][列]，行序与 CSV 文件行序一致
     * @throws IllegalArgumentException 文件为空、各行长度不一致或存在非法整数
     * @throws UncheckedIOException     文件不存在或读取失败
     */
    public static int[][] parse(String csvPath) {
        List<int[]> rows = readRows(csvPath);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV 地图文件中没有任何有效数据行: " + csvPath);
        }
        int width = rows.get(0).length;
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).length != width) {
                throw new IllegalArgumentException("CSV 第 " + (i + 1) + " 行有 " + rows.get(i).length
                        + " 列，与首行的 " + width + " 列不一致（地图必须为矩形）: " + csvPath);
            }
        }
        return rows.toArray(new int[0][]);
    }

    /** 逐行读取 CSV 并解析为 int 列表；跳过空行，容忍 UTF-8 BOM 与单元格两侧空格。 */
    private static List<int[]> readRows(String csvPath) {
        List<int[]> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Path.of(csvPath), StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String trimmed = stripBom(line.strip());
                if (trimmed.isEmpty()) {
                    continue; // 跳过空行
                }
                rows.add(parseCsvLine(trimmed, lineNo, csvPath));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取 CSV 地图文件失败: " + csvPath, e);
        }
        return rows;
    }

    /** 去掉文本开头的 UTF-8 BOM（\uFEFF，Windows 记事本保存的文件常见），其余内容原样返回。 */
    private static String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    /** 把一行 "0,3,1" 形式的文本解析为 int[]，遇到非法整数时抛出带行列号的异常。 */
    private static int[] parseCsvLine(String line, int lineNo, String csvPath) {
        String[] tokens = line.split(",");
        int[] values = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                values[i] = Integer.parseInt(tokens[i].strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("CSV 第 " + lineNo + " 行第 " + (i + 1)
                        + " 列不是有效整数: \"" + tokens[i].strip() + "\" (" + csvPath + ")");
            }
        }
        return values;
    }
}
