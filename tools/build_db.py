#!/usr/bin/env python3
"""Builds app/src/main/assets/dictionary.db from several open dictionaries.

Sources (each one is a function that yields rows; add your own to SOURCES):
  wordnet    Princeton WordNet 3.0      definitions, examples, synonyms, word forms
  webster    Webster's 1913 (public domain)   ~100k headwords, rich older vocabulary
  cmudict    CMU Pronouncing Dictionary       pronunciations (shown as IPA)
  wiktionary Wiktionary via kaikki.org        optional, big: pass --wiktionary

Everything is downloaded into --cache the first time, so re-runs are offline.
Usage: python3 tools/build_db.py [--cache DIR] [--out FILE] [--wiktionary]
"""
import argparse
import gzip
import io
import json
import os
import re
import sqlite3
import sys
import urllib.request
import zipfile
from collections import defaultdict

RAW = "https://raw.githubusercontent.com"
URLS = {
    "wordnet": f"{RAW}/nltk/nltk_data/gh-pages/packages/corpora/wordnet.zip",
    "webster": f"{RAW}/matthewreagan/WebstersEnglishDictionary/master/dictionary.json",
    "cmudict": f"{RAW}/cmusphinx/cmudict/master/cmudict.dict",
    "wiktionary": "https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl",
}

# Source codes stored in the database; the app maps them to display names.
WORDNET, WEBSTER, WIKTIONARY = "wn", "web", "wikt"

POS = {"n": "noun", "v": "verb", "a": "adjective", "s": "adjective", "r": "adverb"}
POS_FILES = {"noun": "n", "verb": "v", "adj": "a", "adv": "r"}

ARPA_TO_IPA = {
    "AA": "ɑ", "AE": "æ", "AH": "ʌ", "AO": "ɔ", "AW": "aʊ", "AY": "aɪ", "B": "b", "CH": "tʃ",
    "D": "d", "DH": "ð", "EH": "ɛ", "ER": "ɝ", "EY": "eɪ", "F": "f", "G": "ɡ", "HH": "h",
    "IH": "ɪ", "IY": "i", "JH": "dʒ", "K": "k", "L": "l", "M": "m", "N": "n", "NG": "ŋ",
    "OW": "oʊ", "OY": "ɔɪ", "P": "p", "R": "ɹ", "S": "s", "SH": "ʃ", "T": "t", "TH": "θ",
    "UH": "ʊ", "UW": "u", "V": "v", "W": "w", "Y": "j", "Z": "z", "ZH": "ʒ",
}


def fetch(name, cache):
    os.makedirs(cache, exist_ok=True)
    path = os.path.join(cache, os.path.basename(URLS[name]))
    if not os.path.exists(path):
        print(f"downloading {URLS[name]}", file=sys.stderr)
        urllib.request.urlretrieve(URLS[name], path + ".part")
        os.replace(path + ".part", path)
    return path


def clean(text):
    return re.sub(r"\s+", " ", text).strip()


# --- WordNet ---------------------------------------------------------------

def wordnet(cache):
    """Yields (word, source, pos, definition, example, synonyms) and fills forms/common."""
    zf = zipfile.ZipFile(fetch("wordnet", cache))
    root = next(n for n in zf.namelist() if n.endswith("data.noun")).rsplit("data.noun", 1)[0]
    read = lambda n: zf.read(root + n).decode("utf-8").splitlines()

    for fname, code in POS_FILES.items():
        for line in read(f"data.{fname}"):
            if line.startswith("  "):
                continue
            head, _, gloss = line.partition("|")
            parts = head.split()
            ss_type = parts[2]
            count = int(parts[3], 16)
            lemmas = []
            for i in range(count):
                w = parts[4 + 2 * i]
                w = re.sub(r"\((a|p|ip)\)$", "", w).replace("_", " ")
                lemmas.append(w)
            chunks = [c.strip() for c in gloss.split(";")]
            definition = []
            examples = []
            for c in chunks:
                if c.startswith('"'):
                    examples.append(c.strip('"'))
                elif c:
                    definition.append(c)
            definition = "; ".join(definition)
            example = " / ".join(examples)
            for w in lemmas:
                syn = [x for x in lemmas if x.lower() != w.lower()]
                yield w, WORDNET, POS[ss_type], definition, example, ", ".join(syn)

    forms = {}
    for fname in POS_FILES:
        for line in read(f"{fname}.exc"):
            p = line.split()
            if len(p) >= 2:
                forms[p[0].replace("_", " ")] = p[1].replace("_", " ")
    common = set()
    for line in read("cntlist.rev"):
        p = line.split()
        if len(p) >= 3 and int(p[2]) > 0:
            common.add(p[0].split("%")[0].replace("_", " ").lower())
    wordnet.forms, wordnet.common = forms, common


# --- Webster's 1913 ----------------------------------------------------------

_SENSE = re.compile(r"(?:^|\s)(\d{1,2})\.\s+(?=\S)")


def split_senses(text):
    """Splits '1. foo 2. bar' on consecutive sense numbers only (so '2. 5. 1913' stays whole)."""
    marks, want = [], 1
    for m in _SENSE.finditer(text):
        if int(m.group(1)) == want:
            marks.append(m)
            want += 1
    if not marks:
        return [text]
    out = []
    if text[: marks[0].start()].strip():
        out.append(text[: marks[0].start()])
    for i, m in enumerate(marks):
        end = marks[i + 1].start() if i + 1 < len(marks) else len(text)
        out.append(text[m.end():end])
    return out


def webster(cache):
    data = json.load(open(fetch("webster", cache), encoding="utf-8"))
    for word, text in data.items():
        for sense in split_senses(text):
            sense = clean(sense)
            if sense:
                yield word, WEBSTER, "", sense, "", ""


# --- Wiktionary (optional) ---------------------------------------------------

def wiktionary(cache):
    path = fetch("wiktionary", cache)
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as f:
        for line in f:
            e = json.loads(line)
            if e.get("lang_code") != "en" or " " in e.get("word", ""):
                continue
            for s in e.get("senses", []):
                gl = s.get("glosses")
                if not gl or s.get("form_of") or s.get("alt_of"):
                    continue
                ex = next((x["text"] for x in s.get("examples", []) if x.get("text")), "")
                syn = ", ".join(x["word"] for x in s.get("synonyms", [])[:6])
                yield e["word"], WIKTIONARY, e.get("pos", ""), clean(gl[-1]), clean(ex), syn


# --- CMUdict -----------------------------------------------------------------

def to_ipa(phones):
    out = []
    for ph in phones:
        base, stress = ph.rstrip("012"), ph[-1] if ph[-1] in "012" else ""
        ipa = ARPA_TO_IPA.get(base, base.lower())
        if base == "AH" and stress == "0":
            ipa = "ə"
        elif base == "ER" and stress == "0":
            ipa = "ɚ"
        if stress == "1":
            ipa = "ˈ" + ipa
        elif stress == "2":
            ipa = "ˌ" + ipa
        out.append(ipa)
    return "/" + "".join(out) + "/"


def cmudict(cache):
    prons = {}
    for line in open(fetch("cmudict", cache), encoding="utf-8"):
        w, _, ph = line.strip().partition(" ")
        if not w or re.search(r"\(\d+\)$", w):
            continue  # first pronunciation only
        prons[w] = to_ipa(ph.split())
    return prons


# --- Build -------------------------------------------------------------------

SCHEMA = """
CREATE TABLE words(id INTEGER PRIMARY KEY, word TEXT NOT NULL, lc TEXT NOT NULL, ipa TEXT, common INTEGER NOT NULL);
CREATE TABLE defs(word_id INTEGER NOT NULL, source TEXT NOT NULL, pos TEXT NOT NULL, ord INTEGER NOT NULL,
                  text TEXT NOT NULL, example TEXT NOT NULL, syns TEXT NOT NULL);
CREATE TABLE forms(form TEXT PRIMARY KEY, lemma TEXT NOT NULL) WITHOUT ROWID;
CREATE INDEX defs_word ON defs(word_id);
CREATE INDEX words_lc ON words(lc);
"""


def main():
    ap = argparse.ArgumentParser()
    here = os.path.dirname(os.path.abspath(__file__))
    ap.add_argument("--cache", default=os.path.join(here, ".cache"))
    ap.add_argument("--out", default=os.path.join(here, "..", "app", "src", "main", "assets", "dictionary.db"))
    ap.add_argument("--wiktionary", action="store_true", help="also merge Wiktionary (large download)")
    args = ap.parse_args()

    sources = [wordnet, webster] + ([wiktionary] if args.wiktionary else [])
    entries = defaultdict(list)  # lowercase -> [(display word, source, pos, text, example, syns)]
    for src in sources:
        n = 0
        for row in src(args.cache):
            entries[row[0].lower()].append(row)
            n += 1
        print(f"{src.__name__}: {n} definitions", file=sys.stderr)
    prons = cmudict(args.cache)
    forms, common = wordnet.forms, wordnet.common

    # Keep only the lemma of each surface form that has no entry of its own.
    forms = {f.lower(): l.lower() for f, l in forms.items() if f.lower() not in entries and l.lower() in entries}

    out = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.executescript(SCHEMA)
    order = {WORDNET: 0, WIKTIONARY: 1, WEBSTER: 2}
    for wid, lc in enumerate(sorted(entries), 1):
        rows = entries[lc]
        # Display with the capitalisation WordNet/Wiktionary use (proper nouns), else lowercase.
        word = next((r[0] for r in rows if r[1] != WEBSTER), lc)
        db.execute("INSERT INTO words VALUES(?,?,?,?,?)",
                   (wid, word, lc, prons.get(lc), 1 if lc in common else 0))
        rows.sort(key=lambda r: order[r[1]])  # stable: keeps each source's own sense order
        seen, k = set(), 0
        for _, source, pos, text, example, syns in rows:
            key = (source, pos, text.lower())
            if key in seen:
                continue
            seen.add(key)
            db.execute("INSERT INTO defs VALUES(?,?,?,?,?,?,?)", (wid, source, pos, k, text, example, syns))
            k += 1
    db.executemany("INSERT INTO forms VALUES(?,?)", forms.items())
    db.commit()
    db.execute("VACUUM")
    db.close()
    words = sqlite3.connect(out).execute("SELECT COUNT(*) FROM words").fetchone()[0]
    print(f"wrote {out}: {words} words, {os.path.getsize(out) / 1e6:.1f} MB", file=sys.stderr)


if __name__ == "__main__":
    main()
