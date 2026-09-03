# Spell Checker

[English](README.md) | **日本語**

指定したキーワードを **含む** 単語を単語リストから探し（大文字小文字を区別しない部分一致）、
各ヒットが何文字余分に持っているかでグループ分けする小さなコマンドラインツールです。

**実行マシンのメモリより大きいキーワードファイル** を `OutOfMemoryError` を起こさずに
処理することを設計目標にしています。

## 何をするか

キーワードを 1 つ渡すと、3 種類の結果を報告します。

| 結果                          | 意味                                                             |
| ----------------------------- | -------------------------------------------------------------- |
| `(match with [...])`          | キーワードを含む、同じ長さの辞書単語                              |
| `(diff by N char from [...])` | キーワードを部分文字列として含む、より長い辞書単語               |
| `(not in dictionary)`         | キーワードを含む辞書単語が存在しない                            |

実行例:

```
$ java -jar spellchecker-1.0.0.jar mango
[INFO] io.github.annupuri.spellchecker.SpellChecker search: Line1: mango (match with [mango])
[INFO] io.github.annupuri.spellchecker.SpellChecker search: Line1: mango (diff by 1 char from [mangos])
[INFO] io.github.annupuri.spellchecker.SpellChecker search: Line1: mango (diff by 2 char from [mangoes, mangold])
[INFO] io.github.annupuri.spellchecker.SpellChecker search: Line1: mango (diff by 3 char from [mangolds, mangonel])
```

## 使い方

```
# 1 つ以上のキーワード
java -jar spellchecker-1.0.0.jar KEYWORD [KEYWORD ...]

# 既存のファイルを 1 つ指定: 各行をスペースで分割し、全トークンをキーワードとして扱う
java -jar spellchecker-1.0.0.jar path/to/keywords.txt
```

引数なしで実行すると使い方を表示して終了します。

### 辞書

単語リストは、次の優先順位で最初に見つかったソースから読み込みます。

1. システムプロパティ `spellchecker.dictionary` が指すファイル
   （`java -Dspellchecker.dictionary=/path/to/words -jar ...`）
2. カレントディレクトリの `words` ファイル
3. `/usr/share/dict/words`（存在する場合）
4. jar に同梱された gzip 圧縮版 `words.gz` — 確実なフォールバック

同梱リスト（`src/main/resources/words.gz`）は約 17.3 万語の
[ENABLE](https://en.wikipedia.org/wiki/Words_with_Friends#ENABLE) 単語リストで、
作者によりパブリックドメインに置かれています。`spellchecker.dictionary` または
`./words` で任意の改行区切り単語ファイルを指定できます。

## ビルド

JDK 21 以上と Maven が必要です。

```
mvn clean package
# -> target/spellchecker-1.0.0.jar
```

## 設計メモ

- **長さによるバケット化。** 辞書は単語長をキーとした
  `ConcurrentSkipListMap<Integer, Set<String>>` で保持します。長さ *n* のキーワードは
  長さ *n* 以上の単語の部分文字列にしかなり得ないため、検索は `tailMap(n)` から始め、
  それより短いバケットはすべてスキップします。
- **ストリーミング入力。** キーワードファイルは 1 行ずつ読み込み、全体をメモリに
  保持しません。これが、巨大なキーワードファイルでもヒープを使い切らずに済む理由です。
  辞書自体は固定サイズのインメモリ索引で、各キーワードの検索結果も辞書サイズ以下に
  有界であり、キーワード間で保持もされません。
- **バケットの並列スキャン。** `Dictionary.search` は候補となる長さバケットを、共有の
  有界ワーカープール（`invokeAll`）でスキャンします。結果は単語長の昇順にマージされます。
  プールサイズの既定値は利用可能なプロセッサ数で、`-Dspellchecker.searchThreads=N` で
  上書きできます（`1` で逐次実行）。
- **単一の自己完結 jar。** 共通ヘルパーは Apache Commons Lang / Commons Collections から、
  ロギングは `java.util.logging` を使います。`mvn package` がこれらを 1 つの実行可能 jar に
  shade します。JUnit のみが test スコープの依存です。

## 既知の制限

- これは部分文字列の包含であって、本来のスペルチェックではありません。編集距離による
  ランキングや「もしかして」候補の提示はありません。
- 中核の探索アルゴリズムは Java 8 世代の実装が元ですが、ビルド・依存・並行処理は
  その後見直しています。

挙動チェックリストは [docs/test-spec.md](docs/test-spec.md) を参照してください。

## ライセンス

Apache License 2.0 — [LICENSE](LICENSE) および [NOTICE](NOTICE) を参照。
