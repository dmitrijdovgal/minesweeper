// Minesweeper.kt
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.Instant
import java.time.Duration
import kotlin.random.Random

const val RESET = "\u001B[0m"
const val BLUE = "\u001B[34m"
const val GREEN = "\u001B[32m"
const val RED = "\u001B[31m"
const val CYAN = "\u001B[36m"
const val BLACK = "\u001B[30m"
const val GRAY = "\u001B[37m"
const val YELLOW = "\u001B[33m"
const val WHITE = "\u001B[97m"
const val BG_DARK = "\u001B[100m"
const val BG_LIGHT = "\u001B[47m"

val digitColors = mapOf(
    '1' to BLUE, '2' to GREEN, '3' to RED, '4' to BLUE,
    '5' to RED, '6' to CYAN, '7' to BLACK, '8' to GRAY
)

class Minesweeper(private val rows: Int, private val cols: Int, private val mines: Int) {
    private val board = Array(rows) { CharArray(cols) { ' ' } }
    private val revealed = Array(rows) { BooleanArray(cols) { false } }
    private val flagged = Array(rows) { BooleanArray(cols) { false } }
    private var gameOver = false
    private var won = false
    private var firstMove = true
    private val minePositions = mutableSetOf<Pair<Int, Int>>()
    private var startTime: Instant? = null
    private var elapsed = 0.0
    private var minesLeft = mines

    private fun placeMines(firstRow: Int, firstCol: Int) {
        val safe = mutableSetOf<Pair<Int, Int>>()
        for (dr in -1..1)
            for (dc in -1..1) {
                val nr = firstRow + dr
                val nc = firstCol + dc
                if (nr in 0 until rows && nc in 0 until cols) safe.add(nr to nc)
            }
        val candidates = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows)
            for (c in 0 until cols)
                if (r to c !in safe) candidates.add(r to c)
        candidates.shuffle(Random.Default)
        minePositions.addAll(candidates.take(mines))
        minePositions.forEach { (r, c) -> board[r][c] = '*' }
    }

    private fun countNeighbors(r: Int, c: Int): Int {
        if (board[r][c] == '*') return -1
        var cnt = 0
        for (dr in -1..1)
            for (dc in -1..1) {
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until rows && nc in 0 until cols && board[nr][nc] == '*') cnt++
            }
        return cnt
    }

    fun reveal(r: Int, c: Int) {
        if (gameOver || won) return
        if (r !in 0 until rows || c !in 0 until cols) return
        if (flagged[r][c]) return
        if (revealed[r][c]) return
        if (firstMove) {
            firstMove = false
            placeMines(r, c)
            startTime = Instant.now()
            for (rr in 0 until rows)
                for (cc in 0 until cols)
                    if (board[rr][cc] != '*')
                        board[rr][cc] = countNeighbors(rr, cc).let { if (it == 0) ' ' else ('0' + it) }
        }
        if (board[r][c] == '*') {
            gameOver = true
            revealAll()
            elapsed = Duration.between(startTime, Instant.now()).toMillis() / 1000.0
            return
        }
        revealed[r][c] = true
        if (board[r][c] == ' ') {
            for (dr in -1..1)
                for (dc in -1..1) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0 until rows && nc in 0 until cols && !revealed[nr][nc] && !flagged[nr][nc])
                        reveal(nr, nc)
                }
        }
        checkWin()
    }

    fun toggleFlag(r: Int, c: Int) {
        if (gameOver || won) return
        if (r !in 0 until rows || c !in 0 until cols) return
        if (revealed[r][c]) return
        if (flagged[r][c]) {
            flagged[r][c] = false
            minesLeft++
        } else {
            flagged[r][c] = true
            minesLeft--
        }
    }

    private fun checkWin() {
        val revealedCount = (0 until rows).sumOf { r -> (0 until cols).count { c -> revealed[r][c] } }
        if (revealedCount == rows * cols - mines) {
            won = true
            elapsed = Duration.between(startTime, Instant.now()).toMillis() / 1000.0
            gameOver = true
        }
    }

    private fun revealAll() {
        for (r in 0 until rows)
            for (c in 0 until cols)
                revealed[r][c] = true
    }

    private fun getDisplayChar(r: Int, c: Int): String {
        if (gameOver && !won) {
            if (board[r][c] == '*') return "💣"
            if (revealed[r][c]) return board[r][c].toString()
            return " "
        }
        if (revealed[r][c]) return board[r][c].toString()
        if (flagged[r][c]) return "⚑"
        return " "
    }

    private fun getColor(r: Int, c: Int): String {
        if (gameOver && !won) {
            if (board[r][c] == '*') return RED
        }
        if (!revealed[r][c]) {
            if (flagged[r][c]) return YELLOW
            return WHITE
        }
        val ch = board[r][c]
        if (ch in '1'..'8') return digitColors[ch] ?: WHITE
        return WHITE
    }

    private fun render() {
        print("  ")
        for (c in 0 until cols) print("${'a' + c} ")
        println()
        for (r in 0 until rows) {
            print("%2d ".format(r + 1))
            for (c in 0 until cols) {
                val ch = getDisplayChar(r, c)
                val color = getColor(r, c)
                val bg = if (revealed[r][c] || (gameOver && !won)) BG_LIGHT else BG_DARK
                print("$bg$color$ch$RESET ")
            }
            println()
        }
    }

    fun play() {
        val scanner = java.util.Scanner(System.`in`)
        println("Добро пожаловать в Сапер!")
        println("Вводите ход в формате: a1 (открыть) или f a1 (флаг)")
        println("q - выход")
        while (!gameOver) {
            render()
            println("Мин осталось: $minesLeft")
            print("Введите ход: ")
            val line = scanner.nextLine().trim()
            if (line == "q") break
            val parts = line.split("\\s+".toRegex())
            if (parts.size == 2 && parts[0] == "f") {
                val coord = parts[1]
                if (coord.length < 2) continue
                val col = coord[0] - 'a'
                val row = coord.substring(1).toInt() - 1
                toggleFlag(row, col)
            } else if (parts.size == 1) {
                val coord = parts[0]
                if (coord.length < 2) continue
                val col = coord[0] - 'a'
                val row = coord.substring(1).toInt() - 1
                reveal(row, col)
            }
        }
        render()
        if (won) {
            println("Поздравляем! Вы выиграли за ${"%.1f".format(elapsed)} секунд!")
            // Рекорды
            val gson = GsonBuilder().setPrettyPrinting().create()
            val type = object : TypeToken<MutableMap<String, Double>>() {}.type
            val records = try {
                gson.fromJson(File("records.json").readText(), type) as MutableMap<String, Double>
            } catch (e: Exception) { mutableMapOf() }
            val key = "${rows}x${cols}_${mines}"
            if (!records.containsKey(key) || elapsed < records[key]!!) {
                records[key] = elapsed
                File("records.json").writeText(gson.toJson(records))
                println("Новый рекорд! ${"%.1f".format(elapsed)} сек.")
            } else {
                println("Рекорд для этого уровня: ${"%.1f".format(records[key])} сек.")
            }
        } else {
            println("Вы проиграли. Попробуйте снова!")
        }
        scanner.close()
    }
}

fun main() {
    println("Выберите уровень сложности:")
    println("1. Лёгкий (9x9, 10 мин)")
    println("2. Средний (16x16, 40 мин)")
    println("3. Сложный (30x16, 99 мин)")
    print("Ваш выбор: ")
    val choice = readLine()?.trim() ?: ""
    val (rows, cols, mines) = when (choice) {
        "1" -> Triple(9, 9, 10)
        "2" -> Triple(16, 16, 40)
        "3" -> Triple(30, 16, 99)
        else -> {
            println("Некорректный выбор, установлен лёгкий.")
            Triple(9, 9, 10)
        }
    }
    val game = Minesweeper(rows, cols, mines)
    game.play()
}
