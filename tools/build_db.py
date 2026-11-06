#!/usr/bin/env python3
"""Builds app/src/main/assets/dictionary.db from several open dictionaries.

Sources (each one is a function that yields rows; add your own to SOURCES):
  wordnet    Princeton WordNet 3.0      definitions, examples, synonyms, antonyms, related terms, word forms
  webster    Webster's 1913 (public domain)   ~100k headwords, rich older vocabulary (part of speech inferred, see below)
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
from collections import defaultdict, namedtuple

RAW = "https://raw.githubusercontent.com"
URLS = {
    "wordnet": f"{RAW}/nltk/nltk_data/gh-pages/packages/corpora/wordnet.zip",
    "webster": f"{RAW}/matthewreagan/WebstersEnglishDictionary/master/dictionary.json",
    "cmudict": f"{RAW}/cmusphinx/cmudict/master/cmudict.dict",
    "wiktionary": "https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl",
}

# Source codes stored in the database; the app maps them to display names.
WORDNET, WEBSTER, WIKTIONARY = "wn", "web", "wikt"

# One definition of one word. The last five are comma-separated word lists.
Row = namedtuple("Row", "word source pos text example syns ants similar broader narrower",
                 defaults=("", "", "", "", "", ""))

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
    """Yields Rows and fills wordnet.forms / wordnet.common.

    Besides synonyms (the other words of the same synset), it follows WordNet's pointers
    for antonyms (!), similar adjectives (&), broader terms (@) and narrower terms (~).
    """
    zf = zipfile.ZipFile(fetch("wordnet", cache))
    root = next(n for n in zf.namelist() if n.endswith("data.noun")).rsplit("data.noun", 1)[0]
    read = lambda n: zf.read(root + n).decode("utf-8").splitlines()
    file_of = {"n": "noun", "v": "verb", "a": "adj", "s": "adj", "r": "adv"}

    synsets = {}  # (file, offset) -> dict
    for fname in POS_FILES:
        for line in read(f"data.{fname}"):
            if line.startswith("  "):
                continue
            head, _, gloss = line.partition("|")
            parts = head.split()
            count = int(parts[3], 16)
            lemmas = [re.sub(r"\((a|p|ip)\)$", "", parts[4 + 2 * i]).replace("_", " ") for i in range(count)]
            i = 4 + 2 * count
            ptrs = []
            for _ in range(int(parts[i])):
                sym, off, pos, st = parts[i + 1:i + 5]
                ptrs.append((sym, (file_of[pos], off), st))
                i += 4
            definition, examples = [], []
            for c in (c.strip() for c in gloss.split(";")):
                if c.startswith('"'):
                    examples.append(c.strip('"'))
                elif c:
                    definition.append(c)
            synsets[(fname, parts[0])] = dict(
                type=parts[2], lemmas=lemmas, ptrs=ptrs,
                text="; ".join(definition), example=" / ".join(examples))

    def related(ss, symbols, limit, skip):
        out = []
        for sym, key, _ in ss["ptrs"]:
            if sym in symbols and key in synsets:
                for w in synsets[key]["lemmas"]:
                    if w.lower() not in skip and w not in out:
                        out.append(w)
        return out[:limit]

    for ss in synsets.values():
        lemmas = ss["lemmas"]
        # A satellite adjective has no antonym of its own; its head adjective's antonyms stand in.
        heads = [synsets[k] for sym, k, _ in ss["ptrs"] if sym == "&" and k in synsets] if ss["type"] == "s" else []
        for n, w in enumerate(lemmas, 1):
            skip = {w.lower()}
            syns = [x for x in lemmas if x.lower() != w.lower()]
            ants = []
            for sym, key, st in ss["ptrs"]:
                if sym == "!" and int(st[:2], 16) == n and key in synsets:
                    ants.append(synsets[key]["lemmas"][int(st[2:], 16) - 1])
            if not ants:
                for h in heads:
                    for sym, key, st in h["ptrs"]:
                        if sym == "!" and key in synsets:
                            ants.append(synsets[key]["lemmas"][int(st[2:], 16) - 1])
                    break
            skip |= {x.lower() for x in syns + ants}
            similar = related(ss, {"&"}, 8, skip)
            broader = related(ss, {"@", "@i"}, 6, skip)
            narrower = related(ss, {"~", "~i"}, 10, skip)
            yield Row(w, WORDNET, POS[ss["type"]], ss["text"], ss["example"],
                      ", ".join(syns), ", ".join(dict.fromkeys(ants)),
                      ", ".join(similar), ", ".join(broader), ", ".join(narrower))

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
                yield Row(word, WEBSTER, "", sense)


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
                syn = ", ".join(x["word"] for x in s.get("synonyms", [])[:8])
                ant = ", ".join(x["word"] for x in s.get("antonyms", [])[:8])
                yield Row(e["word"], WIKTIONARY, e.get("pos", ""), clean(gl[-1]), clean(ex), syn, ant)


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



# --- Part of speech for Webster's 1913 ----------------------------------------
# Only WordNet records a part of speech; the Webster's data has none. These rules read it from how the
# definition is worded ("To …" is a verb, "Of or pertaining to …" an adjective). Checked against the words
# WordNet also has, they agree about 98% of the time; anything unclear is left blank rather than guessed.

LEAD_TAGS = re.compile(r"^(?:\s*(?:\([^)]{1,40}\)|\[[^\]]{1,40}\]))+\s*")
ADJ_START = (
    "of or pertaining", "pertaining to", "relating to", "of or relating", "of or like", "having ", "resembling ",
    "capable of", "incapable of", "like ", "without ", "full of", "situated ", "producing ", "belonging to",
    "containing ", "consisting of", "tending to", "bearing ", "destitute ", "made of", "made up", "serving ",
    "furnished with", "covered with", "formed ", "shaped ", "abounding ", "causing ", "characterized ",
    "growing ", "affected ", "not ", "somewhat ", "free from", "of the nature", "of a ", "of an ", "of the ",
    "able to", "fit for", "fitted ", "suitable ", "prone to", "liable to", "inclined to", "apt to", "easily ",
    "being ", "wanting ", "lacking ", "possessing ", "provided with", "marked ", "composed of", "subject to",
    "that may be", "that can be", "that is ", "that has ", "that have ",
)
NOUN_START = (
    "a ", "an ", "the ", "one who", "one that", "one of", "that which", "any ", "something ", "someone ",
    "act of", "state of", "quality of", "condition of", "process of", "art of", "science of", "study of",
)
# Cross-references: the word takes the part of speech of the word it points to.
XREF = re.compile(r"^(?:see|same as)\s+([a-z][a-z\- ']*?)\s*[.,;]", re.I)
XREF_FORM = re.compile(r"^(pl\.|plural|imp\.(?: & p\. ?p\.)?)\s+of\b", re.I)
# Word endings that almost always mean one part of speech (95%+ of the time among words WordNet also knows).
NOUN_ENDINGS = ("ness", "tion", "ation", "ia", "ism", "ence", "ology", "ity", "ist", "age", "ment")
ADJ_ENDINGS = ("ous", "ical", "less", "able", "ible", "ed", "ial", "ive")


def guess_pos(word, text):
    """Part of speech from the wording of one Webster definition, or "" when it isn't clear."""
    if word.startswith("-") and len(word) > 1:
        return "suffix"
    if word.endswith("-") and len(word) > 1:
        return "prefix"
    t = LEAD_TAGS.sub("", text).strip().lower()
    if not t:
        return ""
    if XREF.match(t):
        return ""  # resolved later, from the word it points to
    m = XREF_FORM.match(t)
    if m:
        return "noun" if m.group(1).lower().startswith("pl") else "verb"
    if t.startswith(("see ", "same as", "alt.", "alternative", "obsolete form", "variant of", "p. p.", "third person",
                     "past tense", "a form of", "an old form", "archaic form", "abbrev", "abbr.", "contr.")):
        return ""
    if t.startswith("to "):
        return "verb"
    if t.startswith(("in a ", "in an ")) and " manner" in t[:80] or t.startswith(("in such a", "in the manner")):
        return "adverb"
    if t.startswith(("an exclamation", "an interjection")):
        return "interjection"
    if t.startswith(ADJ_START):
        return "adjective"
    if t.startswith(NOUN_START):
        return "noun"
    return ""


ADVERB_LEAD = ("by ", "with ", "toward", "towards", "in ", "on ", "at ", "from ", "to a", "to an", "after ", "according",
               "for ", "without ", "into ", "upon ", "under ", "through ")


def ly_adverb(word, text):
    """A word ending in -ly whose definition starts like a prepositional phrase ("By means of …") is an adverb."""
    t = LEAD_TAGS.sub("", text).strip().lower()
    return "adverb" if word.endswith("ly") and len(word) > 4 and t.startswith(ADVERB_LEAD) else ""


def ending_pos(word):
    if word.endswith(NOUN_ENDINGS) and len(word) > 5:
        return "noun"
    if word.endswith(ADJ_ENDINGS) and len(word) > 5:
        return "adjective"
    return ""


def fill_webster_pos(entries):
    """Fills in `pos` on Webster rows (in place, per lowercase headword). Returns how many words stay unlabelled."""
    # 1. the wording of each sense, then the word's ending, then the previous sense of the same word
    for lc, rows in entries.items():
        previous = ""
        for i, r in enumerate(rows):
            if r.source != WEBSTER or r.pos:
                continue
            pos = (guess_pos(lc, r.text) or ly_adverb(lc, r.text) or ending_pos(lc)) if not XREF.match(LEAD_TAGS.sub("", r.text).strip()) else ""
            pos = pos or previous
            rows[i] = r._replace(pos=pos)
            previous = pos or previous
    # 1b. a blank sense takes the part of speech its word's other Webster senses all agree on
    for rows in entries.values():
        agreed = {r.pos for r in rows if r.source == WEBSTER and r.pos}
        if len(agreed) == 1:
            only = next(iter(agreed))
            for i, r in enumerate(rows):
                if r.source == WEBSTER and not r.pos and not XREF.match(LEAD_TAGS.sub("", r.text).strip()):
                    rows[i] = r._replace(pos=only)
    # 2. "See X." takes the part of speech X has, if it has just one
    def pos_of(lc):
        found = {r.pos for r in entries.get(lc, []) if r.pos}
        return found.pop() if len(found) == 1 else ""

    for _ in range(2):  # twice, so a chain of cross-references settles
        for lc, rows in entries.items():
            for i, r in enumerate(rows):
                if r.source == WEBSTER and not r.pos:
                    m = XREF.match(LEAD_TAGS.sub("", r.text).strip())
                    if m:
                        rows[i] = r._replace(pos=pos_of(m.group(1).strip().lower()))
    return sum(1 for rows in entries.values() if all(not r.pos for r in rows))


# --- Build -------------------------------------------------------------------

SCHEMA = """
CREATE TABLE words(id INTEGER PRIMARY KEY, word TEXT NOT NULL, lc TEXT NOT NULL, ipa TEXT, common INTEGER NOT NULL);
CREATE TABLE defs(word_id INTEGER NOT NULL, source TEXT NOT NULL, pos TEXT NOT NULL, ord INTEGER NOT NULL,
                  text TEXT NOT NULL, example TEXT NOT NULL, syns TEXT NOT NULL, ants TEXT NOT NULL,
                  similar TEXT NOT NULL, broader TEXT NOT NULL, narrower TEXT NOT NULL);
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
    entries = defaultdict(list)  # lowercase -> [Row]
    for src in sources:
        n = 0
        for row in src(args.cache):
            entries[row.word.lower()].append(row)
            n += 1
        print(f"{src.__name__}: {n} definitions", file=sys.stderr)
    unlabelled = fill_webster_pos(entries)
    print(f"part of speech: {len(entries) - unlabelled} of {len(entries)} words labelled", file=sys.stderr)
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
        word = next((r.word for r in rows if r.source != WEBSTER), lc)
        db.execute("INSERT INTO words VALUES(?,?,?,?,?)",
                   (wid, word, lc, prons.get(lc), 1 if lc in common else 0))
        rows.sort(key=lambda r: order[r.source])  # stable: keeps each source's own sense order
        seen, k = set(), 0
        for r in rows:
            key = (r.source, r.pos, r.text.lower())
            if key in seen:
                continue
            seen.add(key)
            db.execute("INSERT INTO defs VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                       (wid, r.source, r.pos, k, r.text, r.example, r.syns, r.ants, r.similar, r.broader, r.narrower))
            k += 1
    db.executemany("INSERT INTO forms VALUES(?,?)", forms.items())
    db.commit()
    db.execute("VACUUM")
    db.close()
    words = sqlite3.connect(out).execute("SELECT COUNT(*) FROM words").fetchone()[0]
    print(f"wrote {out}: {words} words, {os.path.getsize(out) / 1e6:.1f} MB", file=sys.stderr)


if __name__ == "__main__":
    main()
