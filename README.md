# NewsFlash

Paper 1.20.6 以降向けのニュース速報プラグインです。

外務省 海外安全情報オープンデータと P2P地震情報 WebSocket API を利用し、通知対象のニュースや地震情報をサーバーチャットへ流します。

今後、Jアラートや緊急地震速報なども追加し、総合的なニュースフラッシュプラグインにしていく予定です。

## 主な機能

- 外務省 海外安全情報オープンデータの新着情報を定期取得
- キーワードに一致した海外安全情報だけをチャット通知
- P2P地震情報 WebSocket API から地震情報をリアルタイム受信
- デフォルトでは最大震度4以上の地震情報を通知
- 津波注意報・津波警報・大津波警報を通知
- 緊急地震速報（警報）を通知
- 任意のRSS/Atomフィードを設定して通知
- 重複通知を防止
- `/newsflash reload` による設定再読み込み

## 対応環境

- Server: Paper
- Minecraft: 1.20.6 以降
- Java: 21 以降（Minecraft 1.20.6 - 1.21.x）
- Java: 25 以降（Minecraft 26.x）

Paper API 1.20.6 を基準にビルドし、1.20.6 以降の Paper 系サーバーで使える範囲を広く取る方針です。

## ビルド

```bash
mvn package
```

生成物:

```text
target/NewsFlash-0.1.0.jar
```

## 導入

1. `target/NewsFlash-0.1.0.jar` をサーバーの `plugins/` フォルダへ配置します。
2. サーバーを起動します。
3. 生成された `plugins/NewsFlash/config.yml` を必要に応じて編集します。
4. 設定変更後は `/newsflash reload` を実行します。

## コマンド

```text
/newsflash status
/newsflash check
/newsflash check mofa
/newsflash check rss
/newsflash reload
/newsflash reload mofa
/newsflash reload p2pquake
/newsflash reload rss
```

| コマンド | 説明 |
|---|---|
| `/newsflash status` | 有効な取得元と設定状態を表示します |
| `/newsflash check` | 定期取得型の取得元を即時チェックします |
| `/newsflash check mofa` | 外務省データを即時チェックします |
| `/newsflash check rss` | RSS/Atomを即時チェックします |
| `/newsflash reload` | `config.yml` 全体を再読み込みします |
| `/newsflash reload mofa` | 外務省データ設定だけ再読み込みします |
| `/newsflash reload p2pquake` | P2P地震情報のWebSocketを再接続します |
| `/newsflash reload rss` | RSS/Atom設定だけ再読み込みします |

権限:

```text
newsflash.admin
```

## 言語設定

`translation.language` でプラグインが生成する表示文を切り替えられます。

```yaml
translation:
  language: "ja"
```

対応値:

| 値 | 説明 |
|---|---|
| `ja` | 日本語表示 |
| `en` | 英語表示 |
| `zh_CN` | 簡体字中国語ファイル |
| `zh_TW` | 繁体字中国語ファイル |
| `ko` | 韓国語ファイル |
| `de` | ドイツ語ファイル |
| `fr` | フランス語ファイル |
| `es` | スペイン語ファイル |
| `pt_BR` | ブラジルポルトガル語ファイル |
| `ru` | ロシア語ファイル |

翻訳対象は、P2P地震情報の通知ラベル、外務省の配信元名、コマンド応答など、プラグインが生成する文言です。RSS/Atomの記事タイトルや外務省データ本文など、配信元から取得した本文は原文のまま表示します。

初回起動時に以下の翻訳ファイルが生成されます。

```text
plugins/NewsFlash/languages/ja.yml
plugins/NewsFlash/languages/en.yml
plugins/NewsFlash/languages/zh_CN.yml
plugins/NewsFlash/languages/zh_TW.yml
plugins/NewsFlash/languages/ko.yml
plugins/NewsFlash/languages/de.yml
plugins/NewsFlash/languages/fr.yml
plugins/NewsFlash/languages/es.yml
plugins/NewsFlash/languages/pt_BR.yml
plugins/NewsFlash/languages/ru.yml
```

文言を調整したい場合は、対象の言語ファイルを編集して `/newsflash reload` を実行してください。`ja` の未定義キーは日本語に、それ以外の未定義キーは英語にフォールバックします。

## 外務省 海外安全情報

外務省の「新着情報」軽量XMLを定期取得します。

```yaml
mofa:
  enabled: true
  initial-delay-seconds: 60
  poll-interval-minutes: 5
  suppress-initial-broadcast: true
  max-broadcast-per-poll: 5
  seen-history-limit: 1000
```

`suppress-initial-broadcast: true` の場合、初回取得時に既存ニュースを一気に通知せず、既読登録のみ行います。サーバー起動後に新しく追加された情報から通知されます。

## 外務省 通知キーワード

`mofa.filter.enabled` が `true` の場合、外務省データの `title + lead + type` に `mofa.filter.keywords` のいずれかが含まれるニュースだけを通知します。

```yaml
mofa:
  filter:
    enabled: true
    keywords:
      - "ミサイル"
      - "退避"
      - "地震"
```

`mofa.filter.enabled: false` にすると外務省データを全件通知します。`true` の場合はキーワードに一致した外務省ニュースだけ通知します。

## P2P地震情報

P2P地震情報 WebSocket API から気象庁の地震情報をリアルタイム受信します。

```yaml
p2pquake:
  enabled: true
  websocket-url: "wss://api.p2pquake.net/v2/ws"
  reconnect-delay-seconds: 10
  seen-history-limit: 1000

  earthquake:
    enabled: true
    min-scale: 40
    target-prefectures:
      enabled: false
      list:
        # - "東京都"
        # - "神奈川県"
    include-unknown-scale: false

  tsunami:
    enabled: true

  eew:
    enabled: true
    include-tests: false
```

`min-scale` はP2P地震情報 APIの震度コードです。

| 値 | 震度 |
|---:|---|
| `10` | 震度1 |
| `20` | 震度2 |
| `30` | 震度3 |
| `40` | 震度4 |
| `45` | 震度5弱 |
| `50` | 震度5強 |
| `55` | 震度6弱 |
| `60` | 震度6強 |
| `70` | 震度7 |

`target-prefectures.enabled: false` の場合は全国を対象にします。`true` にして都道府県名を設定すると、その地域の観測震度が `min-scale` 以上の場合だけ通知します。

```yaml
p2pquake:
  earthquake:
    min-scale: 40
    target-prefectures:
      enabled: true
      list:
        - "東京都"
        - "神奈川県"
```

津波予報は、津波注意報・津波警報・大津波警報を通知します。解除済みの情報は通知しません。

緊急地震速報（警報）は受信したら通知します。取消情報とテスト配信は初期設定では通知しません。

## RSS/Atom

任意のRSS/Atomフィードを定期取得できます。配信元ごとに利用規約が異なるため、デフォルトでは無効で、URLも同梱していません。

```yaml
rss:
  enabled: false
  poll-interval-minutes: 10
  max-broadcast-per-poll: 5

  feeds:
    # - id: "example"
    #   name: "Example News"
    #   url: "https://example.com/rss.xml"
    #   enabled: true
    #   filter:
    #     enabled: false
    #     keywords:
    #       - "地震"
    #       - "警報"
```

`filter.enabled: false` の場合はそのフィードを全件通知します。`true` にすると、そのフィードの `title + description` に `keywords` のいずれかが含まれる記事だけを通知できます。

## データソース

- 外務省 海外安全情報オープンデータ: `https://www.ezairyu.mofa.go.jp/opendata/area/newarrivalL.xml`
- [外務省 海外安全情報オープンデータ 利用マニュアル](https://www.ezairyu.mofa.go.jp/html/opendata/support/usemanual.pdf)
- P2P地震情報 WebSocket API: `wss://api.p2pquake.net/v2/ws`
- [P2P地震情報 JSON API v2 仕様](https://www.p2pquake.net/develop/json_api_v2/)

日本固有寄りの部分:

- 外務省 海外安全情報: 日本人・日本語利用者向けの海外安全情報です。
- P2P地震情報: 気象庁由来の日本国内向け地震・津波・緊急地震速報を扱います。
- 震度表記、都道府県フィルター: 日本向けの概念です。

汎用部分:

- RSS/Atom: 任意のフィードURLを設定できるため、日本固有ではありません。

## 注意

- 外部サービスの仕様変更、障害、通信断により通知できない場合があります。
- 外務省データとP2P地震情報の利用条件は、それぞれの提供元の条件に従ってください。
- このプラグインの通知は補助情報です。実際の安全判断では、各公的機関の公式発表を確認してください。

## ライセンス

MIT License
