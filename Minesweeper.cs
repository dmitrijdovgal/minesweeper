// Minesweeper.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Minesweeper
{
    class Program
    {
        static void Main(string[] args)
        {
            var (rows, cols, mines) = ChooseDifficulty();
            var game = new Minesweeper(rows, cols, mines);
            game.Play();
        }

        static (int rows, int cols, int mines) ChooseDifficulty()
        {
            Console.WriteLine("Выберите уровень сложности:");
            Console.WriteLine("1. Лёгкий (9x9, 10 мин)");
            Console.WriteLine("2. Средний (16x16, 40 мин)");
            Console.WriteLine("3. Сложный (30x16, 99 мин)");
            Console.Write("Ваш выбор: ");
            string choice = Console.ReadLine().Trim();
            switch (choice)
            {
                case "1": return (9, 9, 10);
                case "2": return (16, 16, 40);
                case "3": return (30, 16, 99);
                default:
                    Console.WriteLine("Некорректный выбор, установлен лёгкий.");
                    return (9, 9, 10);
            }
        }
    }

    class Minesweeper
    {
        private const string RESET = "\x1b[0m";
        private const string BLUE = "\x1b[34m";
        private const string GREEN = "\x1b[32m";
        private const string RED = "\x1b[31m";
        private const string CYAN = "\x1b[36m";
        private const string BLACK = "\x1b[30m";
        private const string GRAY = "\x1b[37m";
        private const string YELLOW = "\x1b[33m";
        private const string WHITE = "\x1b[97m";
        private const string BG_DARK = "\x1b[100m";
        private const string BG_LIGHT = "\x1b[47m";

        private static readonly Dictionary<char, string> digitColors = new Dictionary<char, string>
        {
            {'1', BLUE}, {'2', GREEN}, {'3', RED}, {'4', BLUE},
            {'5', RED}, {'6', CYAN}, {'7', BLACK}, {'8', GRAY}
        };

        private int rows, cols, mines;
        private char[][] board;
        private bool[][] revealed;
        private bool[][] flagged;
        private bool gameOver, won, firstMove;
        private HashSet<(int, int)> minePositions = new HashSet<(int, int)>();
        private DateTime startTime;
        private double elapsed;
        private int minesLeft;

        public Minesweeper(int rows, int cols, int mines)
        {
            this.rows = rows;
            this.cols = cols;
            this.mines = mines;
            board = new char[rows][];
            revealed = new bool[rows][];
            flagged = new bool[rows][];
            for (int r = 0; r < rows; r++)
            {
                board[r] = Enumerable.Repeat(' ', cols).ToArray();
                revealed[r] = new bool[cols];
                flagged[r] = new bool[cols];
            }
            minesLeft = mines;
        }

        private void PlaceMines(int firstRow, int firstCol)
        {
            var safe = new HashSet<(int, int)>();
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++)
                {
                    int nr = firstRow + dr, nc = firstCol + dc;
                    if (nr >= 0 && nr < rows && nc >= 0 && nc < cols)
                        safe.Add((nr, nc));
                }
            var candidates = new List<(int, int)>();
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++)
                    if (!safe.Contains((r, c)))
                        candidates.Add((r, c));
            var rng = new Random();
            candidates = candidates.OrderBy(_ => rng.Next()).ToList();
            minePositions = candidates.Take(mines).ToHashSet();
            foreach (var (r, c) in minePositions)
                board[r][c] = '*';
        }

        private int CountNeighbors(int r, int c)
        {
            if (board[r][c] == '*') return -1;
            int cnt = 0;
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++)
                {
                    int nr = r + dr, nc = c + dc;
                    if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && board[nr][nc] == '*')
                        cnt++;
                }
            return cnt;
        }

        public void Reveal(int r, int c)
        {
            if (gameOver || won) return;
            if (r < 0 || r >= rows || c < 0 || c >= cols) return;
            if (flagged[r][c]) return;
            if (revealed[r][c]) return;
            if (firstMove)
            {
                firstMove = false;
                PlaceMines(r, c);
                startTime = DateTime.UtcNow;
                for (int rr = 0; rr < rows; rr++)
                    for (int cc = 0; cc < cols; cc++)
                        if (board[rr][cc] != '*')
                            board[rr][cc] = CountNeighbors(rr, cc) == 0 ? ' ' : (char)('0' + CountNeighbors(rr, cc));
            }
            if (board[r][c] == '*')
            {
                gameOver = true;
                RevealAll();
                elapsed = (DateTime.UtcNow - startTime).TotalSeconds;
                return;
            }
            revealed[r][c] = true;
            if (board[r][c] == ' ')
            {
                for (int dr = -1; dr <= 1; dr++)
                    for (int dc = -1; dc <= 1; dc++)
                    {
                        int nr = r + dr, nc = c + dc;
                        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !revealed[nr][nc] && !flagged[nr][nc])
                            Reveal(nr, nc);
                    }
            }
            CheckWin();
        }

        public void ToggleFlag(int r, int c)
        {
            if (gameOver || won) return;
            if (r < 0 || r >= rows || c < 0 || c >= cols) return;
            if (revealed[r][c]) return;
            if (flagged[r][c])
            {
                flagged[r][c] = false;
                minesLeft++;
            }
            else
            {
                flagged[r][c] = true;
                minesLeft--;
            }
        }

        private void CheckWin()
        {
            int revealedCount = 0;
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++)
                    if (revealed[r][c]) revealedCount++;
            if (revealedCount == rows * cols - mines)
            {
                won = true;
                elapsed = (DateTime.UtcNow - startTime).TotalSeconds;
                gameOver = true;
            }
        }

        private void RevealAll()
        {
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++)
                    revealed[r][c] = true;
        }

        private string GetDisplayChar(int r, int c)
        {
            if (gameOver && !won)
            {
                if (board[r][c] == '*') return "💣";
                if (revealed[r][c]) return board[r][c].ToString();
                return " ";
            }
            if (revealed[r][c]) return board[r][c].ToString();
            if (flagged[r][c]) return "⚑";
            return " ";
        }

        private string GetColor(int r, int c)
        {
            if (gameOver && !won)
            {
                if (board[r][c] == '*') return RED;
            }
            if (!revealed[r][c])
            {
                if (flagged[r][c]) return YELLOW;
                return WHITE;
            }
            char ch = board[r][c];
            if (ch >= '1' && ch <= '8') return digitColors.GetValueOrDefault(ch, WHITE);
            return WHITE;
        }

        private void Render()
        {
            Console.Write("  ");
            for (int c = 0; c < cols; c++)
                Console.Write((char)('a' + c) + " ");
            Console.WriteLine();
            for (int r = 0; r < rows; r++)
            {
                Console.Write($"{r + 1,2} ");
                for (int c = 0; c < cols; c++)
                {
                    string ch = GetDisplayChar(r, c);
                    string color = GetColor(r, c);
                    string bg = (revealed[r][c] || (gameOver && !won)) ? BG_LIGHT : BG_DARK;
                    if (flagged[r][c] && !revealed[r][c]) bg = BG_DARK;
                    Console.Write($"{bg}{color}{ch}{RESET} ");
                }
                Console.WriteLine();
            }
        }

        public void Play()
        {
            Console.WriteLine("Добро пожаловать в Сапер!");
            Console.WriteLine("Вводите ход в формате: a1 (открыть) или f a1 (флаг)");
            Console.WriteLine("q - выход");
            while (!gameOver)
            {
                Render();
                Console.WriteLine($"Мин осталось: {minesLeft}");
                Console.Write("Введите ход: ");
                string line = Console.ReadLine().Trim();
                if (line == "q") break;
                string[] parts = line.Split(' ');
                if (parts.Length == 2 && parts[0] == "f")
                {
                    string coord = parts[1];
                    if (coord.Length < 2) continue;
                    int col = coord[0] - 'a';
                    int row = int.Parse(coord.Substring(1)) - 1;
                    ToggleFlag(row, col);
                }
                else if (parts.Length == 1)
                {
                    string coord = parts[0];
                    if (coord.Length < 2) continue;
                    int col = coord[0] - 'a';
                    int row = int.Parse(coord.Substring(1)) - 1;
                    Reveal(row, col);
                }
            }
            Render();
            if (won)
            {
                Console.WriteLine($"Поздравляем! Вы выиграли за {elapsed:F1} секунд!");
                // Рекорды
                var records = new Dictionary<string, double>();
                try
                {
                    string json = File.ReadAllText("records.json");
                    records = JsonSerializer.Deserialize<Dictionary<string, double>>(json);
                }
                catch { }
                string key = $"{rows}x{cols}_{mines}";
                if (!records.ContainsKey(key) || elapsed < records[key])
                {
                    records[key] = elapsed;
                    string json = JsonSerializer.Serialize(records, new JsonSerializerOptions { WriteIndented = true });
                    File.WriteAllText("records.json", json);
                    Console.WriteLine($"Новый рекорд! {elapsed:F1} сек.");
                }
                else
                {
                    Console.WriteLine($"Рекорд для этого уровня: {records[key]:F1} сек.");
                }
            }
            else
            {
                Console.WriteLine("Вы проиграли. Попробуйте снова!");
            }
        }
    }
}
