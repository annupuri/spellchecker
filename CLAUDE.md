# Spell Checker

A small Java command-line tool that finds dictionary words containing a
keyword (case-insensitive substring match) and groups the hits by word length.
A design goal is that a keyword file larger than memory never causes an
`OutOfMemoryError`. See `README.md` for usage and design notes.

## Commands

| Command | What it does |
|---|---|
| `mvn -B verify` | Compile, run the JUnit tests and build the shaded jar. CI runs this on JDK 25 |
| `mvn test` | Run the tests only |
| `mvn clean package` | Build `target/spellchecker-<version>.jar` |

Development uses JDK 25 (`.tool-versions`) and the jar targets Java 25
(`maven.compiler.release` in `pom.xml`). Run `mvn -B verify` before every
commit.

## Principles

- **Stream the keyword input.** A keyword file is read line by line and never
  held in memory in full. Do not introduce anything that collects all
  keywords or all results at once.
- **One self-contained jar.** Runtime dependencies are shaded into the jar;
  ask the maintainer before adding a new one.
- **Keep the dictionary lookup order** documented in the README
  (`spellchecker.dictionary`, `./words`, `/usr/share/dict/words`, bundled
  `words.gz`).

## Keeping files in step

- **The README is bilingual.** `README.md` and `README.ja.md` change
  together. `docs/test-spec.md` is Japanese only.
- **Minimum Java release bumps** touch, together: `maven.compiler.release` in
  `pom.xml`, the "JDK 25+" line in `README*.md` and the CI matrix in
  `.github/workflows/ci.yml`. The development JDK lives in `.tool-versions`;
  keep it within the CI matrix.
- **`CONTRIBUTING.md`** repeats the principles, test rules and commit rules
  in this file for outside contributors; when one of them changes here,
  change it there too. The license is Apache 2.0 (`LICENSE`, `NOTICE`).
- User-visible behaviour is listed in `docs/test-spec.md`; update it when the
  behaviour changes.

## Tests

- A bug fix comes with a test that fails without the fix.
- Tests build their dictionary in memory. Do not depend on
  `/usr/share/dict/words` or a `words` file in the working directory.
- `InputFileCreater` (test sources) generates a huge keyword file for manual
  `OutOfMemoryError` checks; it is not a unit test.

## Do not write

- Anything specific to one person's machine: user names, local paths,
  private hostnames or IP addresses, log excerpts. Use placeholders such as
  `<you>`.
- `tmp/` is a private scratch directory; it is gitignored and never committed.

## Commits

- Messages in English: a short imperative subject, then a body explaining why.
- One purpose per commit. Keep refactoring separate from behaviour changes.
- **Never commit on `main`.** `main` changes only through a pull request.
  Before the first commit of a piece of work, create a topic branch from an
  up-to-date `main`, named after its purpose (`fix/...`, `feat/...`,
  `docs/...`, `refactor/...`, `chore/...`). If you find yourself on `main`
  with uncommitted changes, move them to a new branch before committing.
- Pull requests are merged with "Rebase and merge", so every commit on the
  branch lands on `main` as is. Keep the branch's commits clean.
- **The maintainer pushes and merges.** Claude stops at commit on the topic
  branch and never runs `git push`, `gh pr create`, `gh pr merge` or anything
  else that changes `origin`.
- **Do not check out or run an outside contributor's unmerged branch** (build,
  test, `mvn`, or start a session in it) until the maintainer has reviewed
  its diff, especially changes to `CLAUDE.md`, `.claude/`, `.mcp.json`,
  `.github/`, `pom.xml` and test code. Review such a pull request from its
  diff only.
