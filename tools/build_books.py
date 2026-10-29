#!/usr/bin/env python3
"""Builds app/src/main/assets/library.db: the pocket library of public-domain books.

Downloads each book listed in tools/books.txt from Project Gutenberg, strips the
licence header and footer, re-flows the text into paragraphs, splits it into chapters,
and stores it all in one SQLite file. Titles and authors come from the books themselves.

Usage: python3 tools/build_books.py [--cache DIR] [--out FILE] [--only ID ...]
Books already in --cache (as books/<id>.txt) are not downloaded again.
"""
import argparse
import os
import re
import sqlite3
import sys
import time
import urllib.request

URLS = [
    "https://www.gutenberg.org/cache/epub/{id}/pg{id}.txt",
    "https://www.gutenberg.org/files/{id}/{id}-0.txt",
    "https://www.gutenberg.org/ebooks/{id}.txt.utf-8",
]
MIN_SUCCESS = 0.8  # fail the build if fewer than this share of the books could be fetched

START = re.compile(r"\*\*\* ?START OF (?:THE|THIS) PROJECT GUTENBERG EBOOK.*?\*\*\*", re.I | re.S)
END = re.compile(r"\*\*\* ?END OF (?:THE|THIS) PROJECT GUTENBERG EBOOK", re.I)
NUMBER = r"(?:[IVXLC]+|\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|first|second|third|fourth|fifth|last)"
HEADING = re.compile(
    rf"^(?:(?:chapter|book|part|act|stave|letter|canto|volume|section)\s+(?:the\s+)?{NUMBER}\b.{{0,60}}"
    r"|preface|prologue|epilogue|introduction|conclusion"
    r"|(?:[IVXLC]{1,7}|\d{1,3})\.(?:\s+[^.!?]{0,50})?)$",
    re.I,
)
MIN_CHAPTER_CHARS = 200
FALLBACK_CHUNK = 12000


def fetch(book_id, cache):
    path = os.path.join(cache, "books", f"{book_id}.txt")
    if os.path.exists(path):
        return open(path, encoding="utf-8", errors="replace").read()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    for url in (u.format(id=book_id) for u in URLS):
        for attempt in range(2):
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "offline-dictionary-android build (github)"})
                data = urllib.request.urlopen(req, timeout=60).read().decode("utf-8", errors="replace")
                if START.search(data) or "Project Gutenberg" in data[:3000]:
                    with open(path, "w", encoding="utf-8") as f:
                        f.write(data)
                    time.sleep(0.5)  # be polite to the server
                    return data
            except Exception as e:  # try the next URL
                print(f"  {url}: {e}", file=sys.stderr)
                time.sleep(1.5)
    return None


def meta(raw):
    head = raw[:6000]
    title = re.search(r"^Title:\s*(.+?)\s*(?:\n\s*\n|\nAuthor:)", head, re.M | re.S)
    author = re.search(r"^Author:\s*(.+?)\s*$", head, re.M)
    if not title:  # older files: "The Project Gutenberg EBook of Title, by Author"
        m = re.search(r"EBook of (.+?),\s+by\s+(.+?)\s*$", head, re.M)
        return (m.group(1), m.group(2)) if m else (None, None)
    t = re.sub(r"\s+", " ", title.group(1)).strip()
    a = author.group(1).strip() if author else ""
    if "," in a and a.count(",") == 1:  # "Austen, Jane" -> "Jane Austen"
        last, first = [x.strip() for x in a.split(",")]
        a = f"{first} {last}"
    return t, a


def body(raw):
    raw = raw.replace("\r\n", "\n").replace("\r", "\n").lstrip("﻿")
    m = START.search(raw)
    if m:
        raw = raw[m.end():]
    e = END.search(raw)
    if e:
        raw = raw[: e.start()]
    return raw


def paragraphs(text):
    """Blank-line separated blocks. Prose is re-flowed to one line; verse and plays keep their line breaks."""
    out = []
    for block in re.split(r"\n\s*\n", text):
        lines = [l.strip() for l in block.split("\n") if l.strip()]
        if not lines:
            continue
        lines = [re.sub(r"_([^_]+)_", r"\1", l).replace("_", "") for l in lines]
        avg = sum(len(l) for l in lines) / len(lines)
        if len(lines) > 2 and avg < 45:
            out.append("\n".join(lines))
        else:
            out.append(re.sub(r"\s+", " ", " ".join(lines)))
    return out


def is_heading(p):
    return "\n" not in p and len(p) <= 70 and bool(HEADING.match(p))


def chapters(paras):
    """[(title, [paragraphs])]. A heading line starts a chapter; a short, unpunctuated line after it joins the title."""
    chaps, cur = [], ("Beginning", [])
    i = 0
    while i < len(paras):
        p = paras[i]
        if is_heading(p):
            title = p
            nxt = paras[i + 1] if i + 1 < len(paras) else ""
            if nxt and "\n" not in nxt and len(nxt) <= 60 and not nxt.endswith((".", ",", ";", ":", "?", "!", '"')) and not is_heading(nxt):
                title += ". " + nxt
                i += 1
            chaps.append(cur)
            cur = (title, [])
        else:
            cur[1].append(p)
        i += 1
    chaps.append(cur)

    # A heading with next to nothing under it is a table-of-contents line; fold it back into the text.
    merged = []
    for title, ps in chaps:
        if sum(len(x) for x in ps) < MIN_CHAPTER_CHARS and merged:
            merged[-1][1].extend([title] + ps)
        elif sum(len(x) for x in ps) < MIN_CHAPTER_CHARS and not merged:
            merged.append((title, ps))
        else:
            merged.append((title, ps))
    merged = [c for c in merged if c[1]]
    if len(merged) > 1 and merged[0][0] == "Beginning" and sum(len(x) > 150 for x in merged[0][1]) < 3:
        merged = merged[1:]  # title page, contents and illustration lists, not prose to read
    if len(merged) < 2:  # no headings found: cut into evenly sized parts
        flat = [p for _, ps in merged for p in ps]
        merged, cur, size = [], [], 0
        for p in flat:
            cur.append(p)
            size += len(p)
            if size >= FALLBACK_CHUNK:
                merged.append((f"Part {len(merged) + 1}", cur))
                cur, size = [], 0
        if cur:
            merged.append((f"Part {len(merged) + 1}", cur))
    return merged


SCHEMA = """
CREATE TABLE books(id INTEGER PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, chapters INTEGER NOT NULL, words INTEGER NOT NULL,
                  start INTEGER NOT NULL);
CREATE TABLE chapters(book_id INTEGER NOT NULL, idx INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, PRIMARY KEY(book_id, idx)) WITHOUT ROWID;
"""


MAIN_CHAPTER = re.compile(
    rf"^(?:(?:chapter|book|part|act|stave|letter|canto|volume|section)\s+(?:the\s+)?{NUMBER}\b|(?:[IVXLC]{{1,7}}|\d{{1,3}})\.)",
    re.I,
)


def story_start(chaps):
    """Index of the first real chapter, so a book opens on the story and not on its preface or introduction."""
    return next((i for i, (title, _) in enumerate(chaps) if MAIN_CHAPTER.match(title)), 0)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=os.path.join(here, ".cache"))
    ap.add_argument("--out", default=os.path.join(here, "..", "app", "src", "main", "assets", "library.db"))
    ap.add_argument("--list", default=os.path.join(here, "books.txt"))
    ap.add_argument("--only", nargs="*", type=int, help="only these ebook numbers (for testing)")
    args = ap.parse_args()

    wanted = []
    for line in open(args.list, encoding="utf-8"):
        line = line.split("#")[0].strip()
        if line:
            num, _, expected = line.partition("|")
            wanted.append((int(num), expected.strip()))
    if args.only:
        wanted = [w for w in wanted if w[0] in args.only] or [(i, "") for i in args.only]

    out = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.executescript(SCHEMA)

    ok = 0
    for num, expected in wanted:
        raw = fetch(num, args.cache)
        if raw is None:
            print(f"#{num} {expected}: could not download", file=sys.stderr)
            continue
        title, author = meta(raw)
        if not title:
            print(f"#{num} {expected}: no title found in the file, skipped", file=sys.stderr)
            continue
        if expected and expected.split()[-1].lower().strip(".,'") not in title.lower():
            print(f"#{num}: expected '{expected}' but the file says '{title}' (kept)", file=sys.stderr)
        chaps = chapters(paragraphs(body(raw)))
        words = sum(len(p.split()) for _, ps in chaps for p in ps)
        if words < 1000:
            print(f"#{num} {title}: only {words} words, skipped", file=sys.stderr)
            continue
        start = story_start(chaps)
        db.execute("INSERT INTO books VALUES(?,?,?,?,?,?)", (num, title, author, len(chaps), words, start))
        db.executemany("INSERT INTO chapters VALUES(?,?,?,?)",
                       [(num, i, t, "\n\n".join(ps)) for i, (t, ps) in enumerate(chaps)])
        ok += 1
        print(f"#{num} {title} — {author}: {len(chaps)} chapters, {words} words; opens at '{chaps[start][0]}' (#{start})", file=sys.stderr)
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"wrote {out}: {ok}/{len(wanted)} books, {os.path.getsize(out) / 1e6:.1f} MB", file=sys.stderr)
    if ok < len(wanted) * MIN_SUCCESS:
        sys.exit(f"only {ok} of {len(wanted)} books could be built")


if __name__ == "__main__":
    main()
