package at.swtst.a11y

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Erzeugt aus der A11ySummary eine eigenständige HTML-Statusseite.
 * Alle Texte kommen aus ReportMessages (i18n/messages*.properties).
 */
object A11yHtmlReport {

    private val impactOrder = listOf("critical", "serious", "moderate", "minor")

    fun render(
        summary: A11ySummary,
        locale: Locale = A11yConfig.reportLocale,
        title: String? = null,
    ): String {
        val m = ReportMessages(locale)
        val pageTitle = title ?: m["report.title"]
        val findings = summary.findings
        val scenarioCount = findings.flatMap { it.scenarios }.toSet().size
        val ok = summary.newFindings == 0

        return buildString {
            append("<!DOCTYPE html>\n<html lang=\"").append(esc(locale.language))
                .append("\">\n<head>\n<meta charset=\"utf-8\">\n")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            append("<title>").append(esc(pageTitle)).append("</title>\n")
            append("<style>").append(CSS).append("</style>\n</head>\n<body>\n<main>\n")

            // Kopf mit Gesamturteil
            append("<header class=\"head\">\n<div>\n")
            append("<h1>").append(esc(pageTitle)).append("</h1>\n")
            append("<p class=\"muted\">").append(esc(m["report.runOf", formatTime(summary.generatedAt, m)]))
                .append("</p>\n</div>\n")
            if (ok) {
                append("<div class=\"verdict good\"><span class=\"icon\" aria-hidden=\"true\">✓</span>")
                    .append(esc(m["verdict.ok"])).append("</div>\n")
            } else {
                append("<div class=\"verdict bad\"><span class=\"icon\" aria-hidden=\"true\">✕</span>")
                    .append(esc(m["verdict.new", summary.newFindings])).append("</div>\n")
            }
            append("</header>\n")

            // Kennzahlen
            append("<section class=\"tiles\">\n")
            tile(m["tile.new.label"], summary.newFindings, m["tile.new.hint"], if (ok) "" else "accent-bad")
            tile(m["tile.known.label"], summary.knownFindings, m["tile.known.hint"], "")
            tile(m["tile.scenarios.label"], scenarioCount, m["tile.scenarios.hint"], "")
            tile(m["tile.stale.label"], summary.staleBaselineEntries.size, m["tile.stale.hint"], "")
            append("</section>\n")

            // Grafiken
            append("<section class=\"charts\">\n")
            resultBar(summary, m)
            impactBars(findings, m)
            ruleBars(findings, m)
            append("</section>\n")

            // Tabelle
            findingsTable(findings, m)

            // Veraltete Baseline-Einträge
            if (summary.staleBaselineEntries.isNotEmpty()) {
                append("<section class=\"card\">\n<h2>").append(esc(m["stale.title"])).append("</h2>\n")
                append("<p class=\"muted\">").append(esc(m["stale.hint"])).append("</p>\n")
                append("<div class=\"scroll\"><table>\n<thead><tr>")
                listOf("col.fingerprint", "col.rule", "col.page", "col.ticket")
                    .forEach { append("<th>").append(esc(m[it])).append("</th>") }
                append("</tr></thead>\n<tbody>\n")
                summary.staleBaselineEntries.forEach { e ->
                    append("<tr><td class=\"mono\">").append(esc(e.fingerprint)).append("</td>")
                    append("<td>").append(esc(e.rule ?: "–")).append("</td>")
                    append("<td>").append(esc(e.page ?: "–")).append("</td>")
                    append("<td>").append(esc(e.ticket ?: "–")).append("</td></tr>\n")
                }
                append("</tbody></table></div>\n</section>\n")
            }

            append("<footer class=\"muted\">").append(esc(m["report.footer"])).append("</footer>\n")
            append("</main>\n<script>").append(JS).append("</script>\n</body>\n</html>\n")
        }
    }

    private fun StringBuilder.tile(label: String, value: Int, hint: String, extra: String) {
        append("<div class=\"card tile ").append(extra).append("\">")
        append("<div class=\"tile-label\">").append(esc(label)).append("</div>")
        append("<div class=\"tile-value\">").append(value).append("</div>")
        append("<div class=\"muted small\">").append(esc(hint)).append("</div></div>\n")
    }

    /** 100%-Balken neu vs. bekannt: das Ergebnis auf einen Blick. */
    private fun StringBuilder.resultBar(summary: A11ySummary, m: ReportMessages) {
        val total = summary.totalFindings
        append("<div class=\"card chart\">\n<h2>").append(esc(m["result.title"])).append("</h2>\n")
        if (total == 0) {
            append("<p class=\"empty\"><span class=\"icon good-ink\" aria-hidden=\"true\">✓</span>")
                .append(esc(m["result.none"])).append("</p>\n</div>\n")
            return
        }
        append("<div class=\"stack\" role=\"img\" aria-label=\"")
            .append(esc(m["result.aria", summary.newFindings, summary.knownFindings])).append("\">")
        if (summary.newFindings > 0) {
            append("<div class=\"seg seg-new\" style=\"flex-grow:").append(summary.newFindings)
                .append("\" title=\"").append(esc(m["status.new"])).append(": ").append(summary.newFindings)
                .append("\"></div>")
        }
        if (summary.knownFindings > 0) {
            append("<div class=\"seg seg-known\" style=\"flex-grow:").append(summary.knownFindings)
                .append("\" title=\"").append(esc(m["status.known"])).append(": ").append(summary.knownFindings)
                .append("\"></div>")
        }
        append("</div>\n<div class=\"legend\">")
        append("<span><i class=\"sw sw-new\"></i>").append(esc(m["status.new"])).append(" <b>")
            .append(summary.newFindings).append("</b> · ").append(pct(summary.newFindings, total)).append("</span>")
        append("<span><i class=\"sw sw-known\"></i>").append(esc(m["status.known"])).append(" <b>")
            .append(summary.knownFindings).append("</b> · ").append(pct(summary.knownFindings, total)).append("</span>")
        append("</div>\n</div>\n")
    }

    /** Findings nach axe-Impact, jeweils mit Anteil neu. */
    private fun StringBuilder.impactBars(findings: List<SummaryEntry>, m: ReportMessages) {
        append("<div class=\"card chart\">\n<h2>").append(esc(m["impact.title"])).append("</h2>\n")
        if (findings.isEmpty()) {
            append("<p class=\"empty muted\">").append(esc(m["common.noData"])).append("</p>\n</div>\n")
            return
        }
        val byImpact = findings.groupBy { (it.impact ?: "minor").lowercase() }
        val max = impactOrder.maxOf { byImpact[it]?.size ?: 0 }.coerceAtLeast(1)
        append("<div class=\"bars\">\n")
        impactOrder.forEach { impact ->
            val list = byImpact[impact].orEmpty()
            val newCount = list.count { it.status == A11yStatus.NEW }
            val label = m["impact.$impact"]
            append("<div class=\"bar-row\" title=\"").append(esc(m["impact.tooltip", label, list.size, newCount]))
                .append("\">")
            append("<span class=\"bar-label\"><i class=\"sw imp-").append(impact).append("\"></i>")
                .append(esc(label)).append("</span>")
            append("<span class=\"track\">")
            if (list.isNotEmpty()) {
                append("<span class=\"fill imp-").append(impact).append("\" style=\"width:")
                    .append(pctNum(list.size, max)).append("%\"></span>")
            }
            append("</span>")
            append("<span class=\"bar-value\">").append(list.size)
            if (newCount > 0) append(" <em>").append(esc(m["impact.newSuffix", newCount])).append("</em>")
            append("</span></div>\n")
        }
        append("</div>\n</div>\n")
    }

    /** Häufigste Regeln nach Anzahl betroffener Szenarien. */
    private fun StringBuilder.ruleBars(findings: List<SummaryEntry>, m: ReportMessages) {
        append("<div class=\"card chart\">\n<h2>").append(esc(m["rules.title"])).append("</h2>\n")
        if (findings.isEmpty()) {
            append("<p class=\"empty muted\">").append(esc(m["common.noData"])).append("</p>\n</div>\n")
            return
        }
        val byRule = findings.groupBy { it.rule }
            .map { (rule, list) -> Triple(rule, list.sumOf { it.occurrences }, list.size) }
            .sortedByDescending { it.second }
            .take(6)
        val max = byRule.maxOf { it.second }.coerceAtLeast(1)
        append("<div class=\"bars\">\n")
        byRule.forEach { (rule, occurrences, count) ->
            append("<div class=\"bar-row\" title=\"").append(esc(m["rules.tooltip", rule, count, occurrences]))
                .append("\">")
            append("<span class=\"bar-label mono\">").append(esc(rule)).append("</span>")
            append("<span class=\"track\"><span class=\"fill rule\" style=\"width:")
                .append(pctNum(occurrences, max)).append("%\"></span></span>")
            append("<span class=\"bar-value\">").append(occurrences).append("</span></div>\n")
        }
        append("</div>\n<p class=\"muted small\">").append(esc(m["rules.note"])).append("</p>\n</div>\n")
    }

    private fun StringBuilder.findingsTable(findings: List<SummaryEntry>, m: ReportMessages) {
        append("<section class=\"card\">\n<div class=\"table-head\">\n<h2>").append(esc(m["table.title"]))
            .append("</h2>\n")
        append("<div class=\"filters\">")
        append("<input id=\"q\" type=\"search\" placeholder=\"").append(esc(m["table.filter.placeholder"]))
            .append("\" aria-label=\"").append(esc(m["table.filter.aria"])).append("\">")
        append("<label><input id=\"onlyNew\" type=\"checkbox\"> ").append(esc(m["table.filter.onlyNew"]))
            .append("</label>")
        append("</div>\n</div>\n")
        if (findings.isEmpty()) {
            append("<p class=\"empty muted\">").append(esc(m["table.empty"])).append("</p>\n</section>\n")
            return
        }
        append("<div class=\"scroll\"><table id=\"findings\">\n<thead><tr>")
        listOf("col.status", "col.rule", "col.impact", "col.page", "col.selector").forEach {
            append("<th>").append(esc(m[it])).append("</th>")
        }
        append("<th class=\"num\">").append(esc(m["col.scenarios"])).append("</th>")
        append("<th>").append(esc(m["col.ticket"])).append("</th>")
        append("</tr></thead>\n<tbody>\n")
        findings.forEach { f ->
            val isNew = f.status == A11yStatus.NEW
            val impact = (f.impact ?: "minor").lowercase()
            append("<tr data-status=\"").append(f.status.name).append("\">")
            if (isNew) {
                append("<td><span class=\"pill pill-new\">✕ ").append(esc(m["status.new"])).append("</span></td>")
            } else {
                append("<td><span class=\"pill pill-known\">✓ ").append(esc(m["status.known"])).append("</span></td>")
            }
            append("<td>")
            if (f.helpUrl != null) {
                append("<a href=\"").append(esc(f.helpUrl)).append("\" target=\"_blank\" rel=\"noopener\">")
                    .append(esc(f.rule)).append("</a>")
            } else {
                append(esc(f.rule))
            }
            if (f.help != null) append("<div class=\"muted small\">").append(esc(f.help)).append("</div>")
            append("<div class=\"mono small muted\">").append(esc(f.fingerprint)).append("</div></td>")
            val impactText = if (impact in impactOrder) m["impact.$impact"] else impact
            append("<td><i class=\"sw imp-").append(impact).append("\"></i>").append(esc(impactText)).append("</td>")
            append("<td>").append(esc(f.page)).append("</td>")
            append("<td class=\"mono small sel\">").append(esc(f.selector)).append("</td>")
            append("<td class=\"num\"><details><summary>").append(f.occurrences).append("</summary><ul>")
            f.scenarios.forEach { append("<li>").append(esc(it)).append("</li>") }
            append("</ul></details></td>")
            append("<td>").append(esc(f.ticket?.takeIf { it.isNotBlank() } ?: "–")).append("</td>")
            append("</tr>\n")
        }
        append("</tbody></table></div>\n<p id=\"noMatch\" class=\"empty muted\" hidden>")
            .append(esc(m["table.noMatch"])).append("</p>\n</section>\n")
    }

    private fun pct(part: Int, total: Int): String = "${Math.round(part * 100.0 / total)} %"

    private fun pctNum(part: Int, total: Int): String = String.format(Locale.ROOT, "%.1f", part * 100.0 / total)

    private fun formatTime(iso: String, m: ReportMessages): String = runCatching {
        DateTimeFormatter.ofPattern(m["report.dateFormat"], m.locale)
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(iso))
    }.getOrDefault(iso)

    private fun esc(s: String): String = buildString(s.length) {
        s.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    private val CSS = """
:root{
  --page:#f9f9f7;--surface:#fcfcfb;--ink:#0b0b0b;--ink-2:#52514e;--muted:#898781;
  --grid:#e1e0d9;--border:rgba(11,11,11,.10);
  --good:#0ca30c;--good-ink:#006300;--bad:#d03b3b;
  --critical:#d03b3b;--serious:#ec835a;--moderate:#fab219;--minor:#898781;
  --known:#c3c2b7;--rule:#2a78d6;
}
@media (prefers-color-scheme: dark){
  :root{
    --page:#0d0d0d;--surface:#1a1a19;--ink:#ffffff;--ink-2:#c3c2b7;--muted:#898781;
    --grid:#2c2c2a;--border:rgba(255,255,255,.10);
    --good-ink:#0ca30c;--known:#52514e;--rule:#3987e5;--minor:#6b6a65;
  }
}
*{box-sizing:border-box}
body{margin:0;background:var(--page);color:var(--ink);
  font:14px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:1200px;margin:0 auto;padding:32px 16px 48px}
h1{font-size:24px;margin:0;font-weight:650;letter-spacing:-.01em}
h2{font-size:14px;margin:0 0 16px;font-weight:600;color:var(--ink-2)}
a{color:var(--rule)}
.muted{color:var(--muted)}.small{font-size:12px}
.mono{font-family:ui-monospace,"SF Mono",Menlo,Consolas,monospace}
.card{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:20px}
.head{display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap;margin-bottom:24px}
.head p{margin:4px 0 0}
.verdict{display:inline-flex;align-items:center;gap:8px;padding:8px 16px;border-radius:999px;font-weight:600;border:1px solid}
.verdict.good{color:var(--good-ink);border-color:var(--good)}
.verdict.bad{color:var(--bad);border-color:var(--bad)}
.icon{font-weight:700}
.tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:16px;margin-bottom:16px}
.tile-label{color:var(--ink-2);font-size:13px}
.tile-value{font-size:36px;font-weight:650;line-height:1.2;margin:4px 0;font-variant-numeric:tabular-nums}
.tile.accent-bad{border-top:3px solid var(--bad)}
.tile.accent-bad .tile-value{color:var(--bad)}
.charts{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:16px;margin-bottom:16px;align-items:start}
.stack{display:flex;gap:2px;height:28px;margin:8px 0 16px}
.seg{min-width:6px;border-radius:2px}
.seg:first-child{border-top-left-radius:4px;border-bottom-left-radius:4px}
.seg:last-child{border-top-right-radius:4px;border-bottom-right-radius:4px}
.seg-new,.sw-new{background:var(--bad)}
.seg-known,.sw-known{background:var(--known)}
.legend{display:flex;gap:24px;flex-wrap:wrap;color:var(--ink-2)}
.legend b{color:var(--ink);font-variant-numeric:tabular-nums}
.sw{display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:6px;vertical-align:-1px}
.bars{display:flex;flex-direction:column;gap:10px}
.bar-row{display:grid;grid-template-columns:minmax(90px,140px) 1fr auto;align-items:center;gap:12px}
.bar-label{color:var(--ink-2);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.track{height:12px;border-radius:0 4px 4px 0;border-left:1px solid var(--grid)}
.fill{display:block;height:100%;border-radius:0 4px 4px 0;min-width:2px}
.bar-value{font-variant-numeric:tabular-nums;min-width:32px;text-align:right}
.bar-value em{font-style:normal;color:var(--bad);font-size:12px}
.imp-critical{background:var(--critical)}.imp-serious{background:var(--serious)}
.imp-moderate{background:var(--moderate)}.imp-minor{background:var(--minor)}
.fill.rule{background:var(--rule)}
.bar-row:hover .bar-label{color:var(--ink)}
.empty{margin:8px 0}.good-ink{color:var(--good-ink);margin-right:6px}
.table-head{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:12px}
.table-head h2{margin:0}
.filters{display:flex;gap:16px;align-items:center;flex:1 1 auto;justify-content:flex-end}
.filters label{white-space:nowrap}
.filters input[type=search]{width:min(320px,100%);padding:6px 10px;border-radius:8px;border:1px solid var(--border);
  background:var(--page);color:var(--ink);font:inherit}
.scroll{overflow-x:auto}
table{width:100%;border-collapse:collapse;font-size:13px}
th{text-align:left;font-weight:600;color:var(--ink-2);border-bottom:1px solid var(--grid);padding:8px}
td{border-bottom:1px solid var(--grid);padding:10px 8px;vertical-align:top}
tbody tr:hover{background:var(--page)}
.num{text-align:right}
.sel{max-width:280px;word-break:break-all}
.pill{display:inline-block;padding:2px 8px;border-radius:999px;font-size:12px;font-weight:600;white-space:nowrap;border:1px solid}
.pill-new{color:var(--bad);border-color:var(--bad)}
.pill-known{color:var(--ink-2);border-color:var(--known)}
details summary{cursor:pointer;font-variant-numeric:tabular-nums}
details ul{text-align:left;margin:8px 0 0;padding-left:16px;font-size:12px;color:var(--ink-2);min-width:260px}
section.card{margin-bottom:16px}
footer{margin-top:24px;font-size:12px;text-align:center}
@media (max-width:600px){.filters{flex-wrap:wrap;justify-content:flex-start}.tile-value{font-size:28px}.bar-row{grid-template-columns:90px 1fr auto}}
"""

    private val JS = """
(function(){
  var q=document.getElementById('q'),only=document.getElementById('onlyNew'),
      rows=document.querySelectorAll('#findings tbody tr'),none=document.getElementById('noMatch');
  if(!q||!rows.length)return;
  function apply(){
    var term=q.value.trim().toLowerCase(),shown=0;
    rows.forEach(function(r){
      var ok=(!only.checked||r.dataset.status==='NEW')&&(!term||r.textContent.toLowerCase().indexOf(term)>=0);
      r.hidden=!ok;if(ok)shown++;
    });
    none.hidden=shown>0;
  }
  q.addEventListener('input',apply);only.addEventListener('change',apply);
})();
"""
}