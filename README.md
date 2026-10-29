# 📖 Offline Dictionary for Android

An English dictionary that works with no internet at all. The app has **no
permissions**, not even network. The whole dictionary (about 200,000 words) is
built into the app, and it merges several open dictionaries so you get more
than one view of each word.

## Sources

| Source | License | What it adds |
| --- | --- | --- |
| [Princeton WordNet 3.0](https://wordnet.princeton.edu/) | WordNet license | Modern definitions, examples, synonyms, and the word-form table used to find base words ("mice" → "mouse") |
| [Webster's Unabridged, 1913](https://github.com/matthewreagan/WebstersEnglishDictionary) | Public domain | ~100,000 headwords, including rarer and older words |
| [CMU Pronouncing Dictionary](https://github.com/cmusphinx/cmudict) | BSD-style | Pronunciations, shown as IPA |
| [Project Gutenberg](https://www.gutenberg.org/) books | Public domain (US) | The texts for the pocket library; the list is `tools/books.txt` |
| [Wiktionary](https://kaikki.org/) (optional) | CC BY-SA | Broad, current coverage. Off by default because it makes the app much bigger; run the **APK** workflow by hand and tick *wiktionary*, or run `tools/build_db.py --wiktionary` |

Each definition on a word's page is labelled with the source it came from,
and you can hide a source with its chip.

## Features

- Instant search-as-you-type, common words first.
- Finds the base word for inflections: *abetted*, *abhorred*, *running*, *cities*.
- "Did you mean…" suggestions for misspelled words.
- Word of the day, a random word button, and recent searches.
- A **pocket library** of about 50 classic books to read offline (Austen, Dickens, Shakespeare, Sherlock Holmes, Alice, Dracula, Frankenstein, Moby Dick, Meditations, The Art of War and more). The reader remembers where you stopped, has chapters, adjustable text size and Night / Sepia / Paper themes, and **tapping any word shows its meaning** right over the page, from the same offline dictionary. "Continue reading" picks you up where you left off.
- The Library tab also keeps your own word collection: keep words on **shelves** you name ("Exam words", "Book club"…), add a private **note** to any word, **browse the whole dictionary A–Z** like a book, and run quick **flashcard reviews** of your shelved words (five dots show how well you know each one). The star is a one-tap save to the "Saved" shelf.
- Save words with the star; share a definition; hear a word read aloud with
  the phone's own text-to-speech voice.
- Synonyms and antonyms under every definition, plus a **Thesaurus** tab: all synonyms and antonyms of the word, then each sense's similar, broader and narrower terms. Tap any word to jump to it.
- Select a word in any app and choose **Define** from the text menu.

## Installing it

Open the [latest build](../../releases/tag/latest), download
**OfflineDictionary.apk** on your phone and open it. Android will ask you to
allow installing from your browser; allow it, then tap **Install**. Play
Protect may warn that the app isn't from the Play Store: tap **More details →
Install anyway**. Requires Android 8.0 or newer. Newer builds install over
older ones and keep your saved words.

## How it's built

```
tools/build_db.py     ──►  app/src/main/assets/dictionary.db  ──►  APK
tools/build_books.py  ──►  app/src/main/assets/library.db     ──►  APK
 (download + merge)         (SQLite, not committed)
```

- `tools/build_db.py` downloads each source, parses it and writes one SQLite
  file. It needs only Python 3. To add a source, write a function that yields
  `Row(word, source_code, pos, text, ...)` rows,
  add it to `sources` in `main()`, and add a label for its code to `Source` in
  `Dictionary.kt`.
- `tools/build_books.py` downloads the books listed in `tools/books.txt` (Gutenberg ebook numbers), strips the licence boilerplate, re-flows the text into paragraphs, splits it into chapters, and writes `library.db`. To add a book, add its number to `books.txt`.
- The app is Kotlin and Jetpack Compose (Material 3). `Dictionary.kt` copies
  the database out of the APK on first launch and answers the queries.

| File | What's in it |
| --- | --- |
| `Dictionary.kt` | Database access: prefix search, lookup, inflections, spelling suggestions, word of the day |
| `UserData.kt` | Shelves, notes, review progress and history |
| `DictionaryViewModel.kt` | Screen state |
| `ui/App.kt` | Search and word screens |
| `Books.kt` | The book library: book list, chapters, reading progress and reader settings |
| `ui/Books.kt` | Covers, the Books page, the reader and the tap-a-word sheet |
| `ui/Library.kt` | The Library tab: shelves, browse A–Z, review |

## Building

GitHub Actions does it on every push (`.github/workflows/apk.yml`): it builds
the database, builds the APK, searches for words on an Android 14 emulator,
and on `main` publishes the APK as the `latest` release.

Locally: `python3 tools/build_db.py` and `python3 tools/build_books.py`, then `./gradlew assembleRelease` with
`ANDROID_HOME` pointing at an Android SDK (or open the folder in Android
Studio).

`signing/sideload.keystore` is committed on purpose so every build installs as
an update over the last. It is public, so only install builds from this
repository's releases. Supply your own key with the `SIGNING_KEYSTORE`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`
environment variables if you publish the app more widely.
