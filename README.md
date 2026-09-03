# Spell Checker

**English** | [日本語](README.ja.md)

A small command-line tool that looks up a word list for entries **containing** a
given keyword (case-insensitive substring match), and groups the hits by how many
extra characters each match has.

A design goal is to handle a keyword **file larger than the machine's memory**
without an `OutOfMemoryError`.

## What it does

Given a keyword, it reports three kinds of result:

| Result                          | Meaning                                                            |
| ------------------------------- | ----------------------------------------------------------------- |
| `(match with [...])`            | dictionary words of the same length that contain the keyword      |
| `(diff by N char from [...])`   | longer dictionary words that contain the keyword as a substring   |
| `(not in dictionary)`           | no dictionary word contains the keyword                           |

Example:

```
$ java -jar spellchecker-1.0.0.jar mango
[INFO] io.github.annupuri.spellchecker.SpellChecker search: Line1: mango (match with [mango])
[INFO] io.github.annupuri.spellchecker.SpellChecker search: Line1: mango (diff by 1 char from [mangos])
[INFO] io.github.annupuri.spellchecker.SpellChecker search: Line1: mango (diff by 2 char from [mangoes, mangold])
[INFO] io.github.annupuri.spellchecker.SpellChecker search: Line1: mango (diff by 3 char from [mangolds, mangonel])
```

## Usage

```
# one or more keywords
java -jar spellchecker-1.0.0.jar KEYWORD [KEYWORD ...]

# a single existing file: each line is split on spaces and every token is a keyword
java -jar spellchecker-1.0.0.jar path/to/keywords.txt
```

With no arguments it prints usage and exits.

### Dictionary

The word list is resolved from the first source that is available, in order:

1. the file named by the `spellchecker.dictionary` system property, if set
   (`java -Dspellchecker.dictionary=/path/to/words -jar ...`);
2. a file named `words` in the current working directory;
3. `/usr/share/dict/words`, if it exists;
4. the gzipped `words.gz` bundled in the jar — the guaranteed fallback.

The bundled list (`src/main/resources/words.gz`) is the ~173k-word
[ENABLE](https://en.wikipedia.org/wiki/Words_with_Friends#ENABLE) word list, which
its authors placed in the public domain. Point at any other newline-separated word
file with `spellchecker.dictionary` or `./words`.

## Build

Requires JDK 21+ and Maven.

```
mvn clean package
# -> target/spellchecker-1.0.0.jar
```

## Design notes

- **Length bucketing.** The dictionary is held in a
  `ConcurrentSkipListMap<Integer, Set<String>>` keyed by word length. A keyword of
  length *n* can only be a substring of words of length *n* or greater, so the
  search starts from `tailMap(n)` and skips every shorter bucket.
- **Streaming input.** The keyword file is read line by line and never held in
  memory in full, which is what keeps an oversized keyword file from exhausting the
  heap. The dictionary itself is a fixed in-memory index, and each keyword's result
  is bounded by the dictionary size and not retained between keywords.
- **Parallel bucket scan.** `Dictionary.search` scans the candidate length buckets
  on a bounded, shared worker pool (`invokeAll`), so results still merge in
  ascending word-length order. Pool size defaults to the available processor count;
  override it with `-Dspellchecker.searchThreads=N` (`1` forces sequential).
- **Single self-contained jar.** Common helpers come from Apache Commons Lang and
  Commons Collections, and logging uses `java.util.logging`; `mvn package` shades
  these into one runnable jar. JUnit is the only test-scope dependency.

## Known limitations

- This is substring containment, not real spell checking — there is no edit-distance
  ranking or "did you mean" suggestion.
- The core lookup algorithm dates from a Java 8-era implementation; the build,
  dependencies, and concurrency have since been reworked.

See [docs/test-spec.md](docs/test-spec.md) for the behaviour checklist.

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
