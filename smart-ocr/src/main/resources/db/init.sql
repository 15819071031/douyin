-- SmartOCR SQLite数据库初始化脚本

-- 创建OCR历史记录表
CREATE TABLE IF NOT EXISTS ocr_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    text TEXT,
    image_path TEXT,
    type VARCHAR(50),
    char_count INTEGER DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_ocr_history_create_time ON ocr_history(create_time);
CREATE INDEX IF NOT EXISTS idx_ocr_history_type ON ocr_history(type);
