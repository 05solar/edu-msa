// asset-mgr · 교육 기자재·자산 관리 (C#, .NET minimal API)
// 등록 → 불출/배치 → 이관 → 수리 → 폐기 생애주기 + 감가상각(정액법) + 재물조사.
using System.Globalization;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

var builder = WebApplication.CreateBuilder(args);
builder.Logging.ClearProviders();
builder.Services.ConfigureHttpJsonOptions(o =>
{
    o.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
    o.SerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.Never;
});
var port = int.TryParse(Environment.GetEnvironmentVariable("PORT"), out var p) ? p : 8080;
builder.WebHost.UseUrls($"http://0.0.0.0:{port}");

var app = builder.Build();
Store.Init();

IResult Err(int status, string code, string msg) =>
    Results.Json(new { error = new { code, message = msg } }, statusCode: status);

app.MapGet("/healthz", () => Results.Json(new { status = "ok", service = "asset-mgr", time = Util.Now() }));
app.MapGet("/", () => Results.Content(Html.Index, "text/html"));

app.MapGet("/api/assets", (HttpRequest req) =>
{
    var q = req.Query;
    string status = (q["status"].ToString() ?? "").Trim().ToUpper();
    string category = (q["category"].ToString() ?? "").Trim();
    string location = (q["location"].ToString() ?? "").Trim();
    string custodian = (q["custodian"].ToString() ?? "").Trim();
    string kw = (q["q"].ToString() ?? "").Trim();
    int page = int.TryParse(q["page"], out var pg) && pg > 0 ? pg : 1;
    int size = int.TryParse(q["size"], out var sz) && sz is > 0 and <= 100 ? sz : 10;
    string sort = (q["sort"].ToString() ?? "").Trim();
    lock (Store.Lock)
    {
        var items = Store.Assets.Values.Where(a =>
            (status == "" || a.Status == status) &&
            (category == "" || a.Category == category) &&
            (location == "" || a.Location == location) &&
            (custodian == "" || a.Custodian == custodian) &&
            (kw == "" || a.Name.Contains(kw) || a.AssetNo.Contains(kw) || (a.Notes ?? "").Contains(kw))).ToList();
        items = sort switch
        {
            "value" => items.OrderByDescending(a => a.CurrentValue).ToList(),
            "acquired" => items.OrderBy(a => a.AcquiredDate).ToList(),
            _ => items.OrderByDescending(a => a.Id).ToList(),
        };
        int total = items.Count;
        var pageItems = items.Skip((page - 1) * size).Take(size).ToList();
        return Results.Json(new { page, size, total, items = pageItems });
    }
});

app.MapPost("/api/assets", async (HttpContext ctx) =>
{
    var b = await Util.Body(ctx);
    if (b == null) return Err(400, "INVALID_JSON", "요청 본문을 해석할 수 없습니다.");
    string name = Util.S(b, "name");
    if (name == "") return Err(400, "VALIDATION", "name은 필수입니다.");
    if (name.Length > 120) return Err(400, "VALIDATION", "name은 120자 이하여야 합니다.");
    string category = Util.S(b, "category"); if (category == "") category = "기타";
    if (!Store.Categories.Contains(category)) return Err(400, "VALIDATION", $"category는 {string.Join('/', Store.Categories)} 중 하나여야 합니다.");
    long cost = Util.L(b, "acquiredCost");
    if (cost < 0) return Err(400, "VALIDATION", "acquiredCost는 0 이상이어야 합니다.");
    int life = (int)Util.L(b, "usefulLifeYears"); if (life <= 0) life = Store.DefaultLife.GetValueOrDefault(category, 5);
    int qty = (int)Util.L(b, "quantity"); if (qty <= 0) qty = 1;
    string acquired = Util.S(b, "acquiredDate"); if (acquired == "") acquired = Util.Today();
    if (!Util.ValidDate(acquired)) return Err(400, "VALIDATION", "acquiredDate 형식이 올바르지 않습니다(YYYY-MM-DD).");
    if (Util.IsFuture(acquired)) return Err(400, "VALIDATION", "acquiredDate는 미래일 수 없습니다.");
    lock (Store.Lock)
    {
        int id = ++Store.Seq;
        var a = new Asset
        {
            Id = id, AssetNo = Util.AssetNo(acquired, id), Name = name, Category = category,
            AcquiredDate = acquired, AcquiredCost = cost, UsefulLifeYears = life, Quantity = qty,
            Location = Util.SN(b, "location"), Custodian = Util.SN(b, "custodian"),
            Status = "IN_STORAGE", Notes = Util.SN(b, "notes"), Model = Util.SN(b, "model"),
            AcquisitionMethod = Util.SN(b, "acquisitionMethod"), Supplier = Util.SN(b, "supplier"),
            BudgetAccount = Util.SN(b, "budgetAccount"),
        };
        a.Hist(Util.Actor(b) ?? "system", "등록", null);
        Store.Assets[id] = a;
        Store.Save();
        return Results.Json(a, statusCode: 201);
    }
});

IResult WithAsset(string idRaw, Func<Asset, IResult> fn)
{
    if (!int.TryParse(idRaw, out var id)) return Err(400, "VALIDATION", "id가 올바르지 않습니다.");
    lock (Store.Lock)
    {
        if (!Store.Assets.TryGetValue(id, out var a)) return Err(404, "NOT_FOUND", $"자산 {idRaw} 를 찾을 수 없습니다.");
        return fn(a);
    }
}

app.MapGet("/api/assets/{id}", (string id) => WithAsset(id, a => Results.Json(a)));
app.MapGet("/api/assets/{id}/history", (string id) => WithAsset(id, a => Results.Json(new { id = a.Id, history = a.History })));

app.MapPatch("/api/assets/{id}", async (string id, HttpContext ctx) =>
{
    var b = await Util.Body(ctx);
    if (b == null) return Err(400, "INVALID_JSON", "요청 본문을 해석할 수 없습니다.");
    return WithAsset(id, a =>
    {
        if (a.Status == "DISPOSED") return Err(409, "INVALID_STATE", "폐기된 자산은 수정할 수 없습니다.");
        if (Util.Has(b, "name")) { var n = Util.S(b, "name"); if (n == "" || n.Length > 120) return Err(400, "VALIDATION", "name은 1~120자"); a.Name = n; }
        if (Util.Has(b, "category")) { var c = Util.S(b, "category"); if (!Store.Categories.Contains(c)) return Err(400, "VALIDATION", "category 오류"); a.Category = c; }
        if (Util.Has(b, "usefulLifeYears")) { var l = (int)Util.L(b, "usefulLifeYears"); if (l <= 0) return Err(400, "VALIDATION", "usefulLifeYears는 1 이상"); a.UsefulLifeYears = l; }
        if (Util.Has(b, "notes")) a.Notes = Util.SN(b, "notes");
        if (Util.Has(b, "location")) a.Location = Util.SN(b, "location");
        if (Util.Has(b, "model")) a.Model = Util.SN(b, "model");
        if (Util.Has(b, "supplier")) a.Supplier = Util.SN(b, "supplier");
        if (Util.Has(b, "acquisitionMethod")) a.AcquisitionMethod = Util.SN(b, "acquisitionMethod");
        if (Util.Has(b, "budgetAccount")) a.BudgetAccount = Util.SN(b, "budgetAccount");
        if (Util.Has(b, "quantity")) { var qn = (int)Util.L(b, "quantity"); if (qn <= 0) return Err(400, "VALIDATION", "quantity는 1 이상이어야 합니다."); a.Quantity = qn; }
        a.Hist(Util.Actor(b), "수정", null);
        Store.Save();
        return Results.Json(a);
    });
});

app.MapPost("/api/assets/{id}/assign", async (string id, HttpContext ctx) =>
{
    var b = await Util.Body(ctx); if (b == null) return Err(400, "INVALID_JSON", "본문 오류");
    return WithAsset(id, a =>
    {
        string custodian = Util.S(b, "custodian"), location = Util.S(b, "location");
        if (custodian == "" || location == "") return Err(400, "VALIDATION", "custodian, location은 필수입니다.");
        if (a.Status is not ("IN_STORAGE" or "IN_USE")) return Err(409, "INVALID_STATE", "보관/사용중 자산만 배치할 수 있습니다.");
        a.Custodian = custodian; a.Location = location; a.Status = "IN_USE";
        a.Hist(Util.Actor(b), "배치", $"{location} / {custodian}");
        Store.Save(); return Results.Json(a);
    });
});

app.MapPost("/api/assets/{id}/transfer", async (string id, HttpContext ctx) =>
{
    var b = await Util.Body(ctx); if (b == null) return Err(400, "INVALID_JSON", "본문 오류");
    return WithAsset(id, a =>
    {
        string custodian = Util.S(b, "custodian"), location = Util.S(b, "location");
        if (custodian == "" && location == "") return Err(400, "VALIDATION", "custodian 또는 location 중 하나는 필요합니다.");
        if (a.Status != "IN_USE") return Err(409, "INVALID_STATE", "사용중 자산만 이관할 수 있습니다.");
        var from = $"{a.Location}/{a.Custodian}";
        if (location != "") a.Location = location;
        if (custodian != "") a.Custodian = custodian;
        a.Hist(Util.Actor(b), "이관", $"{from} → {a.Location}/{a.Custodian}");
        Store.Save(); return Results.Json(a);
    });
});

app.MapPost("/api/assets/{id}/repair", async (string id, HttpContext ctx) =>
{
    var b = await Util.Body(ctx); if (b == null) return Err(400, "INVALID_JSON", "본문 오류");
    return WithAsset(id, a =>
    {
        string reason = Util.S(b, "reason");
        if (reason == "") return Err(400, "VALIDATION", "수리 사유(reason)는 필수입니다.");
        if (a.Status is not ("IN_STORAGE" or "IN_USE")) return Err(409, "INVALID_STATE", "보관/사용중 자산만 수리 접수할 수 있습니다.");
        a.PrevStatus = a.Status; a.Status = "UNDER_REPAIR";
        a.Hist(Util.Actor(b), "수리접수", reason);
        Store.Save(); return Results.Json(a);
    });
});

app.MapPost("/api/assets/{id}/repair-done", async (string id, HttpContext ctx) =>
{
    var b = await Util.Body(ctx); if (b == null) return Err(400, "INVALID_JSON", "본문 오류");
    return WithAsset(id, a =>
    {
        if (a.Status != "UNDER_REPAIR") return Err(409, "INVALID_STATE", "수리중 자산만 완료할 수 있습니다.");
        long repairCost = Util.L(b, "cost");
        a.RepairCostTotal += Math.Max(0, repairCost);
        a.Status = a.PrevStatus ?? (a.Custodian != null ? "IN_USE" : "IN_STORAGE"); // 수리 전 상태 복원
        a.PrevStatus = null;
        a.Hist(Util.Actor(b), "수리완료", repairCost > 0 ? $"수리비 {repairCost}원" : null);
        Store.Save(); return Results.Json(a);
    });
});

app.MapPost("/api/assets/{id}/dispose", async (string id, HttpContext ctx) =>
{
    var b = await Util.Body(ctx); if (b == null) return Err(400, "INVALID_JSON", "본문 오류");
    return WithAsset(id, a =>
    {
        string reason = Util.S(b, "reason");
        if (reason == "") return Err(400, "VALIDATION", "폐기 사유(reason)는 필수입니다.");
        if (a.Status == "DISPOSED") return Err(409, "INVALID_STATE", "이미 폐기된 자산입니다.");
        a.Status = "DISPOSED"; a.Custodian = null;
        a.Hist(Util.Actor(b), "폐기", reason);
        Store.Save(); return Results.Json(a);
    });
});

app.MapPost("/api/assets/{id}/report-lost", async (string id, HttpContext ctx) =>
{
    var b = await Util.Body(ctx); if (b == null) return Err(400, "INVALID_JSON", "본문 오류");
    return WithAsset(id, a =>
    {
        string reason = Util.S(b, "reason");
        if (reason == "") return Err(400, "VALIDATION", "분실 경위(reason)는 필수입니다.");
        if (a.Status is "DISPOSED" or "LOST") return Err(409, "INVALID_STATE", "폐기/분실 자산은 분실 신고할 수 없습니다.");
        a.Status = "LOST";
        a.Hist(Util.Actor(b), "분실신고", reason);
        Store.Save(); return Results.Json(a);
    });
});

app.MapPost("/api/assets/{id}/recover", async (string id, HttpContext ctx) =>
{
    var b = await Util.Body(ctx); if (b == null) return Err(400, "INVALID_JSON", "본문 오류");
    return WithAsset(id, a =>
    {
        string reason = Util.S(b, "reason");
        if (reason == "") return Err(400, "VALIDATION", "재발견 경위(reason)는 필수입니다.");
        if (a.Status != "LOST") return Err(409, "INVALID_STATE", "분실 자산만 회수(재발견) 처리할 수 있습니다.");
        a.Status = a.Custodian != null ? "IN_USE" : "IN_STORAGE";
        a.Hist(Util.Actor(b), "회수(재발견)", reason);
        Store.Save(); return Results.Json(a);
    });
});

app.MapGet("/api/assets/export", () =>
{
    lock (Store.Lock)
    {
        var sb = new StringBuilder();
        sb.AppendLine("assetNo,name,category,status,location,custodian,acquiredDate,acquiredCost,currentValue,quantity");
        foreach (var a in Store.Assets.Values.OrderBy(x => x.Id))
            sb.AppendLine(string.Join(',', a.AssetNo, Util.Csv(a.Name), a.Category, a.Status,
                Util.Csv(a.Location), Util.Csv(a.Custodian), a.AcquiredDate, a.AcquiredCost,
                a.CurrentValue, a.Quantity));
        return Results.Text(sb.ToString(), "text/csv; charset=utf-8");
    }
});

app.MapPost("/api/assets/{id}/audit", async (string id, HttpContext ctx) =>
{
    var b = await Util.Body(ctx); if (b == null) return Err(400, "INVALID_JSON", "본문 오류");
    return WithAsset(id, a =>
    {
        string auditor = Util.S(b, "auditor");
        if (auditor == "") return Err(400, "VALIDATION", "auditor(조사자)는 필수입니다.");
        bool found = !Util.Has(b, "found") || (b!["found"].ValueKind != JsonValueKind.False);
        a.Audits.Add(new AuditRec(Util.Now(), auditor, found, Util.SN(b, "memo")));
        a.LastAuditDate = Util.Today();
        a.Hist(auditor, "재물조사", found ? "확인" : "미확인");
        if (!found && a.Status is "IN_STORAGE" or "IN_USE") a.Status = "LOST"; // 조사 시 미확인 → 분실 처리
        Store.Save(); return Results.Json(a);
    });
});

app.MapGet("/api/stats", () =>
{
    lock (Store.Lock)
    {
        var byStatus = Store.Statuses.ToDictionary(s => s, _ => 0);
        var byCategory = new Dictionary<string, int>();
        long acqTotal = 0, curTotal = 0; int underRepair = 0;
        foreach (var a in Store.Assets.Values)
        {
            byStatus[a.Status] = byStatus.GetValueOrDefault(a.Status) + 1;
            byCategory[a.Category] = byCategory.GetValueOrDefault(a.Category) + 1;
            acqTotal += a.AcquiredCost; curTotal += a.CurrentValue;
            if (a.Status == "UNDER_REPAIR") underRepair++;
        }
        return Results.Json(new
        {
            total = Store.Assets.Count, byStatus, byCategory,
            totalAcquisitionCost = acqTotal, totalCurrentValue = curTotal, underRepair
        });
    }
});

app.Run();

// ================= 모델 / 저장소 / 유틸 =================
public class Asset
{
    public int Id { get; set; }
    public string AssetNo { get; set; } = "";
    public string Name { get; set; } = "";
    public string Category { get; set; } = "";
    public string AcquiredDate { get; set; } = "";
    public long AcquiredCost { get; set; }
    public int UsefulLifeYears { get; set; } = 5;
    public string? Location { get; set; }
    public string? Custodian { get; set; }
    public string Status { get; set; } = "IN_STORAGE";
    public string? Notes { get; set; }
    public int Quantity { get; set; } = 1;
    public string? Model { get; set; }              // 규격/모델
    public string? AcquisitionMethod { get; set; }  // 취득방법(구입/기증/제작)
    public string? Supplier { get; set; }           // 구입처
    public string? BudgetAccount { get; set; }       // 예산과목
    public long RepairCostTotal { get; set; }
    public string? LastAuditDate { get; set; }
    [JsonIgnore] public string? PrevStatus { get; set; }
    public List<HistRec> History { get; set; } = new();
    public List<AuditRec> Audits { get; set; } = new();

    // 정액법 감가상각 후 현재가치(잔존가 0)
    public long CurrentValue
    {
        get
        {
            if (Status is "DISPOSED" or "LOST") return 0;
            if (!DateTime.TryParse(AcquiredDate, CultureInfo.InvariantCulture, DateTimeStyles.None, out var acq)) return AcquiredCost;
            double age = (DateTime.UtcNow.AddHours(9) - acq).TotalDays / 365.0;
            double ratio = Math.Max(0, Math.Min(1, 1 - age / Math.Max(1, UsefulLifeYears))); // 0~1 상한
            long v = (long)Math.Round(AcquiredCost * ratio);
            return AcquiredCost > 0 ? Math.Max(1000, v) : 0; // 비망가액 1,000원 유지
        }
    }

    public void Hist(string? actor, string act, string? memo)
    {
        History.Add(new HistRec(Util.Now(), actor ?? "system", act, memo));
    }
}

public record HistRec(string At, string Actor, string Act, string? Memo);
public record AuditRec(string At, string Auditor, bool Found, string? Memo);

public static class Store
{
    public static readonly object Lock = new();
    public static readonly Dictionary<int, Asset> Assets = new();
    public static int Seq = 0;
    public static readonly string[] Categories = { "전산기기", "실험기자재", "도서", "가구", "체육용품", "기타" };
    public static readonly string[] Statuses = { "IN_STORAGE", "IN_USE", "UNDER_REPAIR", "DISPOSED", "LOST" };
    // 분류별 표준 내용연수(년)
    public static readonly Dictionary<string, int> DefaultLife = new()
    { { "전산기기", 5 }, { "실험기자재", 8 }, { "도서", 10 }, { "가구", 9 }, { "체육용품", 6 }, { "기타", 5 } };
    public static string File = Environment.GetEnvironmentVariable("DATA_FILE") ?? "";

    public static void Init()
    {
        if (File != "" && System.IO.File.Exists(File) && Load()) { Console.WriteLine($"loaded {Assets.Count} assets from {File}"); return; }
        Seed(); Save();
    }

    static readonly JsonSerializerOptions Opt = new() { PropertyNamingPolicy = JsonNamingPolicy.CamelCase, WriteIndented = true };

    public static void Save()
    {
        if (File == "") return;
        try
        {
            var snap = new Snapshot(Seq, Assets.Values.ToList());
            System.IO.File.WriteAllText(File + ".tmp", JsonSerializer.Serialize(snap, Opt));
            System.IO.File.Move(File + ".tmp", File, true);
        }
        catch (Exception e) { Console.WriteLine("save failed: " + e.Message); }
    }

    static bool Load()
    {
        try
        {
            var snap = JsonSerializer.Deserialize<Snapshot>(System.IO.File.ReadAllText(File), Opt);
            if (snap == null) return false;
            Seq = snap.Seq;
            foreach (var a in snap.Assets) Assets[a.Id] = a;
            return Assets.Count > 0;
        }
        catch { return false; }
    }

    static void Seed()
    {
        void Mk(string name, string cat, string date, long cost, int life, string? loc, string? cust, string status, params (string, string, string?)[] hist)
        {
            int id = ++Seq;
            var a = new Asset { Id = id, AssetNo = Util.AssetNo(date, id), Name = name, Category = cat, AcquiredDate = date, AcquiredCost = cost, UsefulLifeYears = life, Location = loc, Custodian = cust, Status = status };
            foreach (var (at, act, memo) in hist) a.History.Add(new HistRec(at, "관리자", act, memo));
            Assets[id] = a;
        }
        Mk("교무실 데스크탑 PC", "전산기기", "2023-03-02", 1_050_000, 5, "본관/교무실", "김도현", "IN_USE",
            ("2023-03-02 09:00:00", "등록", null), ("2023-03-05 10:00:00", "배치", "본관/교무실 / 김도현"));
        Mk("과학실 현미경 세트(20대)", "실험기자재", "2022-04-10", 3_200_000, 8, "별관/과학실", "이준호", "IN_USE",
            ("2022-04-10 09:00:00", "등록", null), ("2022-04-12 10:00:00", "배치", "별관/과학실 / 이준호"));
        Mk("체육관 탁구대", "체육용품", "2021-09-01", 480_000, 6, null, null, "IN_STORAGE",
            ("2021-09-01 09:00:00", "등록", null));
        Mk("도서관 빔프로젝터", "전산기기", "2024-08-20", 720_000, 5, "별관/도서관", "윤민아", "UNDER_REPAIR",
            ("2024-08-20 09:00:00", "등록", null), ("2026-08-22 10:00:00", "수리접수", "램프 불량"));
    }
}

public record Snapshot(int Seq, List<Asset> Assets);

public static class Util
{
    public static string Now() => DateTime.UtcNow.AddHours(9).ToString("yyyy-MM-dd HH:mm:ss");
    public static string Today() => DateTime.UtcNow.AddHours(9).ToString("yyyy-MM-dd");
    public static bool ValidDate(string s) => DateTime.TryParseExact(s, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out _);
    public static bool IsFuture(string s) => DateTime.TryParseExact(s, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var d) && d.Date > DateTime.UtcNow.AddHours(9).Date;
    public static string Csv(string? v) { v ??= ""; return v.Contains(',') || v.Contains('"') ? "\"" + v.Replace("\"", "\"\"") + "\"" : v; }
    public static string AssetNo(string date, int id) => $"EDU-{(date.Length >= 4 ? date[..4] : "0000")}-{id:D5}";

    public static async Task<Dictionary<string, JsonElement>?> Body(HttpContext ctx)
    {
        try
        {
            if (ctx.Request.ContentLength is null or 0) return new();
            return await ctx.Request.ReadFromJsonAsync<Dictionary<string, JsonElement>>() ?? new();
        }
        catch { return null; }
    }

    public static bool Has(Dictionary<string, JsonElement> b, string k) => b.ContainsKey(k);
    public static string S(Dictionary<string, JsonElement> b, string k) =>
        b.TryGetValue(k, out var v) && v.ValueKind == JsonValueKind.String ? (v.GetString() ?? "").Trim() : "";
    public static string? SN(Dictionary<string, JsonElement> b, string k) { var s = S(b, k); return s == "" ? null : s; }
    public static long L(Dictionary<string, JsonElement> b, string k) =>
        b.TryGetValue(k, out var v) && v.ValueKind == JsonValueKind.Number ? v.GetInt64() : 0;
    public static string? Actor(Dictionary<string, JsonElement> b) => SN(b, "actor");
}

public static class Html
{
    public const string Index = "<!doctype html><html lang=ko><meta charset=utf-8>"
        + "<title>기자재·자산 관리 · asset-mgr</title>"
        + "<style>body{font-family:system-ui,'Malgun Gothic',sans-serif;max-width:820px;margin:32px auto;padding:0 16px;color:#1e293b}"
        + "h1{font-size:22px}code{background:#eef2f8;padding:2px 6px;border-radius:5px}li{margin:5px 0}</style>"
        + "<h1>교육 기자재·자산 관리 (asset-mgr)</h1>"
        + "<p>등록 → 배치 → 이관 → 수리 → 폐기 생애주기 + 감가상각(정액법) + 재물조사를 관리합니다.</p><ul>"
        + "<li><code>GET /healthz</code></li>"
        + "<li><code>GET /api/assets?status=&category=&location=&custodian=&q=&sort=&page=&size=</code></li>"
        + "<li><code>POST /api/assets</code> 등록 · <code>PATCH /api/assets/{id}</code> 수정</li>"
        + "<li><code>POST /api/assets/{id}/assign|transfer|repair|repair-done|dispose|report-lost|recover|audit</code></li>"
        + "<li><code>GET /api/assets/export</code> 재물조사용 CSV</li>"
        + "<li><code>GET /api/assets/{id}/history</code> · <code>GET /api/stats</code></li>"
        + "</ul><p>currentValue=정액법 감가상각 후 현재가치. 샘플 자산 4건 시드. 배포 경로 <code>/svc/asset-mgr</code>.</p></html>";
}
