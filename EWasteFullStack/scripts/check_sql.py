#!/usr/bin/env python3
"""Validates every SQL statement in SqliteRepository.java against a real SQLite engine.
Usage: python3 scripts/check_sql.py"""
import re, sqlite3, sys, pathlib

src = pathlib.Path(__file__).resolve().parent.parent / "src/com/ewaste/server/repo/SqliteRepository.java"
text = src.read_text()
# join "a" + "b" concatenations, then pick string literals that look like SQL
lit = r'"((?:[^"\\]|\\.)*)"'
joined = re.findall(lit + r'(?:\s*\+\s*' + lit + r')*', text)
stmts = []
for m in re.finditer(lit + r'(?:\s*\+\s*' + lit + r')*', text):
    parts = re.findall(lit, m.group(0))
    s = "".join(parts)
    if re.match(r'(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER)\b', s):
        stmts.append(s)

db = sqlite3.connect(":memory:")
schema = [s for s in stmts if s.startswith("CREATE")]
alters = [s for s in stmts if s.startswith("ALTER")]
dml = [s for s in stmts if not (s.startswith("CREATE") or s.startswith("ALTER"))]
for s in schema:
    db.execute(s)
bad = 0
for s in dml:
    n = s.count("?")
    # bind text for every parameter; SQLite is dynamically typed so this tests syntax/columns only
    try:
        cur = db.execute(s, ["1"] * n)
        if s.startswith("SELECT"):
            cols = [d[0] for d in cur.description]
    except Exception as e:
        bad += 1
        print("FAIL:", e, "\n   ", s)
# Migrations: must work on a database made by the previous version (users table without the new column)
# and must fail harmlessly ("duplicate column") on a fresh one, which the Java code ignores.
old = sqlite3.connect(":memory:")
users_ddl = next(x for x in schema if "TABLE IF NOT EXISTS users" in x)
old.execute(users_ddl.replace(", email_opt_out INTEGER NOT NULL DEFAULT 0", ""))
assert "email_opt_out" not in {r[1] for r in old.execute("pragma table_info(users)")}
for a in alters:
    old.execute(a)
    try:
        db.execute(a)
        bad += 1; print("FAIL: migration unexpectedly succeeded on a fresh schema")
    except sqlite3.OperationalError as e:
        if "duplicate column" not in str(e):
            bad += 1; print("FAIL:", e)
print(f"{len(alters)} migration(s) verified on an old-format database")
print(f"{len(schema)} schema statements, {len(dml)} queries/updates checked, {bad} failed")

# Column coverage: every column read through rs.getX("name") must exist in its table
cols = {}
for (t,) in db.execute("select name from sqlite_master where type='table'"):
    cols[t] = {r[1] for r in db.execute(f"pragma table_info({t})")}
all_cols = set().union(*cols.values())
used = set(re.findall(r'rs\.get\w+\("([a-z_]+)"\)', text))
missing = sorted(used - all_cols)
print("columns read by mappers missing from schema:", missing or "none")
sys.exit(1 if bad or missing else 0)
