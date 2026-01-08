# database.py
import sqlite3
from datetime import datetime

DB_FILE = "crowd.db"


def init_db():
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()

    c.execute("""
        CREATE TABLE IF NOT EXISTS detections (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp TEXT,
            count INTEGER,
            filename TEXT
        )
    """)

    conn.commit()
    conn.close()


def insert_detection(count, filename):
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()

    c.execute(
        "INSERT INTO detections (timestamp, count, filename) VALUES (?, ?, ?)",
        (datetime.now().isoformat(timespec='seconds'), count, filename)
    )

    conn.commit()
    conn.close()


def fetch_history():
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()

    c.execute("""
        SELECT timestamp, count, filename
        FROM detections
        ORDER BY id DESC
    """)

    rows = c.fetchall()
    conn.close()
    return rows
