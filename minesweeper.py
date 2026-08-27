#!/usr/bin/env python3
# minesweeper.py
import random
import sys
import time
import os
import json

# ANSI цветовые коды
COLORS = {
    'reset': '\033[0m',
    'blue': '\033[34m',
    'green': '\033[32m',
    'red': '\033[31m',
    'dark_blue': '\033[34m',  # отображается как синий
    'dark_red': '\033[31m',   # как красный
    'cyan': '\033[36m',
    'black': '\033[30m',
    'gray': '\033[37m',
    'yellow': '\033[33m',
    'white': '\033[97m',
    'bg_dark': '\033[100m',
    'bg_light': '\033[47m',
    'bg_default': '\033[49m',
}

# Цвета для цифр (1-8)
DIGIT_COLORS = {
    1: COLORS['blue'],
    2: COLORS['green'],
    3: COLORS['red'],
    4: COLORS['dark_blue'],
    5: COLORS['dark_red'],
    6: COLORS['cyan'],
    7: COLORS['black'],
    8: COLORS['gray'],
}

class Minesweeper:
    def __init__(self, rows, cols, mines):
        self.rows = rows
        self.cols = cols
        self.mines = mines
        self.board = [[' ' for _ in range(cols)] for _ in range(rows)]  # ' ' - скрыта, '0'..'8' - открыта, '*' - мина, 'F' - флаг
        self.revealed = [[False for _ in range(cols)] for _ in range(rows)]
        self.flagged = [[False for _ in range(cols)] for _ in range(rows)]
        self.game_over = False
        self.won = False
        self.first_move = True
        self.mine_positions = set()
        self.start_time = 0
        self.elapsed = 0
        self.mines_left = mines

    def place_mines(self, first_row, first_col):
        # Размещаем мины, избегая первой клетки и её соседей
        safe = set()
        for dr in (-1, 0, 1):
            for dc in (-1, 0, 1):
                nr, nc = first_row + dr, first_col + dc
                if 0 <= nr < self.rows and 0 <= nc < self.cols:
                    safe.add((nr, nc))
        candidates = [(r, c) for r in range(self.rows) for c in range(self.cols) if (r, c) not in safe]
        self.mine_positions = set(random.sample(candidates, self.mines))
        # Заполняем доску значениями (для подсчёта соседей)
        for r, c in self.mine_positions:
            self.board[r][c] = '*'

    def count_neighbors(self, r, c):
        if self.board[r][c] == '*':
            return -1
        count = 0
        for dr in (-1, 0, 1):
            for dc in (-1, 0, 1):
                nr, nc = r + dr, c + dc
                if 0 <= nr < self.rows and 0 <= nc < self.cols and self.board[nr][nc] == '*':
                    count += 1
        return count

    def reveal(self, r, c):
        if self.game_over or self.won:
            return
        if not (0 <= r < self.rows and 0 <= c < self.cols):
            return
        if self.flagged[r][c]:
            return
        if self.revealed[r][c]:
            return
        if self.first_move:
            self.first_move = False
            self.place_mines(r, c)
            self.start_time = time.time()
            # Пересчитываем цифры после размещения мин
            for rr in range(self.rows):
                for cc in range(self.cols):
                    if self.board[rr][cc] != '*':
                        self.board[rr][cc] = str(self.count_neighbors(rr, cc))
        if self.board[r][c] == '*':
            # Проигрыш
            self.game_over = True
            self.reveal_all()
            self.elapsed = time.time() - self.start_time
            return
        # Открываем клетку
        self.revealed[r][c] = True
        if self.board[r][c] == '0':
            # Автоматически открываем соседей
            for dr in (-1, 0, 1):
                for dc in (-1, 0, 1):
                    nr, nc = r + dr, c + dc
                    if 0 <= nr < self.rows and 0 <= nc < self.cols and not self.revealed[nr][nc] and not self.flagged[nr][nc]:
                        self.reveal(nr, nc)
        # Проверка победы
        self.check_win()

    def toggle_flag(self, r, c):
        if self.game_over or self.won:
            return
        if not (0 <= r < self.rows and 0 <= c < self.cols):
            return
        if self.revealed[r][c]:
            return
        if self.flagged[r][c]:
            self.flagged[r][c] = False
            self.mines_left += 1
        else:
            self.flagged[r][c] = True
            self.mines_left -= 1

    def check_win(self):
        # Победа если все не-минные клетки открыты
        total_cells = self.rows * self.cols
        revealed_count = sum(sum(row) for row in self.revealed)
        if revealed_count == total_cells - self.mines:
            self.won = True
            self.elapsed = time.time() - self.start_time
            self.game_over = True  # чтобы остановить игру

    def reveal_all(self):
        for r in range(self.rows):
            for c in range(self.cols):
                self.revealed[r][c] = True

    def get_display_char(self, r, c):
        if self.game_over and not self.won:
            # Показываем все мины
            if self.board[r][c] == '*':
                return '💣'
            if self.revealed[r][c]:
                return self.board[r][c]
            return ' '
        if self.revealed[r][c]:
            return self.board[r][c]
        if self.flagged[r][c]:
            return '⚑'
        return ' '

    def get_color(self, r, c):
        if self.game_over and not self.won:
            if self.board[r][c] == '*':
                return COLORS['red']
        if not self.revealed[r][c]:
            if self.flagged[r][c]:
                return COLORS['yellow']
            return COLORS['white']  # цвет неоткрытой
        ch = self.board[r][c]
        if ch.isdigit():
            return DIGIT_COLORS.get(int(ch), COLORS['white'])
        if ch == ' ':
            return COLORS['white']
        return COLORS['white']

    def render(self):
        # Верхняя строка с буквами столбцов
        print('  ' + ' '.join(chr(ord('a') + i) for i in range(self.cols)))
        for r in range(self.rows):
            line = f"{r+1:2} "
            for c in range(self.cols):
                ch = self.get_display_char(r, c)
                color = self.get_color(r, c)
                # Фон
                if self.revealed[r][c] or (self.game_over and not self.won):
                    bg = COLORS['bg_light']
                else:
                    bg = COLORS['bg_dark']
                # Если флаг, фон оставляем тёмным
                if self.flagged[r][c] and not self.revealed[r][c]:
                    bg = COLORS['bg_dark']
                # Собираем вывод
                line += f"{bg}{color}{ch}{COLORS['reset']} "
            print(line)

    def play(self):
        print("Добро пожаловать в Сапер!")
        print("Вводите ход в формате: a1 (открыть) или f a1 (флаг)")
        print("q - выход")
        while not self.game_over:
            self.render()
            print(f"Мин осталось: {self.mines_left}")
            cmd = input("Введите ход: ").strip().lower()
            if cmd == 'q':
                sys.exit()
            parts = cmd.split()
            if len(parts) == 2 and parts[0] == 'f':
                # Флаг
                coord = parts[1]
                if len(coord) < 2:
                    continue
                col = ord(coord[0]) - ord('a')
                row = int(coord[1:]) - 1
                self.toggle_flag(row, col)
            elif len(parts) == 1:
                coord = parts[0]
                if len(coord) < 2:
                    continue
                col = ord(coord[0]) - ord('a')
                row = int(coord[1:]) - 1
                self.reveal(row, col)
        # Конец игры
        self.render()
        if self.won:
            print(f"Поздравляем! Вы выиграли за {self.elapsed:.1f} секунд!")
            # Сохраняем рекорд (упрощённо)
            try:
                with open('records.json', 'r') as f:
                    records = json.load(f)
            except:
                records = {}
            key = f"{self.rows}x{self.cols}_{self.mines}"
            if key not in records or self.elapsed < records[key]:
                records[key] = self.elapsed
                with open('records.json', 'w') as f:
                    json.dump(records, f)
                print(f"Новый рекорд! {self.elapsed:.1f} сек.")
            else:
                print(f"Рекорд для этого уровня: {records[key]:.1f} сек.")
        else:
            print("Вы проиграли. Попробуйте снова!")

def choose_difficulty():
    print("Выберите уровень сложности:")
    print("1. Лёгкий (9x9, 10 мин)")
    print("2. Средний (16x16, 40 мин)")
    print("3. Сложный (30x16, 99 мин)")
    choice = input("Ваш выбор: ").strip()
    if choice == '1':
        return 9, 9, 10
    elif choice == '2':
        return 16, 16, 40
    elif choice == '3':
        return 30, 16, 99
    else:
        print("Некорректный выбор, установлен лёгкий.")
        return 9, 9, 10

if __name__ == "__main__":
    rows, cols, mines = choose_difficulty()
    game = Minesweeper(rows, cols, mines)
    game.play()
