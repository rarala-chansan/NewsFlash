# NewsFlash

Minecraft 1.21.8 / Paper 向けのニュース速報プラグインです。

現時点では、外務省 海外安全情報オープンデータの「新着情報」軽量XMLを定期取得し、未配信の新着項目をサーバーチャットへ流します。

## Build

```bash
mvn package
```

生成物:

```text
target/NewsFlash-0.1.0.jar
```

## Commands

```text
/newsflash status
/newsflash check
/newsflash reload
```

## Startup Delay

初回の自動取得は `config.yml` の `mofa.initial-delay-seconds` で遅らせられます。

```yaml
mofa:
  initial-delay-seconds: 60
```

`/newsflash check` はこの設定に関係なく即時実行されます。

## Seen History

配信済み判定用の `plugins/NewsFlash/mofa-seen.txt` は `mofa.seen-history-limit` 件まで保持します。

```yaml
mofa:
  seen-history-limit: 1000
```

## Notification Filter

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

## Data Source

外務省 海外安全情報オープンデータ:

```text
https://www.ezairyu.mofa.go.jp/opendata/area/newarrivalL.xml
```

利用マニュアルでは、新着情報は固定URLで提供され、全データは概ね5分間隔で更新されるとされています。
