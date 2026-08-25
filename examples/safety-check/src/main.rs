// safety-check · 학교 안전점검 (Rust, axum)
// 점검계획 → 수행(체크리스트) → 지적사항 → 개선조치 → 완료 흐름을 상태 전이로 관리한다.
use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    response::{Html, IntoResponse, Response},
    routing::{get, post},
    Json, Router,
};
use chrono::{FixedOffset, Months, NaiveDate, Utc};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};

const TYPES: &[&str] = &["소방", "전기", "가스", "승강기", "석면", "시설", "급식위생"];
const SEVERITIES: &[&str] = &["경미", "중대", "심각"];
const RESULTS: &[&str] = &["PASS", "FAIL", "NA"];
const STATUSES: &[&str] = &["PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELED"];
// 유형별 법정/권장 점검주기(월)
const DEFAULT_CYCLE: &[(&str, u32)] = &[("소방", 6), ("전기", 12), ("가스", 6), ("승강기", 1), ("석면", 6), ("시설", 6), ("급식위생", 3)];

fn now() -> String {
    let kst = FixedOffset::east_opt(9 * 3600).unwrap();
    Utc::now().with_timezone(&kst).format("%Y-%m-%d %H:%M:%S").to_string()
}
fn today() -> String {
    now()[..10].to_string()
}
fn valid_date(s: &str) -> bool {
    NaiveDate::parse_from_str(s, "%Y-%m-%d").is_ok()
}
fn default_cycle(kind: &str) -> u32 {
    DEFAULT_CYCLE.iter().find(|(k, _)| *k == kind).map(|(_, m)| *m).unwrap_or(6)
}
// 유형별 표준 체크리스트 템플릿
fn template_items(kind: &str) -> Vec<Item> {
    let t: &[(&str, &str)] = match kind {
        "소방" => &[("F1", "소화기 압력·비치 상태"), ("F2", "옥내소화전 작동"), ("F3", "피난유도등 점등"), ("F4", "자동화재탐지설비"), ("F5", "피난통로 확보")],
        "전기" => &[("E1", "배전반 절연저항"), ("E2", "누전차단기 동작"), ("E3", "콘센트·배선 과열")],
        "가스" => &[("G1", "가스누출경보기"), ("G2", "중간밸브 차단"), ("G3", "배관 부식·누출")],
        "승강기" => &[("L1", "비상통화장치"), ("L2", "도어 인터록"), ("L3", "비상정지장치")],
        "급식위생" => &[("H1", "냉장·냉동 온도"), ("H2", "교차오염 방지"), ("H3", "종사자 위생")],
        _ => &[("C1", "일반 안전상태")],
    };
    t.iter().map(|(c, l)| Item { code: c.to_string(), label: l.to_string(), result: "NA".into(), note: None }).collect()
}
fn finding_overdue(f: &Finding) -> bool {
    f.status == "OPEN" && f.due_date.as_deref().map_or(false, |d| d < today().as_str())
}
// 응답에 파생 필드(미조치 지적 수, 지적별 기한초과, 차기 점검 도래) 주입
fn enrich(i: &Inspection) -> Value {
    let mut v = serde_json::to_value(i).unwrap();
    v["openFindings"] = json!(open_findings(i));
    if let Some(arr) = v.get_mut("findings").and_then(|x| x.as_array_mut()) {
        for (idx, fv) in arr.iter_mut().enumerate() {
            fv["overdue"] = json!(finding_overdue(&i.findings[idx]));
        }
    }
    v["inspectionOverdue"] = json!(i.status != "CANCELED"
        && i.next_inspection_date.as_deref().map_or(false, |d| d < today().as_str()));
    v
}

#[derive(Serialize, Deserialize, Clone)]
struct Item {
    code: String,
    label: String,
    result: String,
    note: Option<String>,
}
#[derive(Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct Finding {
    id: u32,
    description: String,
    severity: String,
    status: String,
    due_date: Option<String>,
    assignee: Option<String>,
    resolution: Option<String>,
    resolved_at: Option<String>,
    #[serde(default)]
    attachments: Vec<String>,
}
#[derive(Serialize, Deserialize, Clone)]
struct Hist {
    at: String,
    actor: String,
    act: String,
    memo: Option<String>,
}
#[derive(Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct Inspection {
    id: u32,
    title: String,
    #[serde(rename = "type")]
    kind: String,
    area: String,
    scheduled_date: String,
    #[serde(default)]
    cycle_months: Option<u32>,
    #[serde(default)]
    next_inspection_date: Option<String>,
    inspector: String,
    status: String,
    items: Vec<Item>,
    findings: Vec<Finding>,
    finding_seq: u32,
    history: Vec<Hist>,
    created_at: String,
    updated_at: String,
}

#[derive(Serialize, Deserialize, Default)]
struct Store {
    seq: u32,
    #[serde(default)]
    inspections: HashMap<u32, Inspection>,
    #[serde(skip)]
    file: String,
}

type Db = Arc<Mutex<Store>>;

struct ApiError {
    status: StatusCode,
    code: &'static str,
    message: String,
}
fn err(status: StatusCode, code: &'static str, message: impl Into<String>) -> ApiError {
    ApiError { status, code, message: message.into() }
}
impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (self.status, Json(json!({"error":{"code":self.code,"message":self.message}}))).into_response()
    }
}

fn sget(v: &Value, k: &str) -> String {
    v.get(k).and_then(|x| x.as_str()).unwrap_or("").trim().to_string()
}
fn actor(v: &Value) -> String {
    let a = sget(v, "actor");
    if a.is_empty() { "system".into() } else { a }
}
fn parse_body(body: &str) -> Result<Value, ApiError> {
    if body.trim().is_empty() {
        return Ok(json!({}));
    }
    serde_json::from_str(body).map_err(|_| err(StatusCode::BAD_REQUEST, "INVALID_JSON", "요청 본문을 해석할 수 없습니다."))
}

impl Store {
    fn save(&self) {
        if self.file.is_empty() {
            return;
        }
        if let Ok(s) = serde_json::to_string_pretty(self) {
            let tmp = format!("{}.tmp", self.file);
            if let Err(e) = std::fs::write(&tmp, s) {
                eprintln!("save write failed: {}", e);
                return;
            }
            if let Err(e) = std::fs::rename(&tmp, &self.file) {
                eprintln!("save rename failed: {}", e);
            }
        }
    }
    fn get(&mut self, id: u32) -> Result<&mut Inspection, ApiError> {
        self.inspections
            .get_mut(&id)
            .ok_or_else(|| err(StatusCode::NOT_FOUND, "NOT_FOUND", format!("점검 {} 를 찾을 수 없습니다.", id)))
    }
}

fn hist(i: &mut Inspection, actor: String, act: &str, memo: Option<String>) {
    i.history.push(Hist { at: now(), actor, act: act.into(), memo });
    i.updated_at = now();
}

fn seed(st: &mut Store) {
    let mk = |st: &mut Store, title: &str, kind: &str, area: &str, date: &str, inspector: &str, status: &str,
              items: Vec<(&str, &str, &str)>, findings: Vec<(&str, &str, &str)>| {
        st.seq += 1;
        let id = st.seq;
        let its: Vec<Item> = items.iter().map(|(c, l, r)| Item { code: c.to_string(), label: l.to_string(), result: r.to_string(), note: None }).collect();
        let mut fseq = 0u32;
        let fs: Vec<Finding> = findings.iter().map(|(d, sev, stt)| {
            fseq += 1;
            Finding { id: fseq, description: d.to_string(), severity: sev.to_string(), status: stt.to_string(),
                due_date: Some("2026-09-10".into()), assignee: Some("시설팀".into()),
                resolution: if *stt == "RESOLVED" { Some("조치 완료".into()) } else { None },
                resolved_at: if *stt == "RESOLVED" { Some(now()) } else { None }, attachments: vec![] }
        }).collect();
        let insp = Inspection {
            id, title: title.into(), kind: kind.into(), area: area.into(), scheduled_date: date.into(),
            cycle_months: Some(default_cycle(kind)), next_inspection_date: None,
            inspector: inspector.into(), status: status.into(), items: its, findings: fs, finding_seq: fseq,
            history: vec![Hist { at: now(), actor: inspector.into(), act: "계획수립".into(), memo: None }],
            created_at: now(), updated_at: now(),
        };
        st.inspections.insert(id, insp);
    };
    mk(st, "9월 정기 소방시설 점검", "소방", "본관 전체", "2026-09-05", "안전관리자 김OO", "IN_PROGRESS",
        vec![("F1", "소화기 압력·비치", "PASS"), ("F2", "옥내소화전 작동", "FAIL"), ("F3", "피난유도등 점등", "PASS")],
        vec![("옥내소화전 2층 밸브 누수", "중대", "OPEN")]);
    mk(st, "전기안전 정기점검", "전기", "별관 배전반", "2026-08-20", "전기안전관리자 이OO", "COMPLETED",
        vec![("E1", "배전반 절연저항", "PASS"), ("E2", "누전차단기 동작", "PASS")],
        vec![("3층 콘센트 과열 흔적", "경미", "RESOLVED")]);
    mk(st, "승강기 월례 안전점검", "승강기", "본관 승강기 1호기", "2026-08-28", "승강기관리원 박OO", "PLANNED",
        vec![("L1", "비상통화장치", "NA"), ("L2", "도어 인터록", "NA")], vec![]);
}

// ---------- 핸들러 ----------
async fn healthz() -> Json<Value> {
    Json(json!({"status":"ok","service":"safety-check","time":now()}))
}

async fn index() -> Html<&'static str> {
    Html(INDEX)
}

async fn list(State(db): State<Db>, Query(q): Query<HashMap<String, String>>) -> Json<Value> {
    let st = db.lock().unwrap();
    let status = q.get("status").cloned().unwrap_or_default().to_uppercase();
    let kind = q.get("type").cloned().unwrap_or_default();
    let area = q.get("area").cloned().unwrap_or_default();
    let inspector = q.get("inspector").cloned().unwrap_or_default();
    let kw = q.get("q").cloned().unwrap_or_default();
    let page: usize = q.get("page").and_then(|x| x.parse().ok()).filter(|&p| p >= 1).unwrap_or(1);
    let mut size: usize = q.get("size").and_then(|x| x.parse().ok()).unwrap_or(10);
    if size < 1 || size > 100 { size = 10; }
    let mut items: Vec<&Inspection> = st.inspections.values().filter(|i| {
        (status.is_empty() || i.status == status)
            && (kind.is_empty() || i.kind == kind)
            && (area.is_empty() || i.area == area)
            && (inspector.is_empty() || i.inspector == inspector)
            && (kw.is_empty() || i.title.contains(&kw) || i.area.contains(&kw))
    }).collect();
    items.sort_by(|a, b| b.id.cmp(&a.id));
    let total = items.len();
    let start = (page - 1) * size;
    let page_items: Vec<Value> = items.into_iter().skip(start).take(size).map(enrich).collect();
    Json(json!({"page":page,"size":size,"total":total,"items":page_items}))
}

fn open_findings(i: &Inspection) -> usize {
    i.findings.iter().filter(|f| f.status == "OPEN").count()
}

async fn create(State(db): State<Db>, body: String) -> Result<(StatusCode, Json<Value>), ApiError> {
    let b = parse_body(&body)?;
    let title = sget(&b, "title");
    let kind = sget(&b, "type");
    let area = sget(&b, "area");
    if title.is_empty() || kind.is_empty() || area.is_empty() {
        return Err(err(StatusCode::BAD_REQUEST, "VALIDATION", "title, type, area는 필수입니다."));
    }
    if !TYPES.contains(&kind.as_str()) {
        return Err(err(StatusCode::BAD_REQUEST, "VALIDATION", format!("type은 {:?} 중 하나여야 합니다.", TYPES)));
    }
    let mut sched = sget(&b, "scheduledDate");
    if sched.is_empty() { sched = now()[..10].to_string(); }
    if !valid_date(&sched) {
        return Err(err(StatusCode::BAD_REQUEST, "VALIDATION", "scheduledDate 형식이 올바르지 않습니다(YYYY-MM-DD)."));
    }
    // 체크리스트 항목(선택): [{code,label}]. 미지정 시 유형별 표준 템플릿 사용.
    let mut items: Vec<Item> = vec![];
    if let Some(arr) = b.get("items").and_then(|x| x.as_array()) {
        for it in arr {
            let code = sget(it, "code");
            let label = sget(it, "label");
            if !label.is_empty() {
                items.push(Item { code: if code.is_empty() { format!("C{}", items.len() + 1) } else { code }, label, result: "NA".into(), note: None });
            }
        }
    }
    if items.is_empty() {
        items = template_items(&kind);
    }
    let cycle = b.get("cycleMonths").and_then(|x| x.as_u64()).map(|n| n as u32).filter(|&n| n > 0).unwrap_or_else(|| default_cycle(&kind));
    let mut st = db.lock().unwrap();
    st.seq += 1;
    let id = st.seq;
    let mut insp = Inspection {
        id, title, kind, area, scheduled_date: sched, cycle_months: Some(cycle), next_inspection_date: None,
        inspector: sget(&b, "inspector"), status: "PLANNED".into(), items, findings: vec![], finding_seq: 0,
        history: vec![], created_at: now(), updated_at: now(),
    };
    hist(&mut insp, actor(&b), "계획수립", None);
    st.inspections.insert(id, insp.clone());
    st.save();
    Ok((StatusCode::CREATED, Json(enrich(&insp))))
}

async fn get_one(State(db): State<Db>, Path(id): Path<u32>) -> Result<Json<Value>, ApiError> {
    let mut st = db.lock().unwrap();
    let i = st.get(id)?;
    Ok(Json(enrich(&*i)))
}

async fn history(State(db): State<Db>, Path(id): Path<u32>) -> Result<Json<Value>, ApiError> {
    let mut st = db.lock().unwrap();
    let i = st.get(id)?;
    Ok(Json(json!({"id":i.id,"history":i.history})))
}

async fn edit(State(db): State<Db>, Path(id): Path<u32>, body: String) -> Result<Json<Value>, ApiError> {
    let b = parse_body(&body)?;
    let mut st = db.lock().unwrap();
    let i = st.get(id)?;
    if i.status != "PLANNED" {
        return Err(err(StatusCode::CONFLICT, "INVALID_STATE", "계획(PLANNED) 상태의 점검만 수정할 수 있습니다."));
    }
    if b.get("title").is_some() { let t = sget(&b, "title"); if t.is_empty() { return Err(err(StatusCode::BAD_REQUEST, "VALIDATION", "title 오류")); } i.title = t; }
    if b.get("area").is_some() { i.area = sget(&b, "area"); }
    if b.get("inspector").is_some() { i.inspector = sget(&b, "inspector"); }
    if b.get("scheduledDate").is_some() {
        let d = sget(&b, "scheduledDate");
        if !valid_date(&d) { return Err(err(StatusCode::BAD_REQUEST, "VALIDATION", "scheduledDate 형식 오류")); }
        i.scheduled_date = d;
    }
    let ac = actor(&b);
    hist(i, ac, "수정", None);
    st.save();
    let i = st.get(id)?;
    Ok(Json(enrich(&*i)))
}

async fn start(State(db): State<Db>, Path(id): Path<u32>, body: String) -> Result<Json<Value>, ApiError> {
    let b = parse_body(&body)?;
    let mut st = db.lock().unwrap();
    let i = st.get(id)?;
    if i.status != "PLANNED" {
        return Err(err(StatusCode::CONFLICT, "INVALID_STATE", "계획 상태의 점검만 시작할 수 있습니다."));
    }
    i.status = "IN_PROGRESS".into();
    let ac = actor(&b);
    hist(i, ac, "점검시작", None);
    st.save();
    let i = st.get(id)?;
    Ok(Json(enrich(&*i)))
}

async fn check(State(db): State<Db>, Path(id): Path<u32>, body: String) -> Result<Json<Value>, ApiError> {
    let b = parse_body(&body)?;
    let code = sget(&b, "code");
    let result = sget(&b, "result").to_uppercase();
    if code.is_empty() { return Err(err(StatusCode::BAD_REQUEST, "VALIDATION", "code는 필수입니다.")); }
    if !RESULTS.contains(&result.as_str()) { return Err(err(StatusCode::BAD_REQUEST, "VALIDATION", "result는 PASS/FAIL/NA 중 하나여야 합니다.")); }
    let note = sget(&b, "note");
    let ac = actor(&b);
    let mut st = db.lock().unwrap();
    let i = st.get(id)?;
    if i.status != "IN_PROGRESS" {
        return Err(err(StatusCode::CONFLICT, "INVALID_STATE", "진행 중인 점검만 항목을 판정할 수 있습니다."));
    }
    let idx = i.items.iter().position(|it| it.code == code)
        .ok_or_else(|| err(StatusCode::NOT_FOUND, "NOT_FOUND", format!("체크 항목 {} 를 찾을 수 없습니다.", code)))?;
    i.items[idx].result = result.clone();
    i.items[idx].note = if note.is_empty() { None } else { Some(note.clone()) };
    let label = i.items[idx].label.clone();
    // FAIL 항목은 지적사항 자동 생성(중복 방지: 같은 항목 라벨의 OPEN 지적이 없을 때만)
    if result == "FAIL" {
        let exists = i.findings.iter().any(|f| f.status == "OPEN" && f.description.contains(&label));
        if !exists {
            i.finding_seq += 1;
            let fid = i.finding_seq;
            i.findings.push(Finding {
                id: fid, description: format!("[{}] {}", code, label), severity: "중대".into(),
                status: "OPEN".into(), due_date: None, assignee: None, resolution: None, resolved_at: None,
                attachments: vec![],
            });
        }
    }
    hist(i, ac, "항목판정", Some(format!("{} = {}", code, result)));
    st.save();
    let i = st.get(id)?;
    Ok(Json(enrich(&*i)))
}

async fn add_finding(State(db): State<Db>, Path(id): Path<u32>, body: String) -> Result<(StatusCode, Json<Value>), ApiError> {
    let b = parse_body(&body)?;
    let desc = sget(&b, "description");
    if desc.is_empty() { return Err(err(StatusCode::BAD_REQUEST, "VALIDATION", "description은 필수입니다.")); }
    let mut sev = sget(&b, "severity");
    if sev.is_empty() { sev = "중대".into(); }
    if !SEVERITIES.contains(&sev.as_str()) { return Err(err(StatusCode::BAD_REQUEST, "VALIDATION", "severity는 경미/중대/심각 중 하나여야 합니다.")); }
    let due = sget(&b, "dueDate");
    if !due.is_empty() && !valid_date(&due) { return Err(err(StatusCode::BAD_REQUEST, "VALIDATION", "dueDate 형식 오류")); }
    let ac = actor(&b);
    let mut st = db.lock().unwrap();
    let i = st.get(id)?;
    if i.status != "IN_PROGRESS" {
        return Err(err(StatusCode::CONFLICT, "INVALID_STATE", "진행 중인 점검에만 지적사항을 추가할 수 있습니다."));
    }
    i.finding_seq += 1;
    let fid = i.finding_seq;
    let atts: Vec<String> = b.get("attachments").and_then(|x| x.as_array())
        .map(|a| a.iter().filter_map(|v| v.as_str().map(|s| s.trim().to_string())).filter(|s| !s.is_empty()).take(20).collect())
        .unwrap_or_default();
    i.findings.push(Finding {
        id: fid, description: desc, severity: sev, status: "OPEN".into(),
        due_date: if due.is_empty() { None } else { Some(due) },
        assignee: { let a = sget(&b, "assignee"); if a.is_empty() { None } else { Some(a) } },
        resolution: None, resolved_at: None, attachments: atts,
    });
    hist(i, ac, "지적등록", None);
    st.save();
    let i = st.get(id)?;
    Ok((StatusCode::CREATED, Json(enrich(&*i))))
}

async fn resolve_finding(State(db): State<Db>, Path((id, fid)): Path<(u32, u32)>, body: String) -> Result<Json<Value>, ApiError> {
    let b = parse_body(&body)?;
    let resolution = sget(&b, "resolution");
    if resolution.is_empty() { return Err(err(StatusCode::BAD_REQUEST, "VALIDATION", "조치 내용(resolution)은 필수입니다.")); }
    let ac = actor(&b);
    let mut st = db.lock().unwrap();
    let i = st.get(id)?;
    let f = i.findings.iter_mut().find(|f| f.id == fid)
        .ok_or_else(|| err(StatusCode::NOT_FOUND, "NOT_FOUND", format!("지적사항 {} 를 찾을 수 없습니다.", fid)))?;
    if f.status == "RESOLVED" {
        return Err(err(StatusCode::CONFLICT, "INVALID_STATE", "이미 조치 완료된 지적사항입니다."));
    }
    f.status = "RESOLVED".into();
    f.resolution = Some(resolution);
    f.resolved_at = Some(now());
    hist(i, ac, "개선조치", Some(format!("지적 #{} 해소", fid)));
    st.save();
    let i = st.get(id)?;
    Ok(Json(enrich(&*i)))
}

async fn reopen_finding(State(db): State<Db>, Path((id, fid)): Path<(u32, u32)>, body: String) -> Result<Json<Value>, ApiError> {
    let b = parse_body(&body)?;
    let reason = sget(&b, "reason");
    if reason.is_empty() {
        return Err(err(StatusCode::BAD_REQUEST, "VALIDATION", "재발 사유(reason)는 필수입니다."));
    }
    let ac = actor(&b);
    let mut st = db.lock().unwrap();
    let i = st.get(id)?;
    {
        let f = i.findings.iter_mut().find(|f| f.id == fid)
            .ok_or_else(|| err(StatusCode::NOT_FOUND, "NOT_FOUND", format!("지적사항 {} 를 찾을 수 없습니다.", fid)))?;
        if f.status != "RESOLVED" {
            return Err(err(StatusCode::CONFLICT, "INVALID_STATE", "조치완료된 지적만 재개할 수 있습니다."));
        }
        f.status = "OPEN".into();
        f.resolution = None;
        f.resolved_at = None;
    }
    if i.status == "COMPLETED" {
        i.status = "IN_PROGRESS".into(); // 완료 점검이라도 지적 재발 시 재개
        i.next_inspection_date = None; // 재개 시 차기 점검일 초기화(재완료 시 재산정)
    }
    hist(i, ac, "지적재개", Some(reason));
    st.save();
    let i = st.get(id)?;
    Ok(Json(enrich(&*i)))
}

async fn complete(State(db): State<Db>, Path(id): Path<u32>, body: String) -> Result<Json<Value>, ApiError> {
    let b = parse_body(&body)?;
    let ac = actor(&b);
    let mut st = db.lock().unwrap();
    let i = st.get(id)?;
    if i.status != "IN_PROGRESS" {
        return Err(err(StatusCode::CONFLICT, "INVALID_STATE", "진행 중인 점검만 완료할 수 있습니다."));
    }
    if open_findings(i) > 0 {
        return Err(err(StatusCode::CONFLICT, "OPEN_FINDINGS", "미조치 지적사항이 남아 완료할 수 없습니다."));
    }
    i.status = "COMPLETED".into();
    // 차기 점검일 자동 산정(완료일 + 점검주기)
    let n = i.cycle_months.unwrap_or_else(|| default_cycle(&i.kind));
    if let Ok(d) = NaiveDate::parse_from_str(&today(), "%Y-%m-%d") {
        i.next_inspection_date = d.checked_add_months(Months::new(n)).map(|nd| nd.format("%Y-%m-%d").to_string());
    }
    hist(i, ac, "점검완료", None);
    st.save();
    let i = st.get(id)?;
    Ok(Json(enrich(&*i)))
}

async fn cancel(State(db): State<Db>, Path(id): Path<u32>, body: String) -> Result<Json<Value>, ApiError> {
    let b = parse_body(&body)?;
    let ac = actor(&b);
    let mut st = db.lock().unwrap();
    let i = st.get(id)?;
    if i.status == "COMPLETED" || i.status == "CANCELED" {
        return Err(err(StatusCode::CONFLICT, "INVALID_STATE", "완료/취소된 점검은 취소할 수 없습니다."));
    }
    i.status = "CANCELED".into();
    hist(i, ac, "취소", Some(sget(&b, "reason")));
    st.save();
    let i = st.get(id)?;
    Ok(Json(enrich(&*i)))
}

async fn stats(State(db): State<Db>) -> Json<Value> {
    let st = db.lock().unwrap();
    let mut by_status: HashMap<String, u32> = STATUSES.iter().map(|s| (s.to_string(), 0)).collect();
    let mut by_type: HashMap<String, u32> = HashMap::new();
    let mut by_severity: HashMap<String, u32> = HashMap::new();
    let mut open = 0u32;
    let mut resolved = 0u32;
    let mut overdue = 0u32;
    let mut due_insp = 0u32;
    for i in st.inspections.values() {
        *by_status.entry(i.status.clone()).or_insert(0) += 1;
        *by_type.entry(i.kind.clone()).or_insert(0) += 1;
        for f in &i.findings {
            *by_severity.entry(f.severity.clone()).or_insert(0) += 1;
            if f.status == "OPEN" {
                open += 1;
                if finding_overdue(f) { overdue += 1; }
            } else {
                resolved += 1;
            }
        }
        if i.status != "CANCELED" && i.next_inspection_date.as_deref().map_or(false, |d| d < today().as_str()) {
            due_insp += 1;
        }
    }
    Json(json!({
        "total": st.inspections.len(), "byStatus": by_status, "byType": by_type,
        "findingsBySeverity": by_severity, "openFindings": open, "resolvedFindings": resolved,
        "overdueFindings": overdue, "dueInspections": due_insp
    }))
}

const INDEX: &str = r#"<!doctype html><html lang=ko><meta charset=utf-8>
<title>학교 안전점검 · safety-check</title>
<style>body{font-family:system-ui,'Malgun Gothic',sans-serif;max-width:820px;margin:32px auto;padding:0 16px;color:#1e293b}
h1{font-size:22px}code{background:#eef2f8;padding:2px 6px;border-radius:5px}li{margin:5px 0}</style>
<h1>학교 안전점검 (safety-check)</h1>
<p>점검계획 → 수행(체크리스트) → 지적사항 → 개선조치 → 완료 흐름을 관리합니다.</p><ul>
<li><code>GET /healthz</code></li>
<li><code>GET /api/inspections?status=&type=&area=&inspector=&q=&page=&size=</code></li>
<li><code>POST /api/inspections</code> 계획수립 · <code>PATCH /api/inspections/{id}</code> 수정</li>
<li><code>POST /api/inspections/{id}/start|check|findings|complete|cancel</code></li>
<li><code>POST /api/inspections/{id}/findings/{fid}/resolve|reopen</code> 개선조치·재개</li>
<li><code>GET /api/inspections/{id}/history</code> · <code>GET /api/stats</code></li>
</ul><p>FAIL 항목은 지적사항 자동 생성, 미조치 지적이 있으면 완료 불가. 샘플 3건 시드. 배포 경로 <code>/svc/safety-check</code>.</p></html>"#;

#[tokio::main]
async fn main() {
    let file = std::env::var("DATA_FILE").unwrap_or_default();
    let mut store = Store { file: file.clone(), ..Default::default() };
    let mut loaded = false;
    if !file.is_empty() {
        if let Ok(data) = std::fs::read_to_string(&file) {
            if let Ok(mut s) = serde_json::from_str::<Store>(&data) {
                s.file = file.clone();
                if !s.inspections.is_empty() {
                    store = s;
                    loaded = true;
                    println!("loaded {} inspections from {}", store.inspections.len(), file);
                }
            }
        }
    }
    if !loaded {
        seed(&mut store);
        store.save();
    }
    let db: Db = Arc::new(Mutex::new(store));

    let app = Router::new()
        .route("/healthz", get(healthz))
        .route("/", get(index))
        .route("/api/inspections", get(list).post(create))
        .route("/api/inspections/:id", get(get_one).patch(edit))
        .route("/api/inspections/:id/history", get(history))
        .route("/api/inspections/:id/start", post(start))
        .route("/api/inspections/:id/check", post(check))
        .route("/api/inspections/:id/findings", post(add_finding))
        .route("/api/inspections/:id/findings/:fid/resolve", post(resolve_finding))
        .route("/api/inspections/:id/findings/:fid/reopen", post(reopen_finding))
        .route("/api/inspections/:id/complete", post(complete))
        .route("/api/inspections/:id/cancel", post(cancel))
        .route("/api/stats", get(stats))
        .with_state(db);

    let port = std::env::var("PORT").ok().and_then(|p| p.parse().ok()).unwrap_or(8080u16);
    let listener = tokio::net::TcpListener::bind(("0.0.0.0", port)).await.unwrap();
    println!("safety-check listening on :{}", port);
    axum::serve(listener, app).await.unwrap();
}
