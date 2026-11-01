#!/usr/bin/env python3
"""
Builds one Mermaid ER diagram per service from its Flyway migrations.

Reads services/<svc>/src/main/resources/db/migration/V*.sql in version order and replays
CREATE TABLE, ALTER TABLE (ADD/DROP COLUMN, ADD CONSTRAINT ... FOREIGN KEY) and DROP TABLE, so the
diagram is the schema as it stands after the last migration, not the first one.

    python deploy/scripts/generate-erd.py [output-dir]      (default: docs/diagrams/db)

Writes <service>.mmd for each service that has migrations. It does not need a database.
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs", "diagrams", "db")
CONSTRAINT_RE = re.compile(r"^\s*(constraint|primary\s+key|foreign\s+key|unique|check|exclude|like)\b", re.I)


def strip_comments(sql):
    sql = re.sub(r"/\*.*?\*/", " ", sql, flags=re.S)
    return re.sub(r"--[^\n]*", " ", sql)


def split_top(text, sep=","):
    """Split on `sep` outside parentheses and quotes."""
    parts, depth, cur, quote = [], 0, [], None
    for ch in text:
        if quote:
            cur.append(ch)
            if ch == quote:
                quote = None
            continue
        if ch in "'\"":
            quote = ch
            cur.append(ch)
        elif ch == "(":
            depth += 1
            cur.append(ch)
        elif ch == ")":
            depth -= 1
            cur.append(ch)
        elif ch == sep and depth == 0:
            parts.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    if "".join(cur).strip():
        parts.append("".join(cur).strip())
    return parts


def ident(s):
    return s.strip().strip('"').lower().split(".")[-1]


def base_type(rest):
    m = re.match(r"\s*([a-zA-Z_][a-zA-Z0-9_ ]*?)\s*(?:\(|$|\s(?:not|null|default|primary|references|unique|check|generated|constraint)\b)", rest + " ", re.I)
    t = (m.group(1) if m else rest.split()[0]).lower()
    t = re.sub(r"\s+", "_", t.strip())
    return {"character_varying": "varchar", "timestamp_with_time_zone": "timestamptz",
            "double_precision": "double"}.get(t, t) or "text"


class Table:
    def __init__(self, name):
        self.name = name
        self.cols = {}   # name -> {"type", "pk", "fk", "uk", "null"}
        self.order = []
        self.fks = []    # (column, parent_table)

    def add_col(self, name, rest):
        low = rest.lower()
        col = {"type": base_type(rest), "pk": "primary key" in low or "bigserial" in low and False,
               "uk": " unique" in " " + low, "fk": False}
        if "primary key" in low:
            col["pk"] = True
        if name not in self.cols:
            self.order.append(name)
        self.cols[name] = col
        m = re.search(r"references\s+([\"\w.]+)", rest, re.I)
        if m:
            self.fks.append((name, ident(m.group(1))))
            col["fk"] = True

    def drop_col(self, name):
        self.cols.pop(name, None)
        if name in self.order:
            self.order.remove(name)
        self.fks = [f for f in self.fks if f[0] != name]


def apply_table_constraint(t, text):
    low = text.lower()
    m = re.search(r"primary key\s*\(([^)]*)\)", low)
    if m:
        for c in m.group(1).split(","):
            if c.strip().strip('"') in t.cols:
                t.cols[c.strip().strip('"')]["pk"] = True
    m = re.search(r"foreign key\s*\(([^)]*)\)\s*references\s+([\"\w.]+)", text, re.I)
    if m:
        for c in m.group(1).split(","):
            c = ident(c)
            t.fks.append((c, ident(m.group(2))))
            if c in t.cols:
                t.cols[c]["fk"] = True
    m = re.search(r"unique\s*\(([^)]*)\)", low)
    if m and "," not in m.group(1):
        c = m.group(1).strip().strip('"')
        if c in t.cols:
            t.cols[c]["uk"] = True


def replay(files):
    tables = {}
    for path in files:
        sql = strip_comments(open(path, encoding="utf-8").read())
        for stmt in split_top(sql, ";"):
            s = stmt.strip()
            low = s.lower()
            m = re.match(r"create\s+table\s+(?:if\s+not\s+exists\s+)?([\"\w.]+)\s*\((.*)\)\s*$", s, re.I | re.S)
            if m:
                t = Table(ident(m.group(1)))
                tables[t.name] = t
                items = split_top(m.group(2))
                for item in items:
                    if CONSTRAINT_RE.match(item):
                        continue
                    name, _, rest = item.strip().partition(" ")
                    t.add_col(ident(name), rest)
                for item in items:
                    if CONSTRAINT_RE.match(item):
                        apply_table_constraint(t, item)
                continue
            m = re.match(r"drop\s+table\s+(?:if\s+exists\s+)?([\"\w.,\s]+?)(?:\s+cascade)?$", s, re.I)
            if m:
                for n in m.group(1).split(","):
                    tables.pop(ident(n), None)
                continue
            m = re.match(r"alter\s+table\s+(?:only\s+)?(?:if\s+exists\s+)?([\"\w.]+)\s+(.*)$", s, re.I | re.S)
            if m and ident(m.group(1)) in tables:
                t = tables[ident(m.group(1))]
                for action in split_top(m.group(2)):
                    a = action.strip()
                    am = re.match(r"add\s+(?:column\s+)?(?:if\s+not\s+exists\s+)?(.*)$", a, re.I | re.S)
                    if am and not re.match(r"(constraint|primary|foreign|unique|check)\b", am.group(1), re.I):
                        name, _, rest = am.group(1).strip().partition(" ")
                        t.add_col(ident(name), rest)
                        continue
                    if re.match(r"add\s+(constraint|primary|foreign|unique)", a, re.I):
                        apply_table_constraint(t, a)
                        continue
                    dm = re.match(r"drop\s+column\s+(?:if\s+exists\s+)?([\"\w]+)", a, re.I)
                    if dm:
                        t.drop_col(ident(dm.group(1)))
                        continue
                    rm = re.match(r"rename\s+column\s+([\"\w]+)\s+to\s+([\"\w]+)", a, re.I)
                    if rm and ident(rm.group(1)) in t.cols:
                        old, new = ident(rm.group(1)), ident(rm.group(2))
                        t.cols[new] = t.cols.pop(old)
                        t.order[t.order.index(old)] = new
                        t.fks = [(new if c == old else c, p) for c, p in t.fks]
                        continue
                    tm = re.match(r"alter\s+column\s+([\"\w]+)\s+(?:set\s+data\s+)?type\s+(.*)$", a, re.I)
                    if tm and ident(tm.group(1)) in t.cols:
                        t.cols[ident(tm.group(1))]["type"] = base_type(tm.group(2))
    return tables


def render(service, tables):
    lines = ["erDiagram"]
    for t in sorted(tables.values(), key=lambda x: x.name):
        lines.append(f"    {t.name} {{")
        for c in t.order:
            col = t.cols[c]
            keys = [k for k, on in (("PK", col["pk"]), ("FK", col["fk"]), ("UK", col["uk"])) if on]
            lines.append(f"        {col['type']} {c}" + (" " + ",".join(keys) if keys else ""))
        lines.append("    }")
    seen = set()
    for t in sorted(tables.values(), key=lambda x: x.name):
        for col, parent in t.fks:
            if parent in tables and (parent, t.name, col) not in seen:
                seen.add((parent, t.name, col))
                lines.append(f'    {parent} ||--o{{ {t.name} : "{col}"')
    return "\n".join(lines) + "\n"


def version_key(name):
    m = re.match(r"V(\d+)__", name)
    return int(m.group(1)) if m else 0


def main():
    os.makedirs(OUT, exist_ok=True)
    services = os.path.join(ROOT, "services")
    for svc in sorted(os.listdir(services)):
        mig = os.path.join(services, svc, "src", "main", "resources", "db", "migration")
        if not os.path.isdir(mig):
            continue
        files = sorted((f for f in os.listdir(mig) if f.endswith(".sql")), key=version_key)
        if not files:
            continue
        tables = replay([os.path.join(mig, f) for f in files])
        name = svc.replace("-service", "")
        with open(os.path.join(OUT, name + ".mmd"), "w", encoding="utf-8", newline="\n") as fh:
            fh.write(render(svc, tables))
        print(f"{name}: {len(tables)} tables from {len(files)} migrations")


if __name__ == "__main__":
    main()
