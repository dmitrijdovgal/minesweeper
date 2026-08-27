// minesweeper.go
package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"strconv"
	"strings"
	"time"
)

const (
	reset   = "\033[0m"
	blue    = "\033[34m"
	green   = "\033[32m"
	red     = "\033[31m"
	cyan    = "\033[36m"
	black   = "\033[30m"
	gray    = "\033[37m"
	yellow  = "\033[33m"
	white   = "\033[97m"
	bgDark  = "\033[100m"
	bgLight = "\033[47m"
)

var digitColors = map[rune]string{
	'1': blue,
	'2': green,
	'3': red,
	'4': blue,
	'5': red,
	'6': cyan,
	'7': black,
	'8': gray,
}

type Minesweeper struct {
	rows, cols, mines int
	board             [][]rune
	revealed          [][]bool
	flagged           [][]bool
	gameOver          bool
	won               bool
	firstMove         bool
	minePositions     map[string]bool
	startTime         time.Time
	elapsed           float64
	minesLeft         int
}

func NewMinesweeper(rows, cols, mines int) *Minesweeper {
	b := make([][]rune, rows)
	r := make([][]bool, rows)
	f := make([][]bool, rows)
	for i := 0; i < rows; i++ {
		b[i] = make([]rune, cols)
		r[i] = make([]bool, cols)
		f[i] = make([]bool, cols)
		for j := 0; j < cols; j++ {
			b[i][j] = ' '
		}
	}
	return &Minesweeper{
		rows:          rows,
		cols:          cols,
		mines:         mines,
		board:         b,
		revealed:      r,
		flagged:       f,
		firstMove:     true,
		minePositions: make(map[string]bool),
		minesLeft:     mines,
	}
}

func (m *Minesweeper) placeMines(firstRow, firstCol int) {
	safe := make(map[string]bool)
	for dr := -1; dr <= 1; dr++ {
		for dc := -1; dc <= 1; dc++ {
			nr, nc := firstRow+dr, firstCol+dc
			if nr >= 0 && nr < m.rows && nc >= 0 && nc < m.cols {
				safe[fmt.Sprintf("%d,%d", nr, nc)] = true
			}
		}
	}
	var candidates []string
	for r := 0; r < m.rows; r++ {
		for c := 0; c < m.cols; c++ {
			key := fmt.Sprintf("%d,%d", r, c)
			if !safe[key] {
				candidates = append(candidates, key)
			}
		}
	}
	// Перемешиваем
	rand.Shuffle(len(candidates), func(i, j int) { candidates[i], candidates[j] = candidates[j], candidates[i] })
	selected := candidates[:m.mines]
	for _, key := range selected {
		m.minePositions[key] = true
		var r, c int
		fmt.Sscanf(key, "%d,%d", &r, &c)
		m.board[r][c] = '*'
	}
}

func (m *Minesweeper) countNeighbors(r, c int) int {
	if m.board[r][c] == '*' {
		return -1
	}
	count := 0
	for dr := -1; dr <= 1; dr++ {
		for dc := -1; dc <= 1; dc++ {
			nr, nc := r+dr, c+dc
			if nr >= 0 && nr < m.rows && nc >= 0 && nc < m.cols && m.board[nr][nc] == '*' {
				count++
			}
		}
	}
	return count
}

func (m *Minesweeper) reveal(r, c int) {
	if m.gameOver || m.won {
		return
	}
	if r < 0 || r >= m.rows || c < 0 || c >= m.cols {
		return
	}
	if m.flagged[r][c] {
		return
	}
	if m.revealed[r][c] {
		return
	}
	if m.firstMove {
		m.firstMove = false
		m.placeMines(r, c)
		m.startTime = time.Now()
		for rr := 0; rr < m.rows; rr++ {
			for cc := 0; cc < m.cols; cc++ {
				if m.board[rr][cc] != '*' {
					m.board[rr][cc] = rune('0' + m.countNeighbors(rr, cc))
				}
			}
		}
	}
	if m.board[r][c] == '*' {
		m.gameOver = true
		m.revealAll()
		m.elapsed = time.Since(m.startTime).Seconds()
		return
	}
	m.revealed[r][c] = true
	if m.board[r][c] == '0' {
		for dr := -1; dr <= 1; dr++ {
			for dc := -1; dc <= 1; dc++ {
				nr, nc := r+dr, c+dc
				if nr >= 0 && nr < m.rows && nc >= 0 && nc < m.cols && !m.revealed[nr][nc] && !m.flagged[nr][nc] {
					m.reveal(nr, nc)
				}
			}
		}
	}
	m.checkWin()
}

func (m *Minesweeper) toggleFlag(r, c int) {
	if m.gameOver || m.won {
		return
	}
	if r < 0 || r >= m.rows || c < 0 || c >= m.cols {
		return
	}
	if m.revealed[r][c] {
		return
	}
	if m.flagged[r][c] {
		m.flagged[r][c] = false
		m.minesLeft++
	} else {
		m.flagged[r][c] = true
		m.minesLeft--
	}
}

func (m *Minesweeper) checkWin() {
	revealedCount := 0
	for r := 0; r < m.rows; r++ {
		for c := 0; c < m.cols; c++ {
			if m.revealed[r][c] {
				revealedCount++
			}
		}
	}
	if revealedCount == m.rows*m.cols-m.mines {
		m.won = true
		m.elapsed = time.Since(m.startTime).Seconds()
		m.gameOver = true
	}
}

func (m *Minesweeper) revealAll() {
	for r := 0; r < m.rows; r++ {
		for c := 0; c < m.cols; c++ {
			m.revealed[r][c] = true
		}
	}
}

func (m *Minesweeper) getDisplayChar(r, c int) string {
	if m.gameOver && !m.won {
		if m.board[r][c] == '*' {
			return "💣"
		}
		if m.revealed[r][c] {
			return string(m.board[r][c])
		}
		return " "
	}
	if m.revealed[r][c] {
		return string(m.board[r][c])
	}
	if m.flagged[r][c] {
		return "⚑"
	}
	return " "
}

func (m *Minesweeper) getColor(r, c int) string {
	if m.gameOver && !m.won {
		if m.board[r][c] == '*' {
			return red
		}
	}
	if !m.revealed[r][c] {
		if m.flagged[r][c] {
			return yellow
		}
		return white
	}
	ch := m.board[r][c]
	if ch >= '1' && ch <= '8' {
		if col, ok := digitColors[ch]; ok {
			return col
		}
	}
	return white
}

func (m *Minesweeper) render() {
	fmt.Print("  ")
	for c := 0; c < m.cols; c++ {
		fmt.Printf("%c ", 'a'+c)
	}
	fmt.Println()
	for r := 0; r < m.rows; r++ {
		fmt.Printf("%2d ", r+1)
		for c := 0; c < m.cols; c++ {
			ch := m.getDisplayChar(r, c)
			color := m.getColor(r, c)
			bg := bgDark
			if m.revealed[r][c] || (m.gameOver && !m.won) {
				bg = bgLight
			}
			if m.flagged[r][c] && !m.revealed[r][c] {
				bg = bgDark
			}
			fmt.Printf("%s%s%s%s ", bg, color, ch, reset)
		}
		fmt.Println()
	}
}

func (m *Minesweeper) play() {
	scanner := bufio.NewScanner(os.Stdin)
	fmt.Println("Добро пожаловать в Сапер!")
	fmt.Println("Вводите ход в формате: a1 (открыть) или f a1 (флаг)")
	fmt.Println("q - выход")
	for !m.gameOver {
		m.render()
		fmt.Printf("Мин осталось: %d\n", m.minesLeft)
		fmt.Print("Введите ход: ")
		if !scanner.Scan() {
			break
		}
		cmd := scanner.Text()
		if cmd == "q" {
			return
		}
		parts := strings.Fields(cmd)
		if len(parts) == 2 && parts[0] == "f" {
			coord := parts[1]
			if len(coord) < 2 {
				continue
			}
			col := int(coord[0] - 'a')
			row, _ := strconv.Atoi(coord[1:])
			m.toggleFlag(row-1, col)
		} else if len(parts) == 1 {
			coord := parts[0]
			if len(coord) < 2 {
				continue
			}
			col := int(coord[0] - 'a')
			row, _ := strconv.Atoi(coord[1:])
			m.reveal(row-1, col)
		}
	}
	m.render()
	if m.won {
		fmt.Printf("Поздравляем! Вы выиграли за %.1f секунд!\n", m.elapsed)
		// Сохраняем рекорд
		records := make(map[string]float64)
		if data, err := os.ReadFile("records.json"); err == nil {
			json.Unmarshal(data, &records)
		}
		key := fmt.Sprintf("%dx%d_%d", m.rows, m.cols, m.mines)
		if val, ok := records[key]; !ok || m.elapsed < val {
			records[key] = m.elapsed
			if data, err := json.MarshalIndent(records, "", "  "); err == nil {
				os.WriteFile("records.json", data, 0644)
			}
			fmt.Printf("Новый рекорд! %.1f сек.\n", m.elapsed)
		} else {
			fmt.Printf("Рекорд для этого уровня: %.1f сек.\n", val)
		}
	} else {
		fmt.Println("Вы проиграли. Попробуйте снова!")
	}
}

func chooseDifficulty() (int, int, int) {
	scanner := bufio.NewScanner(os.Stdin)
	fmt.Println("Выберите уровень сложности:")
	fmt.Println("1. Лёгкий (9x9, 10 мин)")
	fmt.Println("2. Средний (16x16, 40 мин)")
	fmt.Println("3. Сложный (30x16, 99 мин)")
	fmt.Print("Ваш выбор: ")
	scanner.Scan()
	choice := scanner.Text()
	switch choice {
	case "1":
		return 9, 9, 10
	case "2":
		return 16, 16, 40
	case "3":
		return 30, 16, 99
	default:
		fmt.Println("Некорректный выбор, установлен лёгкий.")
		return 9, 9, 10
	}
}

func main() {
	rand.Seed(time.Now().UnixNano())
	rows, cols, mines := chooseDifficulty()
	game := NewMinesweeper(rows, cols, mines)
	game.play()
}
