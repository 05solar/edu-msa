using System.Globalization;
using System.Text.Json;
using System.Text.Json.Serialization;

var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();

var jsonOpts = new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase };

// 표준 예시값(공무원 여비 규정 별표 기준 · 기관 규정에 맞게 조정)
const int DAILY = 25000;   // 일비/일
const int MEAL = 25000;    // 식비/일
const int CAR_RATE = 262;  // 자가용 1km 단가(예시)
const int INCITY_LONG = 20000;  // 관내출장 4시간 이상
const int INCITY_SHORT = 10000; // 관내출장 4시간 미만

int LodgeCap(string region) => region switch
{
    "seoul" => 100000,
    "metro" => 80000,   // 광역시·세종·제주
    _ => 70000,          // 그 밖의 지역
};

IResult Err(string code, string message) =>
    Results.Json(new { error = new { code, message } }, jsonOpts, statusCode: 400);

app.MapGet("/healthz", () => Results.Json(new { status = "ok", service = "travel-allowance" }, jsonOpts));

string indexHtml = File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "index.html"));
app.MapGet("/", () => Results.Content(indexHtml, "text/html; charset=utf-8"));

byte[] ogBytes = File.ReadAllBytes(Path.Combine(AppContext.BaseDirectory, "og.png"));
app.MapGet("/og.png", () => Results.Bytes(ogBytes, "image/png"));

app.MapPost("/api/calc", async (HttpRequest req) =>
{
    CalcReq? r;
    try { r = await JsonSerializer.DeserializeAsync<CalcReq>(req.Body, jsonOpts); }
    catch { return Err("INVALID_JSON", "요청 본문을 해석할 수 없습니다."); }
    if (r is null) return Err("VALIDATION", "입력이 없습니다.");

    var items = new List<object>();
    long total = 0;
    void Add(string label, string detail, long amount) { items.Add(new { label, detail, amount }); total += amount; }

    if (r.TripType == "incity")
    {
        int amt = r.Hours >= 4 ? INCITY_LONG : INCITY_SHORT;
        Add("관내 출장비", r.Hours >= 4 ? "4시간 이상 정액" : "4시간 미만 정액", amt);
        return Results.Json(new { items, total, meta = new { r.TripType } }, jsonOpts);
    }

    // 관외: 기간 계산
    if (!DateTime.TryParse(r.StartDate, CultureInfo.InvariantCulture, DateTimeStyles.None, out var sd)
        || !DateTime.TryParse(r.EndDate, CultureInfo.InvariantCulture, DateTimeStyles.None, out var ed))
        return Err("VALIDATION", "출장 시작일/종료일을 올바르게 입력하세요.");
    if (ed < sd) return Err("VALIDATION", "종료일이 시작일보다 빠릅니다.");
    int days = (ed - sd).Days + 1;
    int nights = Math.Max(0, days - 1);
    if (days > 60) return Err("VALIDATION", "출장 기간이 너무 깁니다(최대 60일).");

    Add("일비", $"{DAILY:N0}원 × {days}일", (long)DAILY * days);
    Add("식비", $"{MEAL:N0}원 × {days}일", (long)MEAL * days);

    // 숙박비
    if (r.FreeLodging)
        Add("숙박비", "무료숙박(관사·연수원 등)", 0);
    else if (nights > 0)
    {
        int cap = LodgeCap(r.Region ?? "other");
        long capTotal = (long)cap * nights;
        long lodging = r.LodgingActual > 0 ? Math.Min(r.LodgingActual, capTotal) : capTotal;
        string regionLabel = r.Region switch { "seoul" => "서울", "metro" => "광역시·세종·제주", _ => "그 밖의 지역" };
        string detail = r.LodgingActual > 0
            ? $"실비 {r.LodgingActual:N0}원 (상한 {cap:N0}×{nights}박={capTotal:N0})"
            : $"상한 {cap:N0}원 × {nights}박 ({regionLabel})";
        Add("숙박비", detail, lodging);
    }

    // 운임
    switch (r.Transport)
    {
        case "car":
            long fare = (long)Math.Max(0, r.DistanceKm) * CAR_RATE;
            Add("운임(자가용)", $"{r.DistanceKm:N0}km × {CAR_RATE}원", fare);
            break;
        case "public":
            Add("운임(대중교통)", "실비", Math.Max(0, r.FareActual));
            break;
        default:
            Add("운임(관용차)", "실비 없음", 0);
            break;
    }

    return Results.Json(new
    {
        items,
        total,
        meta = new { r.TripType, days, nights }
    }, jsonOpts);
});

app.Run();

record CalcReq(
    [property: JsonPropertyName("tripType")] string TripType,   // incity | outcity
    [property: JsonPropertyName("startDate")] string? StartDate,
    [property: JsonPropertyName("endDate")] string? EndDate,
    [property: JsonPropertyName("region")] string? Region,      // seoul | metro | other
    [property: JsonPropertyName("transport")] string? Transport, // car | public | official
    [property: JsonPropertyName("distanceKm")] int DistanceKm,
    [property: JsonPropertyName("fareActual")] int FareActual,
    [property: JsonPropertyName("lodgingActual")] int LodgingActual,
    [property: JsonPropertyName("freeLodging")] bool FreeLodging,
    [property: JsonPropertyName("hours")] int Hours
);
