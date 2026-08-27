// minesweeper.rs
use std::io::{self, Write, BufRead};
use std::fs;
use std::collections::HashMap;
use std::time::{Instant, Duration};
use rand::Rng;
use rand::seq::SliceRandom;

const RESET: &str = "\x1b[0m";
const BLUE: &str = "\x1b[34m";
const GREEN: &str = "\x1b[32m";
const RED: &str = "\x1b[31m";
const CYAN: &str = "\x1b[36m";
const BLACK: &str = "\x1b[30m";
const GRAY: &str = "\x1b[37m";
const YELLOW: &str = "\x1b[33m";
const WHITE: &str = "\x1b[97m";
const BG_DARK: &str = "\x1b[100m";
const BG_LIGHT: &str = "\x1b[47m";

fn digit_color(ch: char) -> &'static str {
    match ch {
        '1' => BLUE,
        '2' => GREEN,
        '3' => RED,
        '4' => BLUE,
        '5' => RED,
        '6' => CYAN,
        '7' => BLACK,
        '8' => GRAY,
        _ => WHITE,
    }
}

struct Minesweeper {
    rows: usize,
    cols: usize,
    mines: usize,
    board: Vec<Vec<char>>,
    revealed: Vec<Vec<bool>>,
    flagged: Vec<Vec<bool>>,
    game_over: bool,
    won: bool,
    first_move: bool,
    mine_positions: Vec<(usize, usize)>,
    start_time: Option<Instant>,
    elapsed: f64,
    mines_left: i32,
}

impl Minesweeper {
    fn new(rows: usize, cols: usize, mines: usize) -> Self {
        Minesweeper {
            rows,
            cols,
            mines,
            board: vec![vec![' '; cols]; rows],
            revealed: vec![vec![false; cols]; rows],
            flagged: vec![vec![false; cols]; rows],
            game_over: false,
            won: false,
            first_move: true,
            mine_positions: Vec::new(),
            start_time: None,
            elapsed: 0.0,
            mines_left: mines as i32,
        }
    }

    fn place_mines(&mut self, first_row: usize, first_col: usize) {
        let mut safe = std::collections::HashSet::new();
        for dr in -1..=1 {
            for dc in -1..=1 {
                let nr = first_row as i32 + dr;
                let nc = first_col as i32 + dc;
                if nr >= 0 && nr < self.rows as i32 && nc >= 0 && nc < self.cols as i32 {
                    safe.insert((nr as usize, nc as usize));
                }
            }
        }
        let mut candidates = Vec::new();
        for r in 0..self.rows {
            for c in 0..self.cols {
                if !safe.contains(&(r, c)) {
                    candidates.push((r, c));
                }
            }
        }
        let mut rng = rand::thread_rng();
        candidates.shuffle(&mut rng);
        self.mine_positions = candidates[..self.mines].to_vec();
        for &(r, c) in &self.mine_positions {
            self.board[r][c] = '*';
        }
    }

    fn count_neighbors(&self, r: usize, c: usize) -> i32 {
        if self.board[r][c] == '*' { return -1; }
        let mut count = 0;
        for dr in -1..=1 {
            for dc in -1..=1 {
                let nr = r as i32 + dr;
                let nc = c as i32 + dc;
                if nr >= 0 && nr < self.rows as i32 && nc >= 0 && nc < self.cols as i32 {
                    if self.board[nr as usize][nc as usize] == '*' {
                        count += 1;
                    }
                }
            }
        }
        count
    }

    fn reveal(&mut self, r: usize, c: usize) {
        if self.game_over || self.won { return; }
        if r >= self.rows || c >= self.cols { return; }
        if self.flagged[r][c] { return; }
        if self.revealed[r][c] { return; }
        if self.first_move {
            self.first_move = false;
            self.place_mines(r, c);
            self.start_time = Some(Instant::now());
            for rr in 0..self.rows {
                for cc in 0..self.cols {
                    if self.board[rr][cc] != '*' {
                        let cnt = self.count_neighbors(rr, cc);
                        self.board[rr][cc] = if cnt == 0 { ' ' } else { char::from_digit(cnt as u32, 10).unwrap() };
                    }
                }
            }
        }
        if self.board[r][c] == '*' {
            self.game_over = true;
            self.reveal_all();
            if let Some(st) = self.start_time {
                self.elapsed = st.elapsed().as_secs_f64();
            }
            return;
        }
        self.revealed[r][c] = true;
        if self.board[r][c] == ' ' { // пустая клетка (0)
            for dr in -1..=1 {
                for dc in -1..=1 {
                    let nr = r as i32 + dr;
                    let nc = c as i32 + dc;
                    if nr >= 0 && nr < self.rows as i32 && nc >= 0 && nc < self.cols as i32 {
                        let (nr, nc) = (nr as usize, nc as usize);
                        if !self.revealed[nr][nc] && !self.flagged[nr][nc] {
                            self.reveal(nr, nc);
                        }
                    }
                }
            }
        }
        self.check_win();
    }

    fn toggle_flag(&mut self, r: usize, c: usize) {
        if self.game_over || self.won { return; }
        if r >= self.rows || c >= self.cols { return; }
        if self.revealed[r][c] { return; }
        if self.flagged[r][c] {
            self.flagged[r][c] = false;
            self.mines_left += 1;
        } else {
            self.flagged[r][c] = true;
            self.mines_left -= 1;
        }
    }

    fn check_win(&mut self) {
        let mut revealed_count = 0;
        for r in 0..self.rows {
            for c in 0..self.cols {
                if self.revealed[r][c] { revealed_count += 1; }
            }
        }
        if revealed_count == self.rows * self.cols - self.mines {
            self.won = true;
            if let Some(st) = self.start_time {
                self.elapsed = st.elapsed().as_secs_f64();
            }
            self.game_over = true;
        }
    }

    fn reveal_all(&mut self) {
        for r in 0..self.rows {
            for c in 0..self.cols {
                self.revealed[r][c] = true;
            }
        }
    }

    fn get_display_char(&self, r: usize, c: usize) -> char {
        if self.game_over && !self.won {
            if self.board[r][c] == '*' { return '💣'; }
            if self.revealed[r][c] { return self.board[r][c]; }
            return ' ';
        }
        if self.revealed[r][c] { return self.board[r][c]; }
        if self.flagged[r][c] { return '⚑'; }
        ' '
    }

    fn get_color(&self, r: usize, c: usize) -> &'static str {
        if self.game_over && !self.won {
            if self.board[r][c] == '*' { return RED; }
        }
        if !self.revealed[r][c] {
            if self.flagged[r][c] { return YELLOW; }
            return WHITE;
        }
        let ch = self.board[r][c];
        if ch.is_ascii_digit() {
            digit_color(ch)
        } else {
            WHITE
        }
    }

    fn render(&self) {
        print!("  ");
        for c in 0..self.cols {
            print!("{} ", (b'a' + c as u8) as char);
        }
        println!();
        for r in 0..self.rows {
            print!("{:2} ", r+1);
            for c in 0..self.cols {
                let ch = self.get_display_char(r, c);
                let color = self.get_color(r, c);
                let bg = if self.revealed[r][c] || (self.game_over && !self.won) { BG_LIGHT } else { BG_DARK };
                print!("{}{}{}{} ", bg, color, ch, RESET);
            }
            println!();
        }
    }

    fn play(&mut self) {
        let stdin = io::stdin();
        let mut input = String::new();
        println!("Добро пожаловать в Сапер!");
        println!("Вводите ход в формате: a1 (открыть) или f a1 (флаг)");
        println!("q - выход");
        while !self.game_over {
            self.render();
            println!("Мин осталось: {}", self.mines_left);
            print!("Введите ход: ");
            io::stdout().flush().unwrap();
            input.clear();
            stdin.read_line(&mut input).unwrap();
            let cmd = input.trim();
            if cmd == "q" { break; }
            let parts: Vec<&str> = cmd.split_whitespace().collect();
            if parts.len() == 2 && parts[0] == "f" {
                let coord = parts[1];
                if coord.len() < 2 { continue; }
                let col = coord.chars().next().unwrap() as usize - 'a' as usize;
                let row = coord[1..].parse::<usize>().unwrap_or(1) - 1;
                self.toggle_flag(row, col);
            } else if parts.len() == 1 {
                let coord = parts[0];
                if coord.len() < 2 { continue; }
                let col = coord.chars().next().unwrap() as usize - 'a' as usize;
                let row = coord[1..].parse::<usize>().unwrap_or(1) - 1;
                self.reveal(row, col);
            }
        }
        self.render();
        if self.won {
            println!("Поздравляем! Вы выиграли за {:.1} секунд!", self.elapsed);
            // Чтение рекордов
            let key = format!("{}x{}_{}", self.rows, self.cols, self.mines);
            let mut records: HashMap<String, f64> = HashMap::new();
            if let Ok(data) = fs::read_to_string("records.json") {
                if let Ok(parsed) = serde_json::from_str(&data) {
                    records = parsed;
                }
            }
            let old = records.get(&key).copied();
            if old.is_none() || self.elapsed < old.unwrap() {
                records.insert(key, self.elapsed);
                if let Ok(json) = serde_json::to_string_pretty(&records) {
                    let _ = fs::write("records.json", json);
                }
                println!("Новый рекорд! {:.1} сек.", self.elapsed);
            } else {
                println!("Рекорд для этого уровня: {:.1} сек.", old.unwrap());
            }
        } else {
            println!("Вы проиграли. Попробуйте снова!");
        }
    }
}

fn choose_difficulty() -> (usize, usize, usize) {
    println!("Выберите уровень сложности:");
    println!("1. Лёгкий (9x9, 10 мин)");
    println!("2. Средний (16x16, 40 мин)");
    println!("3. Сложный (30x16, 99 мин)");
    print!("Ваш выбор: ");
    io::stdout().flush().unwrap();
    let mut input = String::new();
    io::stdin().read_line(&mut input).unwrap();
    match input.trim() {
        "1" => (9, 9, 10),
        "2" => (16, 16, 40),
        "3" => (30, 16, 99),
        _ => {
            println!("Некорректный выбор, установлен лёгкий.");
            (9, 9, 10)
        }
    }
}

fn main() {
    let (rows, cols, mines) = choose_difficulty();
    let mut game = Minesweeper::new(rows, cols, mines);
    game.play();
}
