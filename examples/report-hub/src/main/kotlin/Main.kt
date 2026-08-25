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

const val INDEX = """<!doctype html><html lang=ko><meta charset=utf-8>
<title>통계·보고 관리 · report-hub</title>
<style>body{font-family:system-ui,'Malgun Gothic',sans-serif;max-width:820px;margin:32px auto;padding:0 16px;color:#1e293b}
h1{font-size:22px}code{background:#eef2f8;padding:2px 6px;border-radius:5px}li{margin:5px 0}</style>
<h1>통계/보고 자료 관리 (report-hub)</h1>
<p>보고 항목 정의 → 수집(기관별 제출) → 집계 → 승인 → 공개 흐름을 관리합니다.</p><ul>
<li><code>GET /healthz</code></li>
<li><code>GET /api/reports?status=&category=&q=&page=&size=</code></li>
<li><code>POST /api/reports</code> 보고 생성(fields 정의) · <code>PATCH</code> 수정</li>
<li><code>POST /api/reports/{id}/open|submit|close|approve|publish|reopen</code></li>
<li><code>GET /api/reports/{id}/aggregate|submissions|history|export</code> · <code>GET /api/stats</code></li>
</ul><p>집계는 항목별 합계·평균·최소·최대·건수 자동 산출. 샘플 3건 시드. 배포 경로 <code>/svc/report-hub</code>.</p></html>"""
