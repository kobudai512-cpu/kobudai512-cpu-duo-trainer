# DUO Trainer — 引き継ぎメモ

DUO 3.0 の例文を覚えるための**自分用** Android アプリ（Kotlin / Jetpack Compose / Material 3）。
ユーザーとは日本語でやりとりする。説明は短く、具体例つきで。

## 大事な約束
- **DUO の例文・訳・音声はリポジトリに入れない**（著作権）。データは端末で txt と音声フォルダを読み込む。
  `.gitignore` で `*.mp3` `/data/` `DUO*.txt` を除外している。テストは自作のサンプル例文だけを使う。
- 本番の判定は**和文英訳だけ**（入力あり `FREE` / 入力なし `SELF`）。例文の仕分け・「今日の復習」はこの結果だけで決まる。
  穴埋め・語順パズル・ディクテーション・シャドーイング・単語テストは**練習**（記録は `p:MODE:id` に別保存、どこにも使っていない。評価は 苦手/正解 だけ、次回日付は表示しない）。
- 答え合わせ（core/TextUtil.diff）は大文字小文字・句読点・短縮形を無視。he's = he is / he has、I'd = I would / I had、let's = let us のどちらで書いても正解（"is|has" のような候補つきの語で比べる）。John's のような所有格は展開しない。
- 評価は 苦手 / 難しい / 正解 / 簡単（`Rating.WEAK/HARD/GOOD/MASTERED`）。自信度は わからない / たぶん / 自信あり（押すと即答え表示）。

## 復習のしくみ（core/Srs.kt）
- 間隔 = 前回の間隔 × ease（初期 2.5、1.3〜3.0）。正解を続けると 1 → 3 → 8 → 20 → 50 日…（最大 180）。
- 苦手: 翌日、ease −0.2。**同じ回の 4 問あとにもう一度**（`Question.relearn`、記録は変えない）。ユーザーはこれを残したい。
- 難しい: 前回 × 1.2（最低 +1 日）、ease −0.15。簡単: × 1.3、ease +0.15、最短 4 日。
- 自信度: わからない ×0.7 & ease −0.1、自信あり ×1.15。
- **期限前の復習**は実際に空いた日数で計算し、元の予定日より前に戻さない。**同じ日の 2 回目**は苦手以外なら記録を変えない。
- セクション学習は「新しい文・苦手（今日以外）・期限の来た文」だけ。なければ全部。

## データ（ユーザー側）
- 例文 560・単語 2569・45 セクション。txt は Anki 書き出し（タブ区切り、単語行に `N SECTION` タグ、例文行はタグなし）。
- 例文のセクションは単語との一致で推定（core/DeckParser.kt の Aligner）。単語→例文の対応 2562/2569。
  対応方式を変えたら `Aligner.VERSION` を上げる（起動時に保存済み教材を作り直す）。
- ユーザーの手元の正しい txt は「DUO3.0_修正版5.txt」（誤字修正、Section 44 の順番を本どおりに修正済み。版4 で have endure→endured、No pleasure→No please、版5 で 情報酌量→情状酌量）。
  版5 からアプリで使わない列（[sound:...] の列）と `#separator` `#html` の行を削除。1 行 = 英語 \t 日本語 \t タグ、先頭に `#tags column:3`（パーサーは両方の形式を読める）。
  残っている問題: **No.199 が No.560 の重複**（本の No.199 の文が欠けている。ユーザーから写真をもらえば差し替え）。
- 読み込み直したとき、学習記録と★は英文で照らし合わせて新しい番号に移す（AppViewModel.remapProgress）。
- CD 音声: 1 トラック = 1 セクション。無音で 1 文ずつ分割（core/Segmenter.kt）。冒頭の「Section N」を 1 つ飛ばす、文末 +0.7 秒。
  分割数が例文数と合わないセクションは TTS。分割方式を変えたら `SectionAudio.CURRENT_VERSION` を上げる。

## 画面と機能
ホーム（今日の復習：入力なし/入力ありの 2 ボタン、聞き流し中カード、苦手ランダム、聞き流し、★）、教材（検索、45 セクション）、
セクション詳細（聞き流し・各モード、例文/単語一覧、★、音声の調整）、学習画面、結果、記録（連続日数、7 日間の復習予報、時間・正解率、定着度マップ）、
設定、検索、★ブックマーク、聞き流し（listen/ の前面サービスで画面オフでも再生、通知から操作）。

## ビルドと配布
- GitHub: `kobudai512-cpu/kobudai512-cpu-duo-trainer`。main に push すると Actions がテスト → APK を作り、
  Releases の `latest`（最新ビルド）に `duo-trainer.apk` を置く。ユーザーはそのリンクから上書きインストールする。
  https://github.com/kobudai512-cpu/kobudai512-cpu-duo-trainer/releases/download/latest/duo-trainer.apk
- 署名は `app/debug.keystore` 固定（上書きインストールで記録が消えないように）。変えないこと。
- CI のテスト: core のテスト（必須）、語順パズルのドラッグ UI テスト（必須）、スクリーンショット（失敗可）。
  Robolectric の jar は Maven Central の制限にかかるので、CI では `ROBOLECTRIC_REPO_URL`（Google のミラー）とキャッシュを使う。
- クラウドの作業環境では: Android SDK は自分で入れる必要がある（`dl.google.com` は許可済み、ライセンス同意はユーザーの許可を得てから）。
  Kotlin コンパイラデーモンが落ちるので `./gradlew -Pkotlin.compiler.execution.strategy=in-process ...` を使う。
  Robolectric はローカルでは Maven Central の 429 で動かないことが多い → UI の確認は CI で行う。
- 変更のたびに: core のテストを追加・実行 → `assembleRelease` が通ることを確認 → push → Actions の成功を確認してから報告。

## まだやっていない / 話に出たこと
- 期限を 1 週間以内に前倒しするボタン（ユーザーの返事待ち）。
- 重点範囲（Section 35〜45 など）の設定は「作らなくていい」と言われた。今は「1 日の新しい例文 = 0」＋セクション学習で運用。
- スワイプで評価、リマインダー通知、ウィジェット、iOS 版（Kotlin Multiplatform か PWA）はアイデア段階。
