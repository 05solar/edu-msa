// staff-trip · 교직원 출장·복무 관리 (Java, Javalin)
// 출장 신청 → 승인 → 정산(여비 자동계산) → 지급 흐름을 상태 전이로 관리한다.
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class App {

    static final ObjectMapper OM = new ObjectMapper();
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 여비 규정(간이): 일비·식비/일, 숙박비 상한/박, 자가용 운임/km
    static final int PER_DIEM = 25000, MEAL = 25000, LODGING_CAP = 70000, CAR_RATE = 262, IN_CITY = 20000;
    static final Set<String> RANKS = Set.of("교사", "부장", "교감", "교장", "주무관", "사무관");
    static final Set<String> TRANSPORT = Set.of("자가용", "대중교통", "관용차");
    static final Set<String> TRIP_TYPES = Set.of("관외", "관내");
    static final List<String> STATUSES = List.of("REQUESTED", "APPROVED", "SETTLED", "PAID", "REJECTED", "CANCELED");

    static String now() { return LocalDateTime.now(KST).format(TS); }

    // ---- 저장소 ----
    static final Object LOCK = new Object();
    static final Map<Integer, Map<String, Object>> TRIPS = new LinkedHashMap<>();
    static int seq = 0;
    static String dataFile = System.getenv().getOrDefault("DATA_FILE", "");

    static class ApiException extends RuntimeException {
        final int status; final String code;
        ApiException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    }

    // ---- 여비 계산 ----
    static Map<String, Object> calcExpense(Map<String, Object> t) {
        long days = ((Number) t.get("days")).longValue();
        long nights = Math.max(0, days - 1);
        String transport = (String) t.get("transport");
        String tripType = (String) t.getOrDefault("tripType", "관외");
        int distance = ((Number) t.getOrDefault("distanceKm", 0)).intValue();
        int fareInput = ((Number) t.getOrDefault("fare", 0)).intValue();

        int fare;
        switch (transport) {
            case "자가용" -> fare = distance * CAR_RATE;
            case "관용차" -> fare = 0;
            default -> fare = fareInput; // 대중교통: 실비
        }
        int perDiem, meal, lodging;
        if ("관내".equals(tripType)) {              // 관내출장: 정액(일비), 식비·숙박 없음
            perDiem = (int) (IN_CITY * days);
            meal = 0;
            lodging = 0;
        } else {
            perDiem = (int) (PER_DIEM * days);
            meal = (int) (MEAL * days);
            Object la = t.get("lodgingActual");     // 정산 시 실비(0=무료숙박 포함), 미정산 시 상한 추정
            lodging = (la instanceof Number n) ? n.intValue() : (int) (nights * LODGING_CAP);
        }
        int total = perDiem + meal + lodging + fare;
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("perDiem", perDiem); e.put("meal", meal); e.put("lodging", lodging);
        e.put("fare", fare); e.put("total", total);
        return e;
    }

    static long computeDays(String start, String end) {
        LocalDate s, en;
        try { s = LocalDate.parse(start); en = LocalDate.parse(end); }
        catch (Exception ex) { throw new ApiException(400, "VALIDATION", "날짜 형식이 올바르지 않습니다(YYYY-MM-DD)."); }
        if (en.isBefore(s)) throw new ApiException(400, "VALIDATION", "종료일이 시작일보다 빠를 수 없습니다.");
        return ChronoUnit.DAYS.between(s, en) + 1;
    }

    // ---- 영속성 ----
    @SuppressWarnings("unchecked")
    static boolean load() {
        try {
            Map<String, Object> snap = OM.readValue(new File(dataFile), Map.class);
            seq = ((Number) snap.getOrDefault("seq", 0)).intValue();
            for (Map<String, Object> t : (List<Map<String, Object>>) snap.getOrDefault("trips", List.of()))
                TRIPS.put(((Number) t.get("id")).intValue(), t);
            return !TRIPS.isEmpty();
        } catch (Exception e) { return false; }
    }

    static void save() {
        if (dataFile.isEmpty()) return;
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("seq", seq);
            snap.put("trips", new ArrayList<>(TRIPS.values()));
            File tmp = new File(dataFile + ".tmp");
            Files.write(tmp.toPath(), OM.writerWithDefaultPrettyPrinter().writeValueAsBytes(snap));
            Files.move(tmp.toPath(), new File(dataFile).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) { System.out.println("save failed: " + e.getMessage()); }
    }

    static void addHist(Map<String, Object> t, String actor, String act, String memo) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> h = (List<Map<String, Object>>) t.get("history");
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("at", now()); e.put("actor", actor == null ? "system" : actor); e.put("act", act);
        if (memo != null && !memo.isBlank()) e.put("memo", memo);
        h.add(e);
        t.put("updatedAt", now());
    }

    @SuppressWarnings("unchecked")
    static void seed() {
        mkTrip("김도현", "교육지원과", "교사", "타 시도 교육과정 워크숍 참석", "세종특별자치시",
                "2026-09-03", "2026-09-04", "대중교통", 0, 38000, "APPROVED", "정우성",
                List.<String[]>of(new String[]{"2026-08-20 09:00:00", "김도현", "신청", ""},
                        new String[]{"2026-08-21 10:00:00", "정우성", "승인", ""}));
        mkTrip("윤민아", "행정지원과", "주무관", "관내 학교 시설 실태 점검", "관내 3개교",
                "2026-08-26", "2026-08-26", "자가용", 46, 0, "REQUESTED", null,
                List.<String[]>of(new String[]{"2026-08-24 14:00:00", "윤민아", "신청", ""}));
        mkTrip("정우성", "교육정책과", "교장", "교장단 정책 협의회", "도교육청",
                "2026-08-18", "2026-08-19", "관용차", 0, 0, "PAID", "이준호",
                List.<String[]>of(new String[]{"2026-08-10 09:00:00", "정우성", "신청", ""},
                        new String[]{"2026-08-11 09:00:00", "이준호", "승인", ""},
                        new String[]{"2026-08-20 09:00:00", "행정실", "정산", ""},
                        new String[]{"2026-08-21 09:00:00", "행정실", "지급", ""}));
    }

    static void mkTrip(String applicant, String dept, String rank, String purpose, String dest,
                       String start, String end, String transport, int distance, int fare,
                       String status, String approver, List<String[]> hist) {
        long days = computeDays(start, end);
        Map<String, Object> t = new LinkedHashMap<>();
        int id = ++seq;
        t.put("id", id); t.put("applicant", applicant); t.put("department", dept); t.put("rank", rank);
        t.put("purpose", purpose); t.put("destination", dest);
        t.put("startDate", start); t.put("endDate", end); t.put("days", days);
        t.put("transport", transport); t.put("distanceKm", distance); t.put("fare", fare);
        t.put("tripType", "관외"); t.put("lodgingActual", null); t.put("status", status); t.put("approver", approver);
        t.put("createdAt", now()); t.put("updatedAt", now());
        List<Map<String, Object>> h = new ArrayList<>();
        for (String[] e : hist) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", e[0]); m.put("actor", e[1]); m.put("act", e[2]);
            if (!e[3].isBlank()) m.put("memo", e[3]);
            h.add(m);
        }
        t.put("history", h);
        t.put("expense", calcExpense(t));
        TRIPS.put(id, t);
    }

    // ---- 요청 헬퍼 ----
    @SuppressWarnings("unchecked")
    static Map<String, Object> body(Context ctx) {
        try {
            String b = ctx.body();
            if (b == null || b.isBlank()) return new HashMap<>();
            return OM.readValue(b, Map.class);
        } catch (Exception e) { throw new ApiException(400, "INVALID_JSON", "요청 본문을 해석할 수 없습니다."); }
    }

    static String str(Map<String, Object> b, String k) {
        Object v = b.get(k);
        return v == null ? "" : v.toString().trim();
    }

    static Map<String, Object> trip(Context ctx) {
        int id;
        try { id = Integer.parseInt(ctx.pathParam("id")); }
        catch (NumberFormatException e) { throw new ApiException(400, "VALIDATION", "id가 올바르지 않습니다."); }
        Map<String, Object> t = TRIPS.get(id);
        if (t == null) throw new ApiException(404, "NOT_FOUND", "출장 " + id + " 를 찾을 수 없습니다.");
        return t;
    }

    static String actor(Map<String, Object> b) {
        String a = str(b, "actor");
        return a.isEmpty() ? null : a;
    }

    public static void main(String[] args) {
        synchronized (LOCK) {
            if (!dataFile.isEmpty() && new File(dataFile).exists()) {
                if (!load()) { System.out.println("경고: 로드 실패 — 인메모리 시드"); seed(); }
                else System.out.println("loaded " + TRIPS.size() + " trips from " + dataFile);
            } else { seed(); save(); }
        }
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Javalin app = Javalin.create(cfg -> cfg.showJavalinBanner = false);

        app.exception(ApiException.class, (e, ctx) -> ctx.status(e.status).json(errMap(e.code, e.getMessage())));
        app.exception(Exception.class, (e, ctx) -> ctx.status(500).json(errMap("INTERNAL", String.valueOf(e.getMessage()))));

        app.get("/healthz", ctx -> ctx.json(Map.of("status", "ok", "service", "staff-trip", "time", now())));
        app.get("/", ctx -> ctx.html(INDEX));
        app.get("/api/trips", App::list);
        app.post("/api/trips", App::create);
        app.get("/api/trips/{id}", ctx -> { synchronized (LOCK) { ctx.json(trip(ctx)); } });
        app.get("/api/trips/{id}/history", ctx -> { synchronized (LOCK) { var t = trip(ctx); ctx.json(Map.of("id", t.get("id"), "history", t.get("history"))); } });
        app.patch("/api/trips/{id}", App::edit);
        app.post("/api/trips/{id}/approve", App::approve);
        app.post("/api/trips/{id}/reject", App::reject);
        app.post("/api/trips/{id}/cancel", App::cancel);
        app.post("/api/trips/{id}/settle", App::settle);
        app.post("/api/trips/{id}/pay", App::pay);
        app.post("/api/trips/{id}/return", App::ret);
        app.get("/api/stats", App::stats);

        app.start(port);
        System.out.println("staff-trip listening on :" + port);
    }

    static Map<String, Object> errMap(String code, String msg) {
        return Map.of("error", Map.of("code", code, "message", msg));
    }

    // ---- 핸들러 ----
    static void list(Context ctx) {
        String status = up(ctx.queryParam("status"));
        String applicant = nz(ctx.queryParam("applicant"));
        String dept = nz(ctx.queryParam("department"));
        String kw = nz(ctx.queryParam("q"));
        int page = parseIntDef(ctx.queryParam("page"), 1); if (page < 1) page = 1;
        int size = parseIntDef(ctx.queryParam("size"), 10); if (size < 1 || size > 100) size = 10;
        synchronized (LOCK) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> t : TRIPS.values()) {
                if (!status.isEmpty() && !t.get("status").equals(status)) continue;
                if (!applicant.isEmpty() && !applicant.equals(t.get("applicant"))) continue;
                if (!dept.isEmpty() && !dept.equals(t.get("department"))) continue;
                if (!kw.isEmpty() && !((String) t.get("purpose")).contains(kw)
                        && !((String) t.get("destination")).contains(kw)
                        && !((String) t.get("applicant")).contains(kw)) continue;
                items.add(t);
            }
            items.sort((a, b) -> ((Number) b.get("id")).intValue() - ((Number) a.get("id")).intValue());
            int total = items.size();
            int start = Math.min((page - 1) * size, total);
            int end = Math.min(start + size, total);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("page", page); res.put("size", size); res.put("total", total);
            res.put("items", items.subList(start, end));
            ctx.json(res);
        }
    }

    static void create(Context ctx) {
        Map<String, Object> b = body(ctx);
        String applicant = str(b, "applicant"), purpose = str(b, "purpose"), dest = str(b, "destination");
        String start = str(b, "startDate"), end = str(b, "endDate");
        if (applicant.isEmpty() || purpose.isEmpty() || start.isEmpty() || end.isEmpty())
            throw new ApiException(400, "VALIDATION", "applicant, purpose, startDate, endDate는 필수입니다.");
        String rank = str(b, "rank"); if (rank.isEmpty()) rank = "교사";
        if (!RANKS.contains(rank)) throw new ApiException(400, "VALIDATION", "rank가 올바르지 않습니다: " + RANKS);
        String transport = str(b, "transport"); if (transport.isEmpty()) transport = "대중교통";
        if (!TRANSPORT.contains(transport)) throw new ApiException(400, "VALIDATION", "transport는 자가용/대중교통/관용차 중 하나여야 합니다.");
        String tripType = str(b, "tripType"); if (tripType.isEmpty()) tripType = "관외";
        if (!TRIP_TYPES.contains(tripType)) throw new ApiException(400, "VALIDATION", "tripType은 관외/관내 중 하나여야 합니다.");
        long days = computeDays(start, end); // 날짜 검증 포함
        int distance = num(b, "distanceKm"); int fare = num(b, "fare");
        if (distance < 0 || fare < 0) throw new ApiException(400, "VALIDATION", "distanceKm/fare는 0 이상이어야 합니다.");
        synchronized (LOCK) {
            Map<String, Object> t = new LinkedHashMap<>();
            int id = ++seq;
            t.put("id", id); t.put("applicant", applicant); t.put("department", str(b, "department"));
            t.put("rank", rank); t.put("purpose", purpose); t.put("destination", dest);
            t.put("startDate", start); t.put("endDate", end); t.put("days", days);
            t.put("transport", transport); t.put("distanceKm", distance); t.put("fare", fare);
            t.put("tripType", tripType); t.put("lodgingActual", null); t.put("status", "REQUESTED"); t.put("approver", null);
            t.put("createdAt", now()); t.put("updatedAt", now());
            List<Map<String, Object>> h = new ArrayList<>();
            t.put("history", h);
            addHist(t, applicant, "신청", null);
            t.put("expense", calcExpense(t));
            TRIPS.put(id, t);
            save();
            ctx.status(201).json(t);
        }
    }

    static void edit(Context ctx) {
        Map<String, Object> b = body(ctx);
        synchronized (LOCK) {
            Map<String, Object> t = trip(ctx);
            if (!t.get("status").equals("REQUESTED") && !t.get("status").equals("REJECTED"))
                throw new ApiException(409, "INVALID_STATE", "신청/반려 상태의 출장만 수정할 수 있습니다.");
            if (b.containsKey("purpose")) t.put("purpose", str(b, "purpose"));
            if (b.containsKey("destination")) t.put("destination", str(b, "destination"));
            if (b.containsKey("rank")) {
                String r = str(b, "rank");
                if (!RANKS.contains(r)) throw new ApiException(400, "VALIDATION", "rank가 올바르지 않습니다.");
                t.put("rank", r);
            }
            if (b.containsKey("transport")) {
                String tr = str(b, "transport");
                if (!TRANSPORT.contains(tr)) throw new ApiException(400, "VALIDATION", "transport가 올바르지 않습니다.");
                t.put("transport", tr);
            }
            if (b.containsKey("tripType")) {
                String tt = str(b, "tripType");
                if (!TRIP_TYPES.contains(tt)) throw new ApiException(400, "VALIDATION", "tripType이 올바르지 않습니다.");
                t.put("tripType", tt);
            }
            if (b.containsKey("distanceKm")) t.put("distanceKm", Math.max(0, num(b, "distanceKm")));
            if (b.containsKey("fare")) t.put("fare", Math.max(0, num(b, "fare")));
            if (b.containsKey("startDate") || b.containsKey("endDate")) {
                String s = b.containsKey("startDate") ? str(b, "startDate") : (String) t.get("startDate");
                String e = b.containsKey("endDate") ? str(b, "endDate") : (String) t.get("endDate");
                t.put("days", computeDays(s, e));
                t.put("startDate", s); t.put("endDate", e);
            }
            t.put("status", "REQUESTED"); // 반려건 수정 시 재신청 가능
            t.put("expense", calcExpense(t));
            addHist(t, actor(b) == null ? (String) t.get("applicant") : actor(b), "수정", null);
            save();
            ctx.json(t);
        }
    }

    static void approve(Context ctx) {
        Map<String, Object> b = body(ctx);
        synchronized (LOCK) {
            Map<String, Object> t = trip(ctx);
            if (!t.get("status").equals("REQUESTED"))
                throw new ApiException(409, "INVALID_STATE", "신청 상태의 출장만 승인할 수 있습니다.");
            String ap = actor(b);
            if (ap == null) throw new ApiException(400, "VALIDATION", "승인자(actor)는 필수입니다.");
            t.put("status", "APPROVED"); t.put("approver", ap);
            addHist(t, ap, "승인", null);
            save();
            ctx.json(t);
        }
    }

    static void reject(Context ctx) {
        Map<String, Object> b = body(ctx);
        String reason = str(b, "reason");
        if (reason.isEmpty()) throw new ApiException(400, "VALIDATION", "반려 사유(reason)는 필수입니다.");
        synchronized (LOCK) {
            Map<String, Object> t = trip(ctx);
            if (!t.get("status").equals("REQUESTED"))
                throw new ApiException(409, "INVALID_STATE", "신청 상태의 출장만 반려할 수 있습니다.");
            t.put("status", "REJECTED");
            addHist(t, actor(b), "반려", reason);
            save();
            ctx.json(t);
        }
    }

    static void cancel(Context ctx) {
        Map<String, Object> b = body(ctx);
        synchronized (LOCK) {
            Map<String, Object> t = trip(ctx);
            if (!t.get("status").equals("REQUESTED") && !t.get("status").equals("APPROVED"))
                throw new ApiException(409, "INVALID_STATE", "신청/승인 상태의 출장만 취소할 수 있습니다.");
            t.put("status", "CANCELED");
            addHist(t, actor(b), "취소", str(b, "reason"));
            save();
            ctx.json(t);
        }
    }

    static void settle(Context ctx) {
        Map<String, Object> b = body(ctx);
        synchronized (LOCK) {
            Map<String, Object> t = trip(ctx);
            if (!t.get("status").equals("APPROVED"))
                throw new ApiException(409, "INVALID_STATE", "승인된 출장만 정산할 수 있습니다.");
            // 실비 반영(대중교통 운임/숙박 실비)
            if (b.containsKey("fare")) t.put("fare", Math.max(0, num(b, "fare")));
            if (b.containsKey("lodgingActual")) t.put("lodgingActual", Math.max(0, num(b, "lodgingActual")));
            t.put("expense", calcExpense(t));
            t.put("status", "SETTLED");
            addHist(t, actor(b), "정산", "여비 " + ((Map<?, ?>) t.get("expense")).get("total") + "원");
            save();
            ctx.json(t);
        }
    }

    static void pay(Context ctx) {
        Map<String, Object> b = body(ctx);
        synchronized (LOCK) {
            Map<String, Object> t = trip(ctx);
            if (!t.get("status").equals("SETTLED"))
                throw new ApiException(409, "INVALID_STATE", "정산된 출장만 지급할 수 있습니다.");
            t.put("status", "PAID");
            addHist(t, actor(b), "지급", null);
            save();
            ctx.json(t);
        }
    }

    static void ret(Context ctx) { // 반송: 승인→신청 / 정산→승인
        Map<String, Object> b = body(ctx);
        String reason = str(b, "reason");
        synchronized (LOCK) {
            Map<String, Object> t = trip(ctx);
            String st = (String) t.get("status");
            if (st.equals("APPROVED")) {
                t.put("status", "REQUESTED"); t.put("approver", null);
                addHist(t, actor(b), "승인반송", reason);
            } else if (st.equals("SETTLED")) {
                t.put("status", "APPROVED");
                addHist(t, actor(b), "정산반송", reason);
            } else {
                throw new ApiException(409, "INVALID_STATE", "승인/정산 상태의 출장만 반송할 수 있습니다.");
            }
            save();
            ctx.json(t);
        }
    }

    static void stats(Context ctx) {
        synchronized (LOCK) {
            Map<String, Integer> byStatus = new LinkedHashMap<>();
            for (String s : STATUSES) byStatus.put(s, 0);
            Map<String, Integer> byDept = new LinkedHashMap<>();
            long paidTotal = 0;
            for (Map<String, Object> t : TRIPS.values()) {
                String st = (String) t.get("status");
                byStatus.merge(st, 1, Integer::sum);
                byDept.merge((String) t.getOrDefault("department", ""), 1, Integer::sum);
                if (st.equals("PAID") || st.equals("SETTLED"))
                    paidTotal += ((Number) ((Map<?, ?>) t.get("expense")).get("total")).longValue();
            }
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("total", TRIPS.size()); res.put("byStatus", byStatus);
            res.put("byDepartment", byDept); res.put("settledOrPaidExpense", paidTotal);
            ctx.json(res);
        }
    }

    static String up(String s) { return s == null ? "" : s.trim().toUpperCase(); }
    static String nz(String s) { return s == null ? "" : s.trim(); }
    static int parseIntDef(String s, int d) { try { return Integer.parseInt(s); } catch (Exception e) { return d; } }
    static int num(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    static final String INDEX = """
<!doctype html><html lang=ko><head><meta charset=utf-8>
<meta name=viewport content="width=device-width, initial-scale=1"><title>교직원 출장·복무 관리</title>
<style>
:root{--line:#e2e8f0;--ink:#1e293b;--mut:#64748b;--blue:#2563eb;--bg:#f8fafc}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,'Malgun Gothic',sans-serif;color:var(--ink);background:var(--bg)}
header{background:#fff;border-bottom:1px solid var(--line);padding:16px 24px}header h1{font-size:20px;margin:0}header p{margin:4px 0 0;color:var(--mut);font-size:13px}
.wrap{max-width:1160px;margin:0 auto;padding:20px 24px}
.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:18px}
.card{background:#fff;border:1px solid var(--line);border-radius:12px;padding:14px}.card .lbl{font-size:12px;color:var(--mut)}.card .val{font-size:22px;font-weight:800;margin-top:4px}
.toolbar{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:12px}
input,select,button{font:inherit;padding:8px 10px;border:1px solid var(--line);border-radius:8px;background:#fff;color:var(--ink)}button{cursor:pointer}.btn-primary{background:var(--blue);color:#fff;border-color:var(--blue);font-weight:600}.btn-sm{padding:4px 8px;font-size:12px}
table{width:100%;border-collapse:collapse;background:#fff;border:1px solid var(--line);border-radius:12px;overflow:hidden;font-size:13px}
th,td{text-align:left;padding:10px 12px;border-bottom:1px solid var(--line)}th{background:#f1f5f9;color:var(--mut);font-size:11px}tr:last-child td{border-bottom:none}
.badge{display:inline-block;padding:2px 8px;border-radius:999px;font-size:11px;font-weight:700}
.s-REQUESTED{background:#e0e7ff;color:#3730a3}.s-APPROVED{background:#dbeafe;color:#1e40af}.s-SETTLED{background:#fef3c7;color:#92400e}.s-PAID{background:#dcfce7;color:#166534}.s-REJECTED{background:#fee2e2;color:#991b1b}.s-CANCELED{background:#e2e8f0;color:#475569}
dialog{border:none;border-radius:14px;max-width:520px;width:94%;padding:0}form{padding:20px}form h3{margin:0 0 14px}.fld{margin-bottom:10px}.fld label{display:block;font-size:12px;color:var(--mut);margin-bottom:4px}.fld input,.fld select{width:100%;font:inherit}.rw{display:flex;gap:8px}.rw>*{flex:1}
.modal-actions{display:flex;gap:8px;justify-content:flex-end;margin-top:16px}
</style></head><body>
<header><h1>교직원 출장·복무 관리</h1><p>신청 → 승인 → 정산(여비 자동계산) → 지급</p></header>
<div class="wrap">
<div class="stats" id="stats"></div>
<div class="toolbar"><input id="q" placeholder="목적·출장지·신청자 검색" style="min-width:200px">
<select id="fstatus"><option value="">전체 상태</option></select><button onclick="load()">조회</button>
<span style="flex:1"></span><button class="btn-primary" onclick="reg.showModal()">+ 출장 신청</button></div>
<table><thead><tr><th>신청자</th><th>목적</th><th>출장지</th><th>기간</th><th>교통</th><th>상태</th><th>여비</th><th>처리</th></tr></thead><tbody id="rows"></tbody></table>
</div>
<dialog id="reg"><form onsubmit="return submitReg(event)"><h3>출장 신청</h3>
<div class="rw"><div class="fld"><label>신청자 *</label><input id="r-app" required></div><div class="fld"><label>부서</label><input id="r-dept"></div></div>
<div class="fld"><label>출장 목적 *</label><input id="r-purpose" required></div>
<div class="fld"><label>출장지 *</label><input id="r-dest" required></div>
<div class="rw"><div class="fld"><label>시작일 *</label><input id="r-start" type="date" required></div><div class="fld"><label>종료일 *</label><input id="r-end" type="date" required></div></div>
<div class="rw"><div class="fld"><label>교통수단</label><select id="r-trans"></select></div><div class="fld"><label>거리(km)</label><input id="r-dist" type="number" value="0"></div><div class="fld"><label>운임(원)</label><input id="r-fare" type="number" value="0"></div></div>
<div class="modal-actions"><button type="button" onclick="reg.close()">취소</button><button class="btn-primary" type="submit">신청</button></div>
</form></dialog>
<script>
var TRANS=['자가용','대중교통','관용차'];
var STS={REQUESTED:'신청',APPROVED:'승인',SETTLED:'정산',PAID:'지급완료',REJECTED:'반려',CANCELED:'취소'};
function esc(s){s=s==null?'':(''+s);return s.replace(/[&<>]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;'}[c];});}
function won(n){return (n||0).toLocaleString('ko')+'원';}
function opt(sel,arr,lbl){for(var i=0;i<arr.length;i++){var o=document.createElement('option');o.value=arr[i];o.textContent=lbl?(lbl[arr[i]]||arr[i]):arr[i];sel.appendChild(o);}}
opt(document.getElementById('fstatus'),Object.keys(STS),STS);opt(document.getElementById('r-trans'),TRANS);
function jget(u){return fetch(u).then(function(r){return r.json();});}
function jpost(u,b){return fetch(u,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b||{})}).then(function(r){return r.json().then(function(d){return {ok:r.ok,d:d};});});}
function acts(o){var b=function(t,f){return '<button class="btn-sm" onclick="'+f+'">'+t+'</button> ';};var id=o.id;
 if(o.status==='REQUESTED')return b('승인',"approve("+id+")")+b('반려',"reason("+id+",'reject')")+b('취소',"act("+id+",'cancel')");
 if(o.status==='APPROVED')return b('정산',"settle("+id+")")+b('반송',"act("+id+",'return')")+b('취소',"act("+id+",'cancel')");
 if(o.status==='SETTLED')return b('지급',"act("+id+",'pay')")+b('반송',"act("+id+",'return')");
 return '-';}
function load(){
 var q=new URLSearchParams();var qq=document.getElementById('q').value.trim();if(qq)q.set('q',qq);
 var st=document.getElementById('fstatus').value;if(st)q.set('status',st);q.set('size','100');
 jget('/api/trips?'+q).then(function(d){var rows=document.getElementById('rows');rows.innerHTML='';
  if(!d.items.length)rows.innerHTML='<tr><td colspan=8 style="text-align:center;color:#94a3b8;padding:30px">출장 내역이 없습니다.</td></tr>';
  d.items.forEach(function(o){var tr=document.createElement('tr');var e=o.expense||{};
   tr.innerHTML='<td><b>'+esc(o.applicant)+'</b><br><span style=color:#94a3b8;font-size:11px>'+esc(o.department||'')+'</span></td><td>'+esc(o.purpose)+'</td><td>'+esc(o.destination)+'</td>'+
    '<td style=font-size:12px>'+(o.startDate||'').slice(5)+'~'+(o.endDate||'').slice(5)+'<br><span style=color:#94a3b8>'+o.days+'일</span></td><td>'+o.transport+'</td>'+
    '<td><span class="badge s-'+o.status+'">'+STS[o.status]+'</span></td><td style=text-align:right>'+won(e.total)+'</td><td>'+acts(o)+'</td>';
   rows.appendChild(tr);});});
 jget('/api/stats').then(function(s){var bs=s.byStatus||{};document.getElementById('stats').innerHTML=
  card('전체',s.total+'건')+card('승인대기',(bs.REQUESTED||0)+'건')+card('지급완료',(bs.PAID||0)+'건')+card('지급 여비',won(s.paidTotal));});
}
function card(l,v){return '<div class="card"><div class="lbl">'+l+'</div><div class="val" style=font-size:20px>'+v+'</div></div>';}
function act(id,kind){jpost('/api/trips/'+id+'/'+kind,{actor:'담당자'}).then(function(r){if(!r.ok)alert('오류: '+(r.d.error?r.d.error.message:''));load();});}
function approve(id){var a=prompt('결재자(승인자)');if(!a)return;jpost('/api/trips/'+id+'/approve',{approver:a,actor:a}).then(function(r){if(!r.ok)alert('오류: '+(r.d.error?r.d.error.message:''));load();});}
function reason(id,kind){var r=prompt('사유');if(!r)return;jpost('/api/trips/'+id+'/'+kind,{reason:r,actor:'결재자'}).then(function(x){if(!x.ok)alert('오류: '+(x.d.error?x.d.error.message:''));load();});}
function settle(id){var la=prompt('실 숙박비(원) — 무료숙박은 0, 상한 적용은 빈칸','');var body={actor:'담당자'};if(la!=='' && la!==null)body.lodgingActual=parseInt(la,10);jpost('/api/trips/'+id+'/settle',body).then(function(r){if(!r.ok)alert('오류: '+(r.d.error?r.d.error.message:''));load();});}
function submitReg(e){e.preventDefault();
 jpost('/api/trips',{applicant:document.getElementById('r-app').value,department:document.getElementById('r-dept').value,purpose:document.getElementById('r-purpose').value,destination:document.getElementById('r-dest').value,startDate:document.getElementById('r-start').value,endDate:document.getElementById('r-end').value,transport:document.getElementById('r-trans').value,distanceKm:parseInt(document.getElementById('r-dist').value||'0',10),fare:parseInt(document.getElementById('r-fare').value||'0',10)}).then(function(r){
  if(!r.ok){alert('오류: '+(r.d.error?r.d.error.message:''));return;}reg.close();document.getElementById('r-app').value='';load();});return false;}
load();
</script></body></html>
""";
}
