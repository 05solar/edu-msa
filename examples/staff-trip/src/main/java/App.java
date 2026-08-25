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

    static final String INDEX = "<!doctype html><html lang=ko><meta charset=utf-8>"
            + "<title>출장·복무 · staff-trip</title>"
            + "<style>body{font-family:system-ui,'Malgun Gothic',sans-serif;max-width:820px;margin:32px auto;padding:0 16px;color:#1e293b}"
            + "h1{font-size:22px}code{background:#eef2f8;padding:2px 6px;border-radius:5px}li{margin:5px 0}</style>"
            + "<h1>교직원 출장·복무 관리 (staff-trip)</h1>"
            + "<p>신청 → 승인 → 정산(여비 자동계산) → 지급 흐름을 관리합니다.</p><ul>"
            + "<li><code>GET /healthz</code></li>"
            + "<li><code>GET /api/trips?status=&applicant=&department=&q=&page=&size=</code></li>"
            + "<li><code>POST /api/trips</code> 신청 · <code>PATCH /api/trips/{id}</code> 수정</li>"
            + "<li><code>POST /api/trips/{id}/approve|reject|cancel|settle|pay|return</code></li>"
            + "<li><code>GET /api/trips/{id}/history</code> · <code>GET /api/stats</code></li>"
            + "</ul><p>여비=일비·식비(일)+숙박비(박)+운임(자가용 km*262/대중교통 실비/관용차 0). 샘플 3건 시드.</p>"
            + "<p>배포 경로 <code>/svc/staff-trip</code>.</p></html>";
}
