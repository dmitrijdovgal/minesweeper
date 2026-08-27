#!/usr/bin/env node
// minesweeper.js
const readline = require('readline');
const fs = require('fs');
const crypto = require('crypto');

const COLORS = {
    reset: '\x1b[0m',
    blue: '\x1b[34m',
    green: '\x1b[32m',
    red: '\x1b[31m',
    dark_blue: '\x1b[34m',
    dark_red: '\x1b[31m',
    cyan: '\x1b[36m',
    black: '\x1b[30m',
    gray: '\x1b[37m',
    yellow: '\x1b[33m',
    white: '\x1b[97m',
    bg_dark: '\x1b[100m',
    bg_light: '\x1b[47m',
};

const DIGIT_COLORS = {
    1: COLORS.blue,
    2: COLORS.green,
    3: COLORS.red,
    4: COLORS.dark_blue,
    5: COLORS.dark_red,
    6: COLORS.cyan,
    7: COLORS.black,
    8: COLORS.gray,
};

class Minesweeper {
    constructor(rows, cols, mines) {
        this.rows = rows;
        this.cols = cols;
        this.mines = mines;
        this.board = Array.from({ length: rows }, () => Array(cols).fill(' '));
        this.revealed = Array.from({ length: rows }, () => Array(cols).fill(false));
        this.flagged = Array.from({ length: rows }, () => Array(cols).fill(false));
        this.gameOver = false;
        this.won = false;
        this.firstMove = true;
        this.minePositions = new Set();
        this.startTime = 0;
        this.elapsed = 0;
        this.minesLeft = mines;
        this.rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout,
        });
    }

    placeMines(firstRow, firstCol) {
        const safe = new Set();
        for (let dr = -1; dr <= 1; dr++) {
            for (let dc = -1; dc <= 1; dc++) {
                const nr = firstRow + dr, nc = firstCol + dc;
                if (nr >= 0 && nr < this.rows && nc >= 0 && nc < this.cols) {
                    safe.add(`${nr},${nc}`);
                }
            }
        }
        const candidates = [];
        for (let r = 0; r < this.rows; r++) {
            for (let c = 0; c < this.cols; c++) {
                if (!safe.has(`${r},${c}`)) candidates.push([r, c]);
            }
        }
        // Выбираем случайные мины
        const shuffled = candidates.sort(() => Math.random() - 0.5);
        const selected = shuffled.slice(0, this.mines);
        this.minePositions = new Set(selected.map(([r,c]) => `${r},${c}`));
        for (const key of this.minePositions) {
            const [r, c] = key.split(',').map(Number);
            this.board[r][c] = '*';
        }
    }

    countNeighbors(r, c) {
        if (this.board[r][c] === '*') return -1;
        let count = 0;
        for (let dr = -1; dr <= 1; dr++) {
            for (let dc = -1; dc <= 1; dc++) {
                const nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < this.rows && nc >= 0 && nc < this.cols && this.board[nr][nc] === '*') {
                    count++;
                }
            }
        }
        return count;
    }

    reveal(r, c) {
        if (this.gameOver || this.won) return;
        if (r < 0 || r >= this.rows || c < 0 || c >= this.cols) return;
        if (this.flagged[r][c]) return;
        if (this.revealed[r][c]) return;
        if (this.firstMove) {
            this.firstMove = false;
            this.placeMines(r, c);
            this.startTime = Date.now();
            // Заполняем цифры
            for (let rr = 0; rr < this.rows; rr++) {
                for (let cc = 0; cc < this.cols; cc++) {
                    if (this.board[rr][cc] !== '*') {
                        this.board[rr][cc] = String(this.countNeighbors(rr, cc));
                    }
                }
            }
        }
        if (this.board[r][c] === '*') {
            this.gameOver = true;
            this.revealAll();
            this.elapsed = (Date.now() - this.startTime) / 1000;
            return;
        }
        this.revealed[r][c] = true;
        if (this.board[r][c] === '0') {
            for (let dr = -1; dr <= 1; dr++) {
                for (let dc = -1; dc <= 1; dc++) {
                    const nr = r + dr, nc = c + dc;
                    if (nr >= 0 && nr < this.rows && nc >= 0 && nc < this.cols && !this.revealed[nr][nc] && !this.flagged[nr][nc]) {
                        this.reveal(nr, nc);
                    }
                }
            }
        }
        this.checkWin();
    }

    toggleFlag(r, c) {
        if (this.gameOver || this.won) return;
        if (r < 0 || r >= this.rows || c < 0 || c >= this.cols) return;
        if (this.revealed[r][c]) return;
        if (this.flagged[r][c]) {
            this.flagged[r][c] = false;
            this.minesLeft++;
        } else {
            this.flagged[r][c] = true;
            this.minesLeft--;
        }
    }

    checkWin() {
        let revealedCount = 0;
        for (let r = 0; r < this.rows; r++) {
            for (let c = 0; c < this.cols; c++) {
                if (this.revealed[r][c]) revealedCount++;
            }
        }
        if (revealedCount === this.rows * this.cols - this.mines) {
            this.won = true;
            this.elapsed = (Date.now() - this.startTime) / 1000;
            this.gameOver = true;
        }
    }

    revealAll() {
        for (let r = 0; r < this.rows; r++) {
            for (let c = 0; c < this.cols; c++) {
                this.revealed[r][c] = true;
            }
        }
    }

    getDisplayChar(r, c) {
        if (this.gameOver && !this.won) {
            if (this.board[r][c] === '*') return '💣';
            if (this.revealed[r][c]) return this.board[r][c];
            return ' ';
        }
        if (this.revealed[r][c]) return this.board[r][c];
        if (this.flagged[r][c]) return '⚑';
        return ' ';
    }

    getColor(r, c) {
        if (this.gameOver && !this.won) {
            if (this.board[r][c] === '*') return COLORS.red;
        }
        if (!this.revealed[r][c]) {
            if (this.flagged[r][c]) return COLORS.yellow;
            return COLORS.white;
        }
        const ch = this.board[r][c];
        if (ch >= '1' && ch <= '8') {
            return DIGIT_COLORS[parseInt(ch)] || COLORS.white;
        }
        return COLORS.white;
    }

    render() {
        process.stdout.write('  ');
        for (let c = 0; c < this.cols; c++) {
            process.stdout.write(String.fromCharCode(97 + c) + ' ');
        }
        process.stdout.write('\n');
        for (let r = 0; r < this.rows; r++) {
            process.stdout.write(`${r+1} `.padStart(3, ' '));
            for (let c = 0; c < this.cols; c++) {
                const ch = this.getDisplayChar(r, c);
                const color = this.getColor(r, c);
                const bg = (this.revealed[r][c] || (this.gameOver && !this.won)) ? COLORS.bg_light : COLORS.bg_dark;
                const flag = this.flagged[r][c] && !this.revealed[r][c] ? COLORS.bg_dark : '';
                process.stdout.write(`${bg}${color}${ch}${COLORS.reset} `);
            }
            process.stdout.write('\n');
        }
    }

    async play() {
        console.log('Добро пожаловать в Сапер!');
        console.log('Вводите ход в формате: a1 (открыть) или f a1 (флаг)');
        console.log('q - выход');
        while (!this.gameOver) {
            this.render();
            console.log(`Мин осталось: ${this.minesLeft}`);
            const cmd = await this.question('Введите ход: ');
            if (cmd === 'q') process.exit();
            const parts = cmd.trim().toLowerCase().split(/\s+/);
            if (parts.length === 2 && parts[0] === 'f') {
                const coord = parts[1];
                if (coord.length < 2) continue;
                const col = coord.charCodeAt(0) - 97;
                const row = parseInt(coord.slice(1)) - 1;
                this.toggleFlag(row, col);
            } else if (parts.length === 1) {
                const coord = parts[0];
                if (coord.length < 2) continue;
                const col = coord.charCodeAt(0) - 97;
                const row = parseInt(coord.slice(1)) - 1;
                this.reveal(row, col);
            }
        }
        this.render();
        if (this.won) {
            console.log(`Поздравляем! Вы выиграли за ${this.elapsed.toFixed(1)} секунд!`);
            // Сохраняем рекорд
            try {
                const data = fs.readFileSync('records.json', 'utf8');
                const records = JSON.parse(data);
                const key = `${this.rows}x${this.cols}_${this.mines}`;
                if (!records[key] || this.elapsed < records[key]) {
                    records[key] = this.elapsed;
                    fs.writeFileSync('records.json', JSON.stringify(records, null, 2));
                    console.log(`Новый рекорд! ${this.elapsed.toFixed(1)} сек.`);
                } else {
                    console.log(`Рекорд для этого уровня: ${records[key].toFixed(1)} сек.`);
                }
            } catch {
                const records = {};
                const key = `${this.rows}x${this.cols}_${this.mines}`;
                records[key] = this.elapsed;
                fs.writeFileSync('records.json', JSON.stringify(records, null, 2));
                console.log(`Новый рекорд! ${this.elapsed.toFixed(1)} сек.`);
            }
        } else {
            console.log('Вы проиграли. Попробуйте снова!');
        }
        this.rl.close();
    }

    question(prompt) {
        return new Promise(resolve => {
            this.rl.question(prompt, resolve);
        });
    }
}

async function chooseDifficulty() {
    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
    });
    const q = (prompt) => new Promise(resolve => rl.question(prompt, resolve));
    console.log('Выберите уровень сложности:');
    console.log('1. Лёгкий (9x9, 10 мин)');
    console.log('2. Средний (16x16, 40 мин)');
    console.log('3. Сложный (30x16, 99 мин)');
    const choice = await q('Ваш выбор: ');
    rl.close();
    if (choice === '1') return [9, 9, 10];
    if (choice === '2') return [16, 16, 40];
    if (choice === '3') return [30, 16, 99];
    console.log('Некорректный выбор, установлен лёгкий.');
    return [9, 9, 10];
}

(async () => {
    const [rows, cols, mines] = await chooseDifficulty();
    const game = new Minesweeper(rows, cols, mines);
    await game.play();
})();
