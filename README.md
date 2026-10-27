# Budík

Moderní budík pro Android (Kotlin, Jetpack Compose, Material You).

## Funkce

- Libovolný počet nezávislých budíků – dny v týdnu, jednorázový budík (i na konkrétní datum), přeskočení příštího zvonění, duplikace
- **Dny volna** – vestavěné české státní svátky (offline) + vybrané kalendáře v telefonu (např. Google kalendář „Dovolená“) s volitelným filtrem podle klíčových slov
- Vyzvánění – systémové zvuky nebo vlastní audio soubor, vibrace, postupné zesilování hlasitosti
- Automatické ztišení po N minutách → událost *zmeškáno* + notifikace
- Obrazovka zvonění přes zamčenou obrazovku; vypnutí vodorovným posuvníkem, svislým posuvníkem nebo tlačítkem
- **Integrace** při zazvonění / vypnutí / zmeškání: Android broadcast intent (Tasker…), webhook (HTTP), MQTT
  - odesílá se okamžitě a paralelně; při selhání sítě se zpráva zařadí do fronty a odešle po obnovení připojení (s původním časem události)
- Čeština a angličtina

## Integrace

Zástupné symboly v URL, hlavičkách, těle, MQTT topicu a zprávě:
`{event}` (`ringing` / `dismissed` / `missed` / `test`), `{alarm_id}`, `{label}`, `{alarm_time}`, `{timestamp}` (ms), `{time}` (ISO 8601), `{json}`.

`{json}` vypadá takto:

```json
{"event":"ringing","alarm_id":1,"label":"Práce","alarm_time":"06:30","timestamp":1793080200000,"time":"2026-10-27T06:30:00+01:00"}
```

Broadcast intent má výchozí akci `io.github.pkejval.budik.ALARM_EVENT` a extras `event`, `alarm_id`, `label`, `alarm_time`, `timestamp`, `time`.

## Instalace přes Obtainium

Přidejte aplikaci URL `https://github.com/pkejval/Budik`. Každý push do `main` vydá nový podepsaný release.

## Build

```
./gradlew assembleRelease
```

CI podepisuje release klíčem ze secretů `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
