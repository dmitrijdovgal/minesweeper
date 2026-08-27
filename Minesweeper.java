// Minesweeper.java
import java.io.*;
import java.util.*;
import java.time.Instant;
import java.time.Duration;
import java.nio.file.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class Minesweeper {
    private static final String RESET = "\033[0m";
    private static final String BLUE = "\033[34m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String CYAN = "\033[36m";
    private static final String BLACK = "\033[30m";
    private static final String GRAY = "\033[37m";
    private static final String YELLOW = "\033[33m";
    private static final String WHITE = "\033[97m";
    private static final String BG_DARK = "\033[100m";
    private static final String BG_LIGHT = "\033[47m";

    private static final Map<Character, String> digitColors = new HashMap<>();
    static {
        digitColors.put('1', BLUE);
        digitColors.put('2', GREEN);
        digitColors.put('3', RED);
        digitColors.put('4', BLUE);
        digitColors.put('5', RED);
        digitColors.put('6', CYAN);
        digitColors.put('7', BLACK);
        digitColors.put('8', GRAY);
    }

    private int rows, cols, mines;
    private char[][] board;
    private boolean[][] revealed;
    private boolean[][] flagged;
    private boolean gameOver, won, firstMove;
    private Set<String> minePositions = new HashSet<>();
    private Instant startTime;
    private double elapsed;
    private int minesLeft;
    private Scanner scanner = new Scanner(System.in);

    public Minesweeper(int rows, int cols, int mines) {
        this.rows = rows;
        this.cols = cols;
        this.mines = mines;
        board = new char[rows][cols];
        revealed = new boolean[rows][cols];
        flagged = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            Arrays.fill(board[r], ' ');
        }
        minesLeft = mines;
    }

    private void placeMines(int firstRow, int firstCol) {
        Set<String> safe = new HashSet<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = firstRow + dr, nc = firstCol + dc;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                    safe.add(nr + "," + nc);
                }
            }
        }
        List<String> candidates = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!safe.contains(r + "," + c)) {
                    candidates.add(r + "," + c);
                }
            }
        }
        Collections.shuffle(candidates);
        for (int i = 0; i < mines; i++) {
            String key = candidates.get(i);
            String[] parts = key.split(",");
            int r = Integer.parseInt(parts[0]);
            int c = Integer.parseInt(parts[1]);
            minePositions.add(key);
            board[r][c] = '*';
        }
    }

    private int countNeighbors(int r, int c) {
        if (board[r][c] == '*') return -1;
        int cnt = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && board[nr][nc] == '*')
                    cnt++;
            }
        }
        return cnt;
    }

    public void reveal(int r, int c) {
        if (gameOver || won) return;
        if (r < 0 || r >= rows || c < 0 || c >= cols) return;
        if (flagged[r][c]) return;
        if (revealed[r][c]) return;
        if (firstMove) {
            firstMove = false;
            placeMines(r, c);
            startTime = Instant.now();
            for (int rr = 0; rr < rows; rr++) {
                for (int cc = 0; cc < cols; cc++) {
                    if (board[rr][cc] != '*') {
                        int cnt = countNeighbors(rr, cc);
                        board[rr][cc] = cnt == 0 ? ' ' : (char)('0' + cnt);
                    }
                }
            }
        }
        if (board[r][c] == '*') {
            gameOver = true;
            revealAll();
            elapsed = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
            return;
        }
        revealed[r][c] = true;
        if (board[r][c] == ' ') {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int nr = r + dr, nc = c + dc;
                    if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !revealed[nr][nc] && !flagged[nr][nc]) {
                        reveal(nr, nc);
                    }
                }
            }
        }
        checkWin();
    }

    public void toggleFlag(int r, int c) {
        if (gameOver || won) return;
        if (r < 0 || r >= rows || c < 0 || c >= cols) return;
        if (revealed[r][c]) return;
        if (flagged[r][c]) {
            flagged[r][c] = false;
            minesLeft++;
        } else {
            flagged[r][c] = true;
            minesLeft--;
        }
    }

    private void checkWin() {
        int revealedCount = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (revealed[r][c]) revealedCount++;
            }
        }
        if (revealedCount == rows * cols - mines) {
            won = true;
            elapsed = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
            gameOver = true;
        }
    }

    private void revealAll() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                revealed[r][c] = true;
            }
        }
    }

    private String getDisplayChar(int r, int c) {
        if (gameOver && !won) {
            if (board[r][c] == '*') return "💣";
            if (revealed[r][c]) return String.valueOf(board[r][c]);
            return " ";
        }
        if (revealed[r][c]) return String.valueOf(board[r][c]);
        if (flagged[r][c]) return "⚑";
        return " ";
    }

    private String getColor(int r, int c) {
        if (gameOver && !won) {
            if (board[r][c] == '*') return RED;
        }
        if (!revealed[r][c]) {
            if (flagged[r][c]) return YELLOW;
            return WHITE;
        }
        char ch = board[r][c];
        if (ch >= '1' && ch <= '8') return digitColors.getOrDefault(ch, WHITE);
        return WHITE;
    }

    private void render() {
        System.out.print("  ");
        for (int c = 0; c < cols; c++) {
            System.out.print((char)('a' + c) + " ");
        }
        System.out.println();
        for (int r = 0; r < rows; r++) {
            System.out.printf("%2d ", r + 1);
            for (int c = 0; c < cols; c++) {
                String ch = getDisplayChar(r, c);
                String color = getColor(r, c);
                String bg = (revealed[r][c] || (gameOver && !won)) ? BG_LIGHT : BG_DARK;
                if (flagged[r][c] && !revealed[r][c]) bg = BG_DARK;
                System.out.print(bg + color + ch + RESET + " ");
            }
            System.out.println();
        }
    }

    public void play() {
        System.out.println("Добро пожаловать в Сапер!");
        System.out.println("Вводите ход в формате: a1 (открыть) или f a1 (флаг)");
        System.out.println("q - выход");
        while (!gameOver) {
            render();
            System.out.println("Мин осталось: " + minesLeft);
            System.out.print("Введите ход: ");
            String line = scanner.nextLine().trim();
            if (line.equalsIgnoreCase("q")) break;
            String[] parts = line.split("\\s+");
            if (parts.length == 2 && parts[0].equals("f")) {
                String coord = parts[1];
                if (coord.length() < 2) continue;
                int col = coord.charAt(0) - 'a';
                int row = Integer.parseInt(coord.substring(1)) - 1;
                toggleFlag(row, col);
            } else if (parts.length == 1) {
                String coord = parts[0];
                if (coord.length() < 2) continue;
                int col = coord.charAt(0) - 'a';
                int row = Integer.parseInt(coord.substring(1)) - 1;
                reveal(row, col);
            }
        }
        render();
        if (won) {
            System.out.printf("Поздравляем! Вы выиграли за %.1f секунд!\n", elapsed);
            // Рекорды
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Map<String, Double> records = new HashMap<>();
            try {
                String json = new String(Files.readAllBytes(Paths.get("records.json")));
                records = gson.fromJson(json, new TypeToken<Map<String, Double>>(){}.getType());
            } catch (Exception ignored) {}
            String key = rows + "x" + cols + "_" + mines;
            if (!records.containsKey(key) || elapsed < records.get(key)) {
                records.put(key, elapsed);
                try {
                    Files.write(Paths.get("records.json"), gson.toJson(records).getBytes());
                } catch (IOException e) {}
                System.out.printf("Новый рекорд! %.1f сек.\n", elapsed);
            } else {
                System.out.printf("Рекорд для этого уровня: %.1f сек.\n", records.get(key));
            }
        } else {
            System.out.println("Вы проиграли. Попробуйте снова!");
        }
        scanner.close();
    }

    private static int[] chooseDifficulty() {
        Scanner sc = new Scanner(System.in);
        System.out.println("Выберите уровень сложности:");
        System.out.println("1. Лёгкий (9x9, 10 мин)");
        System.out.println("2. Средний (16x16, 40 мин)");
        System.out.println("3. Сложный (30x16, 99 мин)");
        System.out.print("Ваш выбор: ");
        String choice = sc.nextLine().trim();
        sc.close();
        switch (choice) {
            case "1": return new int[]{9, 9, 10};
            case "2": return new int[]{16, 16, 40};
            case "3": return new int[]{30, 16, 99};
            default:
                System.out.println("Некорректный выбор, установлен лёгкий.");
                return new int[]{9, 9, 10};
        }
    }

    public static void main(String[] args) {
        int[] params = chooseDifficulty();
        Minesweeper game = new Minesweeper(params[0], params[1], params[2]);
        game.play();
    }
}
