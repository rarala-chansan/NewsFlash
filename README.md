# NewsFlash

Minecraft 1.21.8 / Paper 向けのニュース速報プラグインです。

外務省 海外安全情報オープンデータと P2P地震情報 WebSocket API を利用し、通知対象のニュースや地震情報をサーバーチャットへ流します。

今後、Jアラートや緊急地震速報なども追加し、総合的なニュースフラッシュプラグインにしていく予定です。

## 主な機能

- 外務省 海外安全情報オープンデータの新着情報を定期取得
- キーワードに一致した海外安全情報だけをチャット通知
- P2P地震情報 WebSocket API から地震情報をリアルタイム受信
- デフォルトでは最大震度4以上の地震情報を通知
- 重複通知を防止
- `/newsflash reload` による設定再読み込み

## 対応環境

- Minecraft: 1.21.8
- Server: Paper
- Java: 21

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
/newsflash reload
```

| コマンド | 説明 |
|---|---|
| `/newsflash status` | 有効な取得元と設定状態を表示します |
| `/newsflash check` | 外務省データを即時チェックします |
| `/newsflash reload` | `config.yml` を再読み込みします |

権限:

```text
newsflash.admin
```

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

## 通知キーワード

`filter.enabled` が `true` の場合、`title + lead + type` に `filter.keywords` のいずれかが含まれるニュースだけを通知します。

```yaml
filter:
  enabled: true
  default-broadcast: false
  keywords:
    - "弾道ミサイル"
    - "邦人"
    - "地震"
```

`filter.enabled: false` にすると全件通知します。
`filter.default-broadcast: true` にすると、キーワードに一致しないニュースも通知します。

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
    include-unknown-scale: false
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

## チャット表示

チャットの接頭辞と本文は `broadcast` で変更できます。

```yaml
broadcast:
  prefix: "<red><bold>[NewsFlash]</bold></red>"
  format: "{prefix} <gold>{source}</gold> <yellow>{title}</yellow> <gray>({date})</gray> <aqua><click:open_url:'{url}'>{url}</click></aqua>"
  console: true
```

使用可能な置換:

```text
{prefix}, {source}, {type}, {title}, {lead}, {keyword}, {date}, {url}
```

表示形式には MiniMessage が使えます。

## データソース

- 外務省 海外安全情報オープンデータ: `https://www.ezairyu.mofa.go.jp/opendata/area/newarrivalL.xml`
- [外務省 海外安全情報オープンデータ 利用マニュアル](https://www.ezairyu.mofa.go.jp/html/opendata/support/usemanual.pdf)
- P2P地震情報 WebSocket API: `wss://api.p2pquake.net/v2/ws`
- [P2P地震情報 JSON API v2 仕様](https://www.p2pquake.net/develop/json_api_v2/)

## 注意

- 外部サービスの仕様変更、障害、通信断により通知できない場合があります。
- 外務省データとP2P地震情報の利用条件は、それぞれの提供元の条件に従ってください。
- このプラグインの通知は補助情報です。実際の安全判断では、各公的機関の公式発表を確認してください。

## ライセンス

MIT License
