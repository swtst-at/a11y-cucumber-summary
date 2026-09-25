# a11y-cucumber-Summary

Baseline und laufweite Deduplizierung für axe-A11y-Checks in parallelen Cucumber-Läufen
(JUnit Platform, Selenium, Kotlin).

- **Fingerprint** pro axe-Node: `rule | normalisierte Seite | normalisierter Selektor`, als gekürzter SHA-1
- **Baseline** (`a11y-baseline.json`): bekannte Findings mit Ticket und Begründung, versioniert im Repo
- **Registry**: thread-sicher, merkt sich pro Lauf jedes Finding und alle betroffenen Szenarien
- **Summary-Plugin**: schreibt am Ende des Laufs (`TestRunFinished`) einen aggregierten Report

## Inhalt

| Klasse | Aufgabe |
|---|---|
| `A11yCheck` | axe ausführen, Fingerprints bilden, mit Baseline und Registry abgleichen, loggen |
| `Fingerprint` | Normalisierung von Pfad und Selektor, Hash |
| `A11yConfig` | Normalisierungsregeln, Hash-Länge, Ort der Baseline |
| `Baseline` | bekannte Findings laden (Datei oder Classpath) |
| `A11yRegistry` | laufweite Deduplizierung (`ConcurrentHashMap`) |
| `CurrentScenario` + `hooks.A11yScenarioHooks` | aktuelles Szenario pro Thread |
| `A11yReportWriter` | Summary und Baseline-Kandidaten schreiben |
| `Summary` | Cucumber-Plugin, ruft den Writer bei `TestRunFinished` auf |

## Bauen und installieren

Cucumber, axe und slf4j sind `provided`. Zur Laufzeit gelten die Versionen deines Testprojekts.

## Einbinden

```xml
<dependency>
  <groupId>io.secugrow</groupId>
  <artifactId>a11y-cucumber</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

An der Suite-Klasse (oder in `junit-platform.properties`):

```kotlin
@Suite
@IncludeEngines("cucumber")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, io.secugrow.a11y.A11ySummaryPlugin:target/a11y"
)
```

**Wichtig:** Ohne `io.secugrow.a11y.hooks` im Glue laufen die Hooks nicht, und jedes Finding
steht im Report bei Szenario „unbekannt“.

## Verwendung im Step

```kotlin
val result = A11yCheck.scan(driver, page = "AntragBearbeiten")
result.newFindings.forEach { softly.fail(it.describe()) }
```

Parameter von `scan`:

- `page`: fachlicher Seitenname. Empfohlen, vor allem bei JSF/SPA. Ohne Angabe wird der
  normalisierte URL-Pfad genommen.
- `stateKey`: optional. Wenn gesetzt, wird `page + stateKey` nur einmal pro Lauf gescannt.
  Spart Laufzeit, z.B. `stateKey = "initial"`.
- `axe`: eigener `AxeBuilder`, z.B. `AxeBuilder().withTags(listOf("wcag2a", "wcag2aa"))`.

Das Ergebnis bietet:

| Eigenschaft | Bedeutung |
|---|---|
| `newFindings` | nicht in der Baseline. **Deterministisch**, empfohlen für Assertions |
| `newFirstInRun` | neu und im Lauf erstmals gesehen. Welches Szenario das ist, hängt vom Thread-Scheduling ab |
| `knownFindings` | in der Baseline, mit `ticket` |

### Variante ohne rote Szenarien

Wenn der fachliche Lauf gar nicht beeinflusst werden soll: im Step nur `scan(...)` aufrufen,
keine Assertion. Die Findings stehen im Log und im Cucumber-Report. Den Build lässt du in der CI
anhand der Summary scheitern:

```bash
jq -e '.newFindings == 0' target/a11y/a11y-summary.json
```

## Log-Format

Jede Zeile beginnt mit einem festen Prefix, z.B.

```
A11Y-NEW [3f2a9c1e4b7d] label (critical) auf AntragBearbeiten: ["#form\:j_idt{n}"]
A11Y-KNOWN [9c1e4b7d3f2a] color-contrast (serious) auf AntragBearbeiten: ["#submit"] -> SWTST-1234
```

Das wird zusätzlich per `scenario.log(...)` in den Cucumber-Report geschrieben und landet damit
in ReportPortal. Dort kannst du auf `A11Y-KNOWN` per Pattern Analysis filtern.

## Baseline

Standardort: `a11y-baseline.json` als Datei im Arbeitsverzeichnis oder als Classpath-Ressource
(`src/test/resources/a11y-baseline.json`). Überschreibbar mit `-Da11y.baseline=pfad/zur/datei.json`.

```json
[
  {
    "fingerprint": "9c1e4b7d3f2a",
    "key": "color-contrast|AntragBearbeiten|[\"#submit\"]",
    "rule": "color-contrast",
    "page": "AntragBearbeiten",
    "ticket": "SWTST-1234",
    "reason": "Designsystem-Fix geplant Q4"
  }
]
```

Nur `fingerprint` ist Pflicht. Der Rest dient der Lesbarkeit im Review.

**Workflow:** Nach einem Lauf liegt in `target/a11y/a11y-baseline-candidates.json` eine Vorlage
aller neuen Findings. Einträge daraus übernimmst du bewusst per PR in die Baseline, mit Ticket
und Begründung.

## Ausgabe

`target/a11y/` (bzw. das Verzeichnis aus der Plugin-Konfiguration):

- `a11y-summary.json`: alle eindeutigen Findings, neue zuerst, dann nach Häufigkeit; mit allen
  betroffenen Szenarien und `staleBaselineEntries`
- `a11y-baseline-candidates.json`: neue Findings im Baseline-Format

`staleBaselineEntries` sind Baseline-Einträge, die im Lauf nicht vorkamen. Das ist nur bei
Voll-Läufen aussagekräftig; bei gefilterten Läufen (Tags) tauchen dort naturgemäß viele auf.

## Normalisierung anpassen

Vor dem ersten Scan, z.B. in einem `@BeforeAll`-Hook:

```kotlin
A11yConfig.selectorRules += Regex("ember\\d+") to "ember{n}"
A11yConfig.pathRules += Regex("/[A-Z]{2}\\d{6}(?=/|$)") to "/{aktenzahl}"
```

Die Regeln werden der Reihe nach angewendet, die Ersetzung wird wörtlich eingesetzt.

So findest du fehlende Regeln: denselben Lauf zweimal hintereinander ausführen und die
Fingerprints in den beiden `a11y-summary.json` vergleichen. Alles, was sich ohne echte
Änderung unterscheidet, braucht eine Regel.

## Einschränkungen

- Die Registry gilt **pro JVM**. Bei `forkCount > 1` (Surefire) oder `maxParallelForks > 1`
  (Gradle) hat jeder Fork seine eigene Registry, und alle schreiben in dasselbe Verzeichnis.
- Bei mehreren `@Suite`-Klassen kommt `TestRunFinished` mehrfach; der Report wird dann
  mehrfach (kumulativ) geschrieben.
