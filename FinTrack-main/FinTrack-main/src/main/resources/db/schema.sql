-- FinTrack SQLite Schema DDL
-- Enforces referential integrity, indexes, and domain constraints

PRAGMA foreign_keys = ON;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    currency TEXT NOT NULL DEFAULT 'USD',
    monthly_income NUMERIC NOT NULL DEFAULT 0.0 CHECK (monthly_income >= 0),
    global_budget NUMERIC NOT NULL DEFAULT 0.0 CHECK (global_budget >= 0),
    savings_goal NUMERIC NOT NULL DEFAULT 0.0 CHECK (savings_goal >= 0),
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 2. Transactions Table
CREATE TABLE IF NOT EXISTS transactions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    category TEXT NOT NULL,
    amount NUMERIC NOT NULL CHECK (amount > 0),
    payment_mode TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    note TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_transactions_user_timestamp 
    ON transactions(user_id, timestamp);

CREATE INDEX IF NOT EXISTS idx_transactions_category 
    ON transactions(category);

-- 3. Category Budgets Table
CREATE TABLE IF NOT EXISTS budgets (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    category TEXT NOT NULL,
    monthly_limit NUMERIC NOT NULL CHECK (monthly_limit > 0),
    month INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    year INTEGER NOT NULL CHECK (year >= 2000),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE (user_id, category, month, year)
);

CREATE INDEX IF NOT EXISTS idx_budgets_user_period 
    ON budgets(user_id, year, month);

-- 4. Milestone Goals Table
CREATE TABLE IF NOT EXISTS goals (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    name TEXT NOT NULL,
    target_amount NUMERIC NOT NULL CHECK (target_amount > 0),
    current_amount NUMERIC NOT NULL DEFAULT 0.0 CHECK (current_amount >= 0),
    target_date TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_goals_user 
    ON goals(user_id);
