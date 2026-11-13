# Contributing to Spell Checker

Thank you for your interest in Spell Checker. This file explains what kind of
change fits the project and how to get one merged.

## Scope

Spell Checker is a small command-line tool that finds dictionary words
containing a keyword and groups them by length. A few principles decide whether
a change is accepted:

- **A keyword file larger than memory must not cause an `OutOfMemoryError`.**
  Keyword input is streamed line by line and never held in memory in full.
  Changes that read the whole keyword file at once are not accepted.
- **The jar stays a single self-contained runnable jar** that works on the
  Java release set in `pom.xml` (`maven.compiler.release`, currently 25).
  New runtime dependencies need a good reason; discuss them in an issue first.
- **The dictionary lookup order stays as documented** in the README:
  `spellchecker.dictionary`, `./words`, `/usr/share/dict/words`, then the
  bundled `words.gz`.

For anything larger than a small fix, please open an issue first so we can
agree on the approach before you spend time on it.

## Security issues

Please do not report security problems in a public issue. Use the
**Report a vulnerability** button on the repository's Security tab instead.

## Workflow

1. Fork the repository and create a topic branch from an up-to-date `main`
   (for example `fix/empty-keyword` or `docs/usage-examples`).
2. Make your change, with tests (see below).
3. Run the build and tests locally with JDK 25 or later and Maven:

   ```sh
   mvn -B verify
   ```

4. Open a pull request against `main`. CI (`mvn verify` on JDK 25) must
   pass before the pull request can be merged.

`main` is protected: every change, including the maintainer's own, goes in
through a pull request. Pull requests are merged with "Rebase and merge", so
each of your commits lands on `main` as it is. Please keep them clean; you may
be asked to squash fix-up commits before merging.

Pull requests that touch `CLAUDE.md`, `.claude/`, `.mcp.json`, `.github/`,
`pom.xml` or test code get extra scrutiny, because those files can run code on
the maintainer's machine or in CI (Maven plugins and tests run during the
build). Please explain why such a change is needed in the pull request
description.

## Tests

- A bug fix comes with a test that fails without the fix.
- Tests use JUnit 5 and live under `src/test/java`. Keep them fast and
  independent of the machine: do not rely on `/usr/share/dict/words` or a
  `words` file in the working directory being present or absent.
- User-visible behaviour is listed in `docs/test-spec.md`; update it when the
  behaviour changes.

## Keeping files in step

- **The README is bilingual.** `README.md` has a Japanese twin,
  `README.ja.md`; change both together. If you cannot write Japanese, update
  the English file only and say so in the pull request; the maintainer will
  update the twin.
- **The minimum Java release** appears in `pom.xml`
  (`maven.compiler.release`), the "JDK 25+" line in both READMEs and the CI
  matrix in `.github/workflows/ci.yml`. A change to it updates all of them.

## Commits

- Write messages in English: a short imperative subject ("Fix ...",
  "Add ..."), a blank line, then a body explaining *why*.
- One purpose per commit. Keep refactoring separate from behaviour changes.
- Do not include anything specific to your machine: user names, local paths,
  private hostnames or IP addresses, or unedited log excerpts. Use
  placeholders such as `<you>`.

## License

Spell Checker is distributed under the Apache License 2.0 (see `LICENSE` and
`NOTICE`). By submitting a pull request you agree that your contribution is
licensed under the same terms.
