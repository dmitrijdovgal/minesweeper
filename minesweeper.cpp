// minesweeper.cpp
#include <iostream>
#include <string>
#include <vector>
#include <random>
#include <unordered_set>
#include <map>
#include <fstream>
#include <chrono>
#include <iomanip>
#include <sstream>
#include <json/json.h> // using jsoncpp

using namespace std;

const string RESET = "\033[0m";
const string BLUE = "\033[34m";
const string GREEN = "\033[32m";
const string RED = "\033[31m";
const string CYAN = "\033[36m";
const string BLACK = "\033[30m";
const string GRAY = "\033[37m";
const string YELLOW = "\033[33m";
const string WHITE = "\033[97m";
const string BG_DARK = "\033[100m";
const string BG_LIGHT = "\033[47m";

map<char, string> digitColors = {
    {'1', BLUE}, {'2', GREEN}, {'3', RED}, {'4', BLUE},
    {'5', RED}, {'6', CYAN}, {'7', BLACK}, {'8', GRAY}
};

class Minesweeper {
private:
    int rows, cols, mines;
    vector<vector<char>> board;
    vector<vector<bool>> revealed, flagged;
    bool gameOver, won, firstMove;
    unordered_set<string> minePositions;
    chrono::steady_clock::time_point startTime;
    double elapsed;
    int minesLeft;

public:
    Minesweeper(int r, int c, int m) : rows(r), cols(c), mines(m), gameOver(false), won(false),
        firstMove(true), minesLeft(m) {
        board = vector<vector<char>>(rows, vector<char>(cols, ' '));
        revealed = vector<vector<bool>>(rows, vector<bool>(cols, false));
        flagged = vector<vector<bool>>(rows, vector<bool>(cols, false));
    }

    void placeMines(int firstRow, int firstCol) {
        unordered_set<string> safe;
        for (int dr = -1; dr <= 1; ++dr)
            for (int dc = -1; dc <= 1; ++dc) {
                int nr = firstRow + dr, nc = firstCol + dc;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols)
                    safe.insert(to_string(nr) + "," + to_string(nc));
            }
        vector<string> candidates;
        for (int r = 0; r < rows; ++r)
            for (int c = 0; c < cols; ++c) {
                string key = to_string(r) + "," + to_string(c);
                if (safe.find(key) == safe.end())
                    candidates.push_back(key);
            }
        random_device rd;
        mt19937 g(rd());
        shuffle(candidates.begin(), candidates.end(), g);
        for (int i = 0; i < mines; ++i) {
            string key = candidates[i];
            int pos = key.find(',');
            int r = stoi(key.substr(0, pos));
            int c = stoi(key.substr(pos+1));
            minePositions.insert(key);
            board[r][c] = '*';
        }
    }

    int countNeighbors(int r, int c) {
        if (board[r][c] == '*') return -1;
        int cnt = 0;
        for (int dr = -1; dr <= 1; ++dr)
            for (int dc = -1; dc <= 1; ++dc) {
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && board[nr][nc] == '*')
                    cnt++;
            }
        return cnt;
    }

    void reveal(int r, int c) {
        if (gameOver || won) return;
        if (r < 0 || r >= rows || c < 0 || c >= cols) return;
        if (flagged[r][c]) return;
        if (revealed[r][c]) return;
        if (firstMove) {
            firstMove = false;
            placeMines(r, c);
            startTime = chrono::steady_clock::now();
            for (int rr = 0; rr < rows; ++rr)
                for (int cc = 0; cc < cols; ++cc)
                    if (board[rr][cc] != '*') {
                        int cnt = countNeighbors(rr, cc);
                        board[rr][cc] = cnt == 0 ? ' ' : char('0' + cnt);
                    }
        }
        if (board[r][c] == '*') {
            gameOver = true;
            revealAll();
            elapsed = chrono::duration<double>(chrono::steady_clock::now() - startTime).count();
            return;
        }
        revealed[r][c] = true;
        if (board[r][c] == ' ') {
            for (int dr = -1; dr <= 1; ++dr)
                for (int dc = -1; dc <= 1; ++dc) {
                    int nr = r + dr, nc = c + dc;
                    if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !revealed[nr][nc] && !flagged[nr][nc])
                        reveal(nr, nc);
                }
        }
        checkWin();
    }

    void toggleFlag(int r, int c) {
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

    void checkWin() {
        int revealedCount = 0;
        for (int r = 0; r < rows; ++r)
            for (int c = 0; c < cols; ++c)
                if (revealed[r][c]) revealedCount++;
        if (revealedCount == rows * cols - mines) {
            won = true;
            elapsed = chrono::duration<double>(chrono::steady_clock::now() - startTime).count();
            gameOver = true;
        }
    }

    void revealAll() {
        for (int r = 0; r < rows; ++r)
            for (int c = 0; c < cols; ++c)
                revealed[r][c] = true;
    }

    string getDisplayChar(int r, int c) {
        if (gameOver && !won) {
            if (board[r][c] == '*') return "💣";
            if (revealed[r][c]) return string(1, board[r][c]);
            return " ";
        }
        if (revealed[r][c]) return string(1, board[r][c]);
        if (flagged[r][c]) return "⚑";
        return " ";
    }

    string getColor(int r, int c) {
        if (gameOver && !won) {
            if (board[r][c] == '*') return RED;
        }
        if (!revealed[r][c]) {
            if (flagged[r][c]) return YELLOW;
            return WHITE;
        }
        char ch = board[r][c];
        if (ch >= '1' && ch <= '8')
            return digitColors[ch];
        return WHITE;
    }

    void render() {
        cout << "  ";
        for (int c = 0; c < cols; ++c)
            cout << char('a' + c) << " ";
        cout << endl;
        for (int r = 0; r < rows; ++r) {
            cout << setw(2) << r+1 << " ";
            for (int c = 0; c < cols; ++c) {
                string ch = getDisplayChar(r, c);
                string color = getColor(r, c);
                string bg = (revealed[r][c] || (gameOver && !won)) ? BG_LIGHT : BG_DARK;
                if (flagged[r][c] && !revealed[r][c]) bg = BG_DARK;
                cout << bg << color << ch << RESET << " ";
            }
            cout << endl;
        }
    }

    void play() {
        cout << "Добро пожаловать в Сапер!" << endl;
        cout << "Вводите ход в формате: a1 (открыть) или f a1 (флаг)" << endl;
        cout << "q - выход" << endl;
        string line;
        while (!gameOver) {
            render();
            cout << "Мин осталось: " << minesLeft << endl;
            cout << "Введите ход: ";
            getline(cin, line);
            if (line == "q") break;
            stringstream ss(line);
            string token;
            vector<string> parts;
            while (ss >> token) parts.push_back(token);
            if (parts.size() == 2 && parts[0] == "f") {
                string coord = parts[1];
                if (coord.size() < 2) continue;
                int col = coord[0] - 'a';
                int row = stoi(coord.substr(1)) - 1;
                toggleFlag(row, col);
            } else if (parts.size() == 1) {
                string coord = parts[0];
                if (coord.size() < 2) continue;
                int col = coord[0] - 'a';
                int row = stoi(coord.substr(1)) - 1;
                reveal(row, col);
            }
        }
        render();
        if (won) {
            cout << "Поздравляем! Вы выиграли за " << fixed << setprecision(1) << elapsed << " секунд!" << endl;
            // Рекорды
            Json::Value records;
            ifstream ifs("records.json");
            if (ifs) ifs >> records;
            string key = to_string(rows) + "x" + to_string(cols) + "_" + to_string(mines);
            if (!records.isMember(key) || elapsed < records[key].asDouble()) {
                records[key] = elapsed;
                ofstream ofs("records.json");
                ofs << records.toStyledString();
                cout << "Новый рекорд! " << fixed << setprecision(1) << elapsed << " сек." << endl;
            } else {
                cout << "Рекорд для этого уровня: " << fixed << setprecision(1) << records[key].asDouble() << " сек." << endl;
            }
        } else {
            cout << "Вы проиграли. Попробуйте снова!" << endl;
        }
    }
};

int main() {
    cout << "Выберите уровень сложности:" << endl;
    cout << "1. Лёгкий (9x9, 10 мин)" << endl;
    cout << "2. Средний (16x16, 40 мин)" << endl;
    cout << "3. Сложный (30x16, 99 мин)" << endl;
    cout << "Ваш выбор: ";
    string choice;
    getline(cin, choice);
    int rows, cols, mines;
    if (choice == "1") { rows=9; cols=9; mines=10; }
    else if (choice == "2") { rows=16; cols=16; mines=40; }
    else if (choice == "3") { rows=30; cols=16; mines=99; }
    else {
        cout << "Некорректный выбор, установлен лёгкий." << endl;
        rows=9; cols=9; mines=10;
    }
    Minesweeper game(rows, cols, mines);
    game.play();
    return 0;
}
