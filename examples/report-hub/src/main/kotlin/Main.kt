// report-hub · 통계/보고 자료 관리 (Kotlin, Ktor)
// 보고 항목 정의 → 수집(기관별 제출) → 집계 → 승인 → 공개 흐름을 상태 전이로 관리한다.
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val KST: ZoneId = ZoneId.of("Asia/Seoul")
val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
fun now(): String = LocalDateTime.now(KST).format(TS)

val CATEGORIES = listOf("학사통계", "급식", "시설", "예산", "안전", "연수")
val STATUSES = listOf("DRAFT", "COLLECTING", "AGGREGATED", "APPROVED", "PUBLISHED")
val FIELD_TYPES = listOf("sum", "avg")

fun round2(x: Double): Double = Math.round(x * 100.0) / 100.0
fun csv(v: String): String = if (v.contains(',') || v.contains('"')) "\"" + v.replace("\"", "\"\"") + "\"" else v

val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class Field(
    val key: String,
    val label: String,
    val type: String = "sum",        // sum(합계형) | avg(평균/점수형)
    val min: Double? = null,          // 제출값 하한(선택)
    val max: Double? = null,          // 제출값 상한(선택)
    val weightKey: String? = null,     // avg형 가중평균 가중치 필드(선택)
)

@Serializable
data class Submission(val org: String, val values: Map<String, Double>, val submitter: String? = null, val submittedAt: String)

@Serializable
data class Hist(val at: String, val actor: String, val act: String, val memo: String? = null)

@Serializable
data class Report(
    var id: Int,
    var title: String,
    var category: String,
    var period: String,
    var fields: List<Field>,
    var targetOrgs: List<String> = emptyList(), // 취합 대상 기관 명부(제출율·미제출자 산출)
    var dueDate: String? = null,
    var status: String = "DRAFT",
    val submissions: MutableList<Submission> = mutableListOf(),
    val history: MutableList<Hist> = mutableListOf(),
    var createdAt: String,
    var updatedAt: String,
)

@Serializable
data class Snapshot(val seq: Int, val reports: List<Report>)

class ApiException(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)

object Store {
    val lock = Any()
    var seq = 0
    val reports = LinkedHashMap<Int, Report>()
    val file: String = System.getenv("DATA_FILE") ?: ""

    fun init() {
        if (file.isNotEmpty() && File(file).exists() && load()) {
            println("loaded ${reports.size} reports from $file"); return
        }
        seed(); save()
    }

    fun save() {
        if (file.isEmpty()) return
        try {
            val snap = Snapshot(seq, reports.values.toList())
            File("$file.tmp").writeText(json.encodeToString(Snapshot.serializer(), snap))
            File("$file.tmp").renameTo(File(file))
        } catch (e: Exception) {
            System.err.println("save failed: ${e.message}")
        }
    }

    fun load(): Boolean = try {
        val snap = json.decodeFromString(Snapshot.serializer(), File(file).readText())
        seq = snap.seq
        snap.reports.forEach { reports[it.id] = it }
        reports.isNotEmpty()
    } catch (e: Exception) { false }

    fun get(id: Int): Report = reports[id] ?: throw ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "보고 $id 를 찾을 수 없습니다.")

    fun seed() {
        fun mk(title: String, cat: String, period: String, fields: List<Pair<String, String>>, status: String,
               subs: List<Triple<String, Map<String, Double>, String>>, hist: List<Pair<String, String>>) {
            val id = ++seq
            val r = Report(
                id = id, title = title, category = cat, period = period,
                fields = fields.map { Field(it.first, it.second) }, dueDate = "2026-09-10", status = status,
                submissions = subs.map { Submission(it.first, it.second, it.third, "2026-08-22 10:00:00") }.toMutableList(),
                history = hist.map { Hist(it.first, "담당자", it.second) }.toMutableList(),
                createdAt = now(), updatedAt = now(),
            )
            reports[id] = r
        }
        mk("2026 2학기 학년별 재학생 현황", "학사통계", "2026-2학기",
            listOf("boys" to "남학생", "girls" to "여학생", "classes" to "학급수"), "COLLECTING",
            listOf(Triple("가람초", mapOf("boys" to 210.0, "girls" to 198.0, "classes" to 18.0), "교무부"),
                Triple("나래중", mapOf("boys" to 320.0, "girls" to 305.0, "classes" to 24.0), "교무부")),
            listOf("2026-08-20 09:00:00" to "보고생성", "2026-08-20 10:00:00" to "수집개시"))
        mk("8월 급식 만족도 조사 집계", "급식", "2026-08",
            listOf("respondents" to "응답자수", "score" to "만족도점수"), "PUBLISHED",
            listOf(Triple("가람초", mapOf("respondents" to 240.0, "score" to 4.3), "영양실"),
                Triple("나래중", mapOf("respondents" to 410.0, "score" to 4.1), "영양실")),
            listOf("2026-08-05 09:00:00" to "보고생성", "2026-08-10 09:00:00" to "수집마감",
                "2026-08-11 09:00:00" to "승인", "2026-08-12 09:00:00" to "공개"))
        mk("3분기 학교 안전점검 실적", "안전", "2026-3Q",
            listOf("inspections" to "점검건수", "findings" to "지적건수", "resolved" to "조치완료"), "DRAFT",
            listOf(), listOf("2026-08-25 09:00:00" to "보고생성"))
    }
}

// ---------- 필드/기관 파싱 ----------
fun parseFields(arr: JsonArray): List<Field> {
    val fs = arr.mapNotNull {
        val o = it.jsonObject
        val key = o.str("key"); val label = o.str("label")
        if (key.isEmpty() || label.isEmpty()) return@mapNotNull null
        val type = o.str("type").ifEmpty { "sum" }
        if (type !in FIELD_TYPES) throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "field type은 sum/avg 중 하나여야 합니다.")
        Field(key, label, type, (o["min"] as? JsonPrimitive)?.doubleOrNull, (o["max"] as? JsonPrimitive)?.doubleOrNull, o.str("weightKey").ifEmpty { null })
    }
    if (fs.map { it.key }.toSet().size != fs.size) throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "field key가 중복되었습니다.")
    return fs
}
fun parseOrgs(el: JsonElement?): List<String> =
    el?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList()

// ---------- 집계 ----------
fun aggregate(r: Report): JsonObject = buildJsonObject {
    for (f in r.fields) {
        val vals = r.submissions.mapNotNull { it.values[f.key] }
        put(f.key, buildJsonObject {
            put("count", vals.size)
            put("type", f.type)
            if (vals.isNotEmpty()) {
                put("min", vals.min()); put("max", vals.max()); put("avg", round2(vals.average()))
                if (f.type == "sum") {
                    put("sum", vals.sum())
                } else if (f.weightKey != null) { // 가중평균(예: 학교별 응답자수 가중)
                    val pairs = r.submissions.mapNotNull { s ->
                        val v = s.values[f.key]; val w = s.values[f.weightKey]
                        if (v != null && w != null) v to w else null
                    }
                    val wsum = pairs.sumOf { it.second }
                    if (wsum > 0) put("weightedAvg", round2(pairs.sumOf { it.first * it.second } / wsum))
                }
            }
        })
    }
}

fun reportJson(r: Report): JsonObject {
    val base = json.encodeToJsonElement(Report.serializer(), r).jsonObject
    val submitted = r.submissions.map { it.org }.toSet()
    val missing = r.targetOrgs.filter { it !in submitted }
    return buildJsonObject {
        base.forEach { (k, v) -> put(k, v) }
        put("submissionCount", r.submissions.size)
        put("aggregate", aggregate(r))
        put("missingOrgs", JsonArray(missing.map { JsonPrimitive(it) }))
        if (r.targetOrgs.isNotEmpty()) {
            val submittedTargets = r.targetOrgs.count { it in submitted }
            put("submissionRate", round2(submittedTargets.toDouble() / r.targetOrgs.size * 100.0))
        }
    }
}

// ---------- 요청 헬퍼 ----------
fun JsonObject.str(k: String): String = (this[k] as? JsonPrimitive)?.contentOrNull?.trim() ?: ""
fun JsonObject.actor(): String = str("actor").ifEmpty { "system" }

suspend fun ApplicationCall.body(): JsonObject {
    val t = receiveText()
    if (t.isBlank()) return JsonObject(emptyMap())
    return try {
        json.parseToJsonElement(t).jsonObject
    } catch (e: Exception) {
        throw ApiException(HttpStatusCode.BadRequest, "INVALID_JSON", "요청 본문을 해석할 수 없습니다.")
    }
}

suspend fun ApplicationCall.ok(obj: JsonElement, status: HttpStatusCode = HttpStatusCode.OK) =
    respondText(json.encodeToString(JsonElement.serializer(), obj), ContentType.Application.Json, status)

fun idParam(call: ApplicationCall): Int =
    call.parameters["id"]?.toIntOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "id가 올바르지 않습니다.")

fun hist(r: Report, actor: String, act: String, memo: String? = null) {
    r.history.add(Hist(now(), actor, act, memo))
    r.updatedAt = now()
}

fun Application.module() {
    install(StatusPages) {
        exception<ApiException> { call, e ->
            call.respondText(json.encodeToString(JsonElement.serializer(),
                buildJsonObject { put("error", buildJsonObject { put("code", e.code); put("message", e.message) }) }),
                ContentType.Application.Json, e.status)
        }
        exception<Throwable> { call, e ->
            call.respondText(json.encodeToString(JsonElement.serializer(),
                buildJsonObject { put("error", buildJsonObject { put("code", "INTERNAL"); put("message", e.message ?: "error") }) }),
                ContentType.Application.Json, HttpStatusCode.InternalServerError)
        }
    }
    routing {
        get("/healthz") { call.ok(buildJsonObject { put("status", "ok"); put("service", "report-hub"); put("time", now()) }) }
        get("/") { call.respondText(INDEX, ContentType.Text.Html) }

        get("/api/reports") {
            val q = call.request.queryParameters
            val status = q["status"]?.trim()?.uppercase() ?: ""
            val category = q["category"]?.trim() ?: ""
            val kw = q["q"]?.trim() ?: ""
            var page = q["page"]?.toIntOrNull() ?: 1; if (page < 1) page = 1
            var size = q["size"]?.toIntOrNull() ?: 10; if (size < 1 || size > 100) size = 10
            val res = synchronized(Store.lock) {
                val items = Store.reports.values.filter {
                    (status.isEmpty() || it.status == status) &&
                        (category.isEmpty() || it.category == category) &&
                        (kw.isEmpty() || it.title.contains(kw) || it.period.contains(kw))
                }.sortedByDescending { it.id }
                val total = items.size
                val pageItems = items.drop((page - 1) * size).take(size).map { reportJson(it) }
                buildJsonObject {
                    put("page", page); put("size", size); put("total", total)
                    put("items", JsonArray(pageItems))
                }
            }
            call.ok(res)
        }

        post("/api/reports") {
            val b = call.body()
            val title = b.str("title"); val category = b.str("category"); val period = b.str("period")
            if (title.isEmpty() || category.isEmpty() || period.isEmpty())
                throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "title, category, period는 필수입니다.")
            if (category !in CATEGORIES)
                throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "category는 ${CATEGORIES} 중 하나여야 합니다.")
            val fieldsArr = b["fields"]?.jsonArray ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "fields(수집 항목)는 필수입니다.")
            val fields = parseFields(fieldsArr)
            if (fields.isEmpty()) throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "유효한 fields가 최소 1개 필요합니다.")
            val targetOrgs = parseOrgs(b["targetOrgs"])
            val res = synchronized(Store.lock) {
                val id = ++Store.seq
                val r = Report(id = id, title = title, category = category, period = period, fields = fields,
                    targetOrgs = targetOrgs, dueDate = b.str("dueDate").ifEmpty { null }, status = "DRAFT", createdAt = now(), updatedAt = now())
                hist(r, b.actor(), "보고생성")
                Store.reports[id] = r
                Store.save()
                reportJson(r)
            }
            call.ok(res, HttpStatusCode.Created)
        }

        get("/api/reports/{id}") {
            val res = synchronized(Store.lock) { reportJson(Store.get(idParam(call))) }
            call.ok(res)
        }
        get("/api/reports/{id}/submissions") {
            val res = synchronized(Store.lock) {
                val r = Store.get(idParam(call))
                buildJsonObject { put("id", r.id); put("submissions", json.encodeToJsonElement(r.submissions)) }
            }
            call.ok(res)
        }
        get("/api/reports/{id}/aggregate") {
            val res = synchronized(Store.lock) {
                val r = Store.get(idParam(call))
                buildJsonObject { put("id", r.id); put("submissionCount", r.submissions.size); put("aggregate", aggregate(r)) }
            }
            call.ok(res)
        }
        get("/api/reports/{id}/history") {
            val res = synchronized(Store.lock) { val r = Store.get(idParam(call)); buildJsonObject { put("id", r.id); put("history", json.encodeToJsonElement(r.history)) } }
            call.ok(res)
        }
        get("/api/reports/{id}/export") { // 상급 보고용 CSV
            val res = synchronized(Store.lock) {
                val r = Store.get(idParam(call))
                val sb = StringBuilder()
                sb.append('﻿') // Excel 한글 인식용 UTF-8 BOM
                sb.append("org").append(r.fields.joinToString("") { ",${it.key}" }).append("\n")
                for (s in r.submissions) {
                    sb.append(csv(s.org))
                    for (f in r.fields) sb.append(",").append(s.values[f.key]?.toString() ?: "")
                    sb.append("\n")
                }
                sb.toString()
            }
            call.respondText(res, ContentType.parse("text/csv"))
        }

        patch("/api/reports/{id}") {
            val b = call.body()
            val res = synchronized(Store.lock) {
                val r = Store.get(idParam(call))
                if (r.status != "DRAFT") throw ApiException(HttpStatusCode.Conflict, "INVALID_STATE", "초안(DRAFT) 보고만 수정할 수 있습니다.")
                b.str("title").let { if (it.isNotEmpty()) r.title = it }
                if (b.containsKey("category")) { val c = b.str("category"); if (c !in CATEGORIES) throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "category 오류"); r.category = c }
                b.str("period").let { if (it.isNotEmpty()) r.period = it }
                if (b.containsKey("dueDate")) r.dueDate = b.str("dueDate").ifEmpty { null }
                b["fields"]?.jsonArray?.let { arr -> val fs = parseFields(arr); if (fs.isNotEmpty()) r.fields = fs }
                if (b.containsKey("targetOrgs")) r.targetOrgs = parseOrgs(b["targetOrgs"])
                hist(r, b.actor(), "수정")
                Store.save()
                reportJson(r)
            }
            call.ok(res)
        }

        post("/api/reports/{id}/open") {
            val b = call.body()
            val res = synchronized(Store.lock) {
                val r = Store.get(idParam(call))
                if (r.status != "DRAFT") throw ApiException(HttpStatusCode.Conflict, "INVALID_STATE", "초안 보고만 수집을 개시할 수 있습니다.")
                r.status = "COLLECTING"; hist(r, b.actor(), "수집개시"); Store.save(); reportJson(r)
            }
            call.ok(res)
        }

        post("/api/reports/{id}/submit") {
            val b = call.body()
            val org = b.str("org")
            if (org.isEmpty()) throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "org(제출 기관)는 필수입니다.")
            val valuesObj = b["values"]?.jsonObject ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "values는 필수입니다.")
            val res = synchronized(Store.lock) {
                val r = Store.get(idParam(call))
                if (r.status != "COLLECTING") throw ApiException(HttpStatusCode.Conflict, "INVALID_STATE", "수집 중(COLLECTING) 보고만 제출할 수 있습니다.")
                val keys = r.fields.map { it.key }.toSet()
                val values = LinkedHashMap<String, Double>()
                for ((k, v) in valuesObj) {
                    if (k !in keys) throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "정의되지 않은 항목: $k")
                    val d = (v as? JsonPrimitive)?.doubleOrNull ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "$k 값은 숫자여야 합니다.")
                    values[k] = d
                }
                if (values.isEmpty()) throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "제출 값이 없습니다.")
                for ((k, d) in values) {
                    val f = r.fields.first { it.key == k }
                    if (f.min != null && d < f.min!!) throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "$k 값은 ${f.min} 이상이어야 합니다.")
                    if (f.max != null && d > f.max!!) throw ApiException(HttpStatusCode.BadRequest, "VALIDATION", "$k 값은 ${f.max} 이하여야 합니다.")
                }
                r.submissions.removeAll { it.org == org } // 같은 기관 재제출은 갱신
                r.submissions.add(Submission(org, values, b.str("submitter").ifEmpty { null }, now()))
                hist(r, b.actor().ifEmpty { org }, "제출", org)
                Store.save()
                reportJson(r)
            }
            call.ok(res)
        }

        post("/api/reports/{id}/close") {
            val b = call.body()
            val res = synchronized(Store.lock) {
                val r = Store.get(idParam(call))
                if (r.status != "COLLECTING") throw ApiException(HttpStatusCode.Conflict, "INVALID_STATE", "수집 중 보고만 마감할 수 있습니다.")
                if (r.submissions.isEmpty()) throw ApiException(HttpStatusCode.Conflict, "NO_SUBMISSION", "제출이 없어 집계 마감할 수 없습니다.")
                r.status = "AGGREGATED"; hist(r, b.actor(), "수집마감"); Store.save(); reportJson(r)
            }
            call.ok(res)
        }

        post("/api/reports/{id}/reopen") {
            val b = call.body()
            val res = synchronized(Store.lock) {
                val r = Store.get(idParam(call))
                if (r.status !in listOf("AGGREGATED", "APPROVED", "PUBLISHED")) throw ApiException(HttpStatusCode.Conflict, "INVALID_STATE", "집계/승인/공개 보고만 수집 재개(정정재공개)할 수 있습니다.")
                val act = if (r.status == "PUBLISHED") "정정재공개" else "수집재개"
                r.status = "COLLECTING"; hist(r, b.actor(), act, b.str("reason").ifEmpty { null }); Store.save(); reportJson(r)
            }
            call.ok(res)
        }

        post("/api/reports/{id}/approve") {
            val b = call.body()
            val res = synchronized(Store.lock) {
                val r = Store.get(idParam(call))
                if (r.status != "AGGREGATED") throw ApiException(HttpStatusCode.Conflict, "INVALID_STATE", "집계 마감된 보고만 승인할 수 있습니다.")
                r.status = "APPROVED"; hist(r, b.actor(), "승인"); Store.save(); reportJson(r)
            }
            call.ok(res)
        }

        post("/api/reports/{id}/publish") {
            val b = call.body()
            val res = synchronized(Store.lock) {
                val r = Store.get(idParam(call))
                if (r.status != "APPROVED") throw ApiException(HttpStatusCode.Conflict, "INVALID_STATE", "승인된 보고만 공개할 수 있습니다.")
                r.status = "PUBLISHED"; hist(r, b.actor(), "공개"); Store.save(); reportJson(r)
            }
            call.ok(res)
        }

        get("/api/stats") {
            val res = synchronized(Store.lock) {
                val byStatus = STATUSES.associateWith { 0 }.toMutableMap()
                val byCategory = LinkedHashMap<String, Int>()
                var totalSubs = 0
                for (r in Store.reports.values) {
                    byStatus[r.status] = (byStatus[r.status] ?: 0) + 1
                    byCategory[r.category] = (byCategory[r.category] ?: 0) + 1
                    totalSubs += r.submissions.size
                }
                buildJsonObject {
                    put("total", Store.reports.size)
                    put("byStatus", json.encodeToJsonElement(byStatus))
                    put("byCategory", json.encodeToJsonElement(byCategory))
                    put("totalSubmissions", totalSubs)
                }
            }
            call.ok(res)
        }
    }
}

fun main() {
    Store.init()
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    println("report-hub listening on :$port")
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

const val INDEX = """<!doctype html><html lang=ko><head><meta charset=utf-8>
<meta name=viewport content="width=device-width, initial-scale=1"><title>통계·보고 자료 관리</title>
<style>
:root{--line:#e2e8f0;--ink:#1e293b;--mut:#64748b;--blue:#2563eb;--bg:#f8fafc}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,'Malgun Gothic',sans-serif;color:var(--ink);background:var(--bg)}
header{background:#fff;border-bottom:1px solid var(--line);padding:16px 24px}header h1{font-size:20px;margin:0}header p{margin:4px 0 0;color:var(--mut);font-size:13px}
.wrap{max-width:1160px;margin:0 auto;padding:20px 24px}
.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:18px}
.card{background:#fff;border:1px solid var(--line);border-radius:12px;padding:14px}.card .lbl{font-size:12px;color:var(--mut)}.card .val{font-size:22px;font-weight:800;margin-top:4px}
.toolbar{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:12px}
input,select,button,textarea{font:inherit;padding:8px 10px;border:1px solid var(--line);border-radius:8px;background:#fff;color:var(--ink)}button{cursor:pointer}.btn-primary{background:var(--blue);color:#fff;border-color:var(--blue);font-weight:600}.btn-sm{padding:4px 8px;font-size:12px}
table{width:100%;border-collapse:collapse;background:#fff;border:1px solid var(--line);border-radius:12px;overflow:hidden;font-size:13px}
th,td{text-align:left;padding:10px 12px;border-bottom:1px solid var(--line)}th{background:#f1f5f9;color:var(--mut);font-size:11px}tr:last-child td{border-bottom:none}
.badge{display:inline-block;padding:2px 8px;border-radius:999px;font-size:11px;font-weight:700}
.s-DRAFT{background:#e2e8f0;color:#475569}.s-COLLECTING{background:#fef3c7;color:#92400e}.s-AGGREGATED{background:#dbeafe;color:#1e40af}.s-APPROVED{background:#cffafe;color:#155e75}.s-PUBLISHED{background:#dcfce7;color:#166534}
.miss{color:#dc2626;font-size:12px}
dialog{border:none;border-radius:14px;max-width:600px;width:94%;padding:0}form,.dlg{padding:20px}h3{margin:0 0 14px}.fld{margin-bottom:10px}.fld label{display:block;font-size:12px;color:var(--mut);margin-bottom:4px}.fld input,.fld select,.fld textarea{width:100%;font:inherit}.rw{display:flex;gap:8px}.rw>*{flex:1}
.modal-actions{display:flex;gap:8px;justify-content:flex-end;margin-top:16px}
.mini{background:#f8fafc;border:1px solid var(--line);border-radius:10px;padding:12px;margin-top:10px}
</style></head><body>
<header><h1>통계·보고 자료 관리</h1><p>항목 정의 → 수집(기관별 제출) → 집계 → 승인 → 공개</p></header>
<div class="wrap">
<div class="stats" id="stats"></div>
<div class="toolbar"><input id="q" placeholder="보고명 검색" style="min-width:200px">
<select id="fstatus"><option value="">전체 상태</option></select><button data-a="reload">조회</button>
<span style="flex:1"></span><button class="btn-primary" data-a="open-reg">+ 보고 생성</button></div>
<table><thead><tr><th>보고명</th><th>분류</th><th>기간</th><th>상태</th><th>제출률</th><th>처리</th></tr></thead><tbody id="rows"></tbody></table>
</div>
<dialog id="reg"><form id="regform"><h3>보고 생성</h3>
<div class="fld"><label>보고명 *</label><input id="r-title" required></div>
<div class="rw"><div class="fld"><label>분류</label><input id="r-cat" placeholder="학사/시설/안전 등"></div><div class="fld"><label>기간</label><input id="r-period" placeholder="2026-09"></div></div>
<div class="fld"><label>마감일</label><input id="r-due" type="date"></div>
<div class="fld"><label>수집 항목 * (한 줄에 하나: 키:이름 또는 키:이름:avg)</label><textarea id="r-fields" rows=3 placeholder="boys:남학생&#10;girls:여학생&#10;score:만족도:avg" required></textarea></div>
<div class="fld"><label>대상 기관 (쉼표 구분, 선택)</label><input id="r-orgs" placeholder="A학교, B학교, C학교"></div>
<div class="modal-actions"><button type="button" data-a="close-reg">취소</button><button class="btn-primary" type="submit">생성</button></div>
</form></dialog>
<dialog id="det"><div class="dlg" id="detBody"></div></dialog>
<script>
var STS={DRAFT:'초안',COLLECTING:'수집중',AGGREGATED:'집계',APPROVED:'승인',PUBLISHED:'공개'};
function esc(s){s=s==null?'':(''+s);return s.replace(/[&<>]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;'}[c];});}
function opt(sel,obj){for(var k in obj){var o=document.createElement('option');o.value=k;o.textContent=obj[k];sel.appendChild(o);}}
opt(document.getElementById('fstatus'),STS);
function jget(u){return fetch(u).then(function(r){return r.json();});}
function jpost(u,b){return fetch(u,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b||{})}).then(function(r){return r.json().then(function(d){return {ok:r.ok,d:d};});});}
function actBtns(o){var arr=[];var id=o.id;
 arr.push(['상세','detail',id]);
 if(o.status==='DRAFT')arr.push(['수집개시','op:open',id]);
 else if(o.status==='COLLECTING')arr.push(['마감','op:close',id]);
 else if(o.status==='AGGREGATED'){arr.push(['승인','op:approve',id]);arr.push(['재개','rs:reopen',id]);}
 else if(o.status==='APPROVED'){arr.push(['공개','op:publish',id]);arr.push(['재개','rs:reopen',id]);}
 else if(o.status==='PUBLISHED')arr.push(['정정재공개','rs:reopen',id]);
 return arr.map(function(a){return '<button class="btn-sm" data-a="'+a[1]+'" data-id="'+a[2]+'">'+a[0]+'</button>';}).join(' ');}
function load(){
 var q=new URLSearchParams();var qq=document.getElementById('q').value.trim();if(qq)q.set('q',qq);
 var st=document.getElementById('fstatus').value;if(st)q.set('status',st);q.set('size','100');
 jget('/api/reports?'+q).then(function(d){var rows=document.getElementById('rows');rows.innerHTML='';
  if(!d.items.length)rows.innerHTML='<tr><td colspan=6 style="text-align:center;color:#94a3b8;padding:30px">보고가 없습니다.</td></tr>';
  d.items.forEach(function(o){var tr=document.createElement('tr');
   var rate=(o.targetOrgs&&o.targetOrgs.length)?(o.submissionRate+'% ('+o.submissionCount+'/'+o.targetOrgs.length+')'):(o.submissionCount+'건');
   tr.innerHTML='<td><b>'+esc(o.title)+'</b></td><td>'+esc(o.category||'')+'</td><td>'+esc(o.period||'')+'</td>'+
    '<td><span class="badge s-'+o.status+'">'+STS[o.status]+'</span></td><td>'+rate+'</td><td>'+actBtns(o)+'</td>';
   rows.appendChild(tr);});});
 jget('/api/stats').then(function(s){var bs=s.byStatus||{};document.getElementById('stats').innerHTML=
  card('전체',s.total+'건')+card('수집중',(bs.COLLECTING||0)+'건')+card('공개',(bs.PUBLISHED||0)+'건')+card('총 제출',s.totalSubmissions+'건');});
}
function card(l,v){return '<div class="card"><div class="lbl">'+l+'</div><div class="val">'+v+'</div></div>';}
function op(id,kind){jpost('/api/reports/'+id+'/'+kind,{actor:'담당자'}).then(function(r){if(!r.ok)alert('오류: '+(r.d.error?r.d.error.message:''));load();if(document.getElementById('det').open)detail(id);});}
function rs(id,kind){var reason=prompt('사유(정정 등)');jpost('/api/reports/'+id+'/'+kind,{reason:reason||'',actor:'담당자'}).then(function(r){if(!r.ok)alert('오류: '+(r.d.error?r.d.error.message:''));load();if(document.getElementById('det').open)detail(id);});}
function agRow(f,ag){var a=ag[f.key]||{};var main=f.type==='avg'?('평균 '+(a.avg!=null?a.avg:'-')+(a.weightedAvg!=null?(' / 가중 '+a.weightedAvg):'')):('합계 '+(a.sum!=null?a.sum:'-'));
 return '<tr><td>'+esc(f.label)+'</td><td>'+(a.count||0)+'</td><td>'+main+'</td><td>'+(a.min!=null?a.min:'-')+'</td><td>'+(a.max!=null?a.max:'-')+'</td></tr>';}
function detail(id){jget('/api/reports/'+id).then(function(o){
 var h='<h3>'+esc(o.title)+' <span class="badge s-'+o.status+'">'+STS[o.status]+'</span></h3>';
 h+='<p style="color:#64748b;font-size:13px;margin:0 0 8px">'+esc(o.category||'')+' · '+esc(o.period||'')+(o.dueDate?(' · 마감 '+o.dueDate):'')+'</p>';
 if(o.targetOrgs&&o.targetOrgs.length){h+='<p style="font-size:13px;margin:0 0 8px">제출률 <b>'+o.submissionRate+'%</b> ('+o.submissionCount+'/'+o.targetOrgs.length+')';
  if(o.missingOrgs&&o.missingOrgs.length)h+=' <span class="miss">미제출: '+o.missingOrgs.map(esc).join(', ')+'</span>';h+='</p>';}
 h+='<div style="font-weight:700;font-size:13px;margin:10px 0 4px">집계</div>';
 h+='<table><thead><tr><th>항목</th><th>건수</th><th>집계</th><th>최소</th><th>최대</th></tr></thead><tbody>';
 o.fields.forEach(function(f){h+=agRow(f,o.aggregate||{});});h+='</tbody></table>';
 if(o.status==='COLLECTING'){h+='<div class="mini"><div style="font-weight:700;font-size:13px;margin-bottom:6px">기관 제출 입력</div>';
  h+='<input id="sf-org" placeholder="기관명" style="width:100%;margin-bottom:6px">';
  o.fields.forEach(function(f){h+='<div class="rw" style="align-items:center;margin-bottom:4px"><label style="flex:1;font-size:12px;color:#64748b">'+esc(f.label)+' ('+f.type+')</label><input id="sf-'+f.key+'" type="number" step="any" style="flex:1"></div>';});
  h+='<button class="btn-primary btn-sm" data-a="submit" data-id="'+id+'">제출 등록</button></div>';}
 h+='<div style="font-weight:700;font-size:13px;margin:12px 0 4px">제출 기관 ('+o.submissionCount+')</div>';
 if(!o.submissions||!o.submissions.length)h+='<p style="color:#94a3b8;font-size:13px;margin:0">제출 없음</p>';
 else h+='<p style="font-size:13px;margin:0">'+o.submissions.map(function(s){return esc(s.org);}).join(', ')+'</p>';
 h+='<div class="modal-actions"><a href="/api/reports/'+id+'/export" style="text-decoration:none"><button type="button">CSV</button></a><button type="button" data-a="close-det">닫기</button></div>';
 document.getElementById('detBody').innerHTML=h;window._rid=id;window._fields=o.fields;if(!document.getElementById('det').open)det.showModal();});}
function doSubmit(id){var org=document.getElementById('sf-org').value.trim();if(!org){alert('기관명을 입력하세요');return;}
 var values={};var fields=window._fields||[];for(var i=0;i<fields.length;i++){var el=document.getElementById('sf-'+fields[i].key);if(el&&el.value!=='')values[fields[i].key]=parseFloat(el.value);}
 jpost('/api/reports/'+id+'/submit',{org:org,values:values,actor:org}).then(function(r){if(!r.ok){alert('오류: '+(r.d.error?r.d.error.message:''));return;}detail(id);load();});}
function submitReg(){
 var lines=document.getElementById('r-fields').value.split('\n');var fields=[];
 for(var i=0;i<lines.length;i++){var t=lines[i].trim();if(!t)continue;var p=t.split(':');if(p.length<2)continue;var fo={key:p[0].trim(),label:p[1].trim()};if(p[2]&&p[2].trim())fo.type=p[2].trim();fields.push(fo);}
 if(!fields.length){alert('수집 항목을 1개 이상 입력하세요 (키:이름)');return;}
 var orgsStr=document.getElementById('r-orgs').value.trim();var orgs=orgsStr?orgsStr.split(',').map(function(x){return x.trim();}).filter(Boolean):[];
 jpost('/api/reports',{title:document.getElementById('r-title').value,category:document.getElementById('r-cat').value,period:document.getElementById('r-period').value,dueDate:document.getElementById('r-due').value,fields:fields,targetOrgs:orgs}).then(function(r){
  if(!r.ok){alert('오류: '+(r.d.error?r.d.error.message:''));return;}reg.close();document.getElementById('r-title').value='';document.getElementById('r-fields').value='';load();});}
document.addEventListener('click',function(e){var t=e.target.closest('[data-a]');if(!t)return;var a=t.getAttribute('data-a');var id=t.getAttribute('data-id');
 if(a==='reload')load();
 else if(a==='open-reg')reg.showModal();
 else if(a==='close-reg')reg.close();
 else if(a==='close-det')det.close();
 else if(a==='detail')detail(id);
 else if(a==='submit')doSubmit(id);
 else if(a.indexOf('op:')===0)op(id,a.slice(3));
 else if(a.indexOf('rs:')===0)rs(id,a.slice(3));});
document.getElementById('regform').addEventListener('submit',function(e){e.preventDefault();submitReg();});
load();
</script></body></html>"""
