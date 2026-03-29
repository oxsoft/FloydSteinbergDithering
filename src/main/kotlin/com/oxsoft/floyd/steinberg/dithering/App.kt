package com.oxsoft.floyd.steinberg.dithering

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.pow
import kotlin.system.exitProcess

object App {
    @JvmStatic
    fun main(vararg args: String) {
        if (args.isEmpty()) {
            println("Please specify input image file path.")
            exitProcess(1)
        }
        val inputFile = File(args[0])
        if (!inputFile.exists()) {
            println("${args[0]} is not found.")
            exitProcess(1)
        }
        val outputDirectory = args[0].substringBeforeLast(".")
        File(outputDirectory).mkdir()
        val colorList = javaClass.getResource("/color-list.tsv")?.readText() ?: return
        val template = javaClass.getResource("/template.html")?.readText() ?: return
        val palette = colorList.split("\n").mapNotNull { row ->
            val cols = row.split("\t")
            if (cols.size < 5) return@mapNotNull null
            val r = cols[2].toIntOrNull() ?: return@mapNotNull null
            val g = cols[3].toIntOrNull() ?: return@mapNotNull null
            val b = cols[4].toIntOrNull() ?: return@mapNotNull null
            Beads(cols[0], cols[1], Color(r, g, b))
        }

        val image = ImageIO.read(File(args[0]))
        val width = image.width
        val height = image.height

        // 1. 精度を保つためにRGB値をDoubleの多次元配列にコピー
        val r = Array(height) { DoubleArray(width) }
        val g = Array(height) { DoubleArray(width) }
        val b = Array(height) { DoubleArray(width) }
        val beads = Array(height) { Array(width) { Beads("", "", Color.black) } }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = Color(image.getRGB(x, y))
                r[y][x] = color.red.toDouble()
                g[y][x] = color.green.toDouble()
                b[y][x] = color.blue.toDouble()
            }
        }

        // 2. 誤差拡散処理
        for (y in 0 until height) {
            for (x in 0 until width) {
                val oldR = r[y][x].coerceIn(0.0, 255.0)
                val oldG = g[y][x].coerceIn(0.0, 255.0)
                val oldB = b[y][x].coerceIn(0.0, 255.0)

                // 最も近いパレット色を選択
                beads[y][x] = findClosestColor(Color(oldR.toInt(), oldG.toInt(), oldB.toInt()), palette)
                val newR = beads[y][x].color.red.toDouble()
                val newG = beads[y][x].color.green.toDouble()
                val newB = beads[y][x].color.blue.toDouble()

                // 誤差を計算
                val errR = oldR - newR
                val errG = oldG - newG
                val errB = oldB - newB

                // 周囲に誤差を配分
                distributeError(r, x, y, errR, width, height)
                distributeError(g, x, y, errG, width, height)
                distributeError(b, x, y, errB, width, height)
            }
        }

        // 3. BufferedImageに戻して保存
        val outputImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                outputImage.setRGB(x, y, beads[y][x].color.rgb)
            }
        }

        ImageIO.write(outputImage, "png", File("$outputDirectory/0_output.png"))
        println("誤差拡散処理が完了しました。")

        val table = buildString {
            for (y in 0 until height) {
                append("<tr>")
                for (x in 0 until width) {
                    val color = beads[y][x].color
                    append("<td style=\"background-color:")
                    append(String.format("#%02x%02x%02x", color.red, color.green, color.blue))
                    append("\"></td>")
                }
                append("</tr>")
            }
        }
        val colors = buildString {
            append("[")
            for (y in 0 until height) {
                append("[")
                for (x in 0 until width) {
                    append("\"")
                    append(beads[y][x].name)
                    append("\",")
                }
                append("],")
            }
            append("]")
        }
        File("$outputDirectory/0_output.html").writeText(
            template
                .replace("WWWWW", (width * 20).toString())
                .replace("TTTTT", table)
                .replace("CCCCC", colors)
        )

        val types = beads.flatMap { it.toSet() }.toSet()
        types.forEach { type ->
            val outputImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val color = when {
                        type == beads[y][x] -> Color.black
                        (x / 5 + y / 5) % 2 == 0 -> {
                            when {
                                (x / 29 + y / 29) % 2 == 0 -> Color.white
                                else -> Color(255, 255, 192)
                            }
                        }

                        else -> {
                            when {
                                (x / 29 + y / 29) % 2 == 0 -> Color.lightGray
                                else -> Color(192, 255, 255)
                            }
                        }
                    }
                    outputImage.setRGB(x, y, color.rgb)
                }
            }
            ImageIO.write(outputImage, "png", File("$outputDirectory/${type.id}_${type.name}.png"))
            println(type.name)
        }
    }

    fun distributeError(data: Array<DoubleArray>, x: Int, y: Int, error: Double, w: Int, h: Int) {
        fun add(dx: Int, dy: Int, weight: Double) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until w && ny in 0 until h) {
                data[ny][nx] += error * weight
            }
        }
        add(1, 0, 7.0 / 16.0)
        add(-1, 1, 3.0 / 16.0)
        add(0, 1, 5.0 / 16.0)
        add(1, 1, 1.0 / 16.0)
    }

    fun findClosestColor(target: Color, palette: List<Beads>): Beads {
        return palette.minByOrNull { beads ->
            val color = beads.color
            (target.red - color.red).toDouble().pow(2) +
                    (target.green - color.green).toDouble().pow(2) +
                    (target.blue - color.blue).toDouble().pow(2)
        } ?: palette[0]
    }
}
