# facility-maint · 학교 시설 유지보수 관리 (Python, FastAPI)
# 접수 → 배정 → 작업 → 완료(보류/반려) 흐름을 상태 전이로 관리하고 SLA/통계를 제공한다.
import json
import os
import threading
from datetime import datetime, timedelta, timezone

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, HTMLResponse, PlainTextResponse

KST = timezone(timedelta(hours=9))
def now() -> datetime:
    return datetime.now(KST)
FMT = "%Y-%m-%d %H:%M:%S"
def fmt(dt: datetime) -> str:
    return dt.strftime(FMT)
def parse(s: str) -> datetime:
    return datetime.strptime(s, FMT).replace(tzinfo=KST)
def compute_due(created: str, priority: str) -> str:
    return fmt(parse(created) + timedelta(hours=SLA_HOURS[priority]))

def norm_attachments(v) -> list:
    # 첨부는 파일 참조(메타)만 관리. 실제 바이너리 저장은 플랫폼/오브젝트스토리지 책임.
    out = []
    if not v:
        return out
    if not isinstance(v, list):
        raise ApiError(400, "VALIDATION", "attachments는 배열이어야 합니다.")
    for a in v[:20]:
        if isinstance(a, str) and a.strip():
            out.append({"name": a.strip(), "note": ""})
        elif isinstance(a, dict) and (a.get("name") or "").strip():
            out.append({"name": a["name"].strip(), "note": (a.get("note") or "").strip()})
    return out

CATEGORIES = {"전기", "배관", "냉난방", "목공", "도장", "기타"}
PRIORITIES = {"LOW", "NORMAL", "HIGH", "URGENT"}
# 우선순위별 SLA(접수→기한) 시간
SLA_HOURS = {"URGENT": 4, "HIGH": 24, "NORMAL": 72, "LOW": 168}

STATUS = ["RECEIVED", "ASSIGNED", "IN_PROGRESS", "ON_HOLD", "DONE", "REJECTED"]


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str):
        self.status, self.code, self.message = status, code, message


def err(status, code, message):
    return JSONResponse(status_code=status, content={"error": {"code": code, "message": message}})


class Store:
    def __init__(self):
        self.lock = threading.Lock()
        self.seq = 0
        self.orders: dict[int, dict] = {}
        self.file = os.getenv("DATA_FILE", "")
        if self.file and os.path.exists(self.file):
            if self._load():
                return
        self._seed()
        self._save()

    def _next(self):
        self.seq += 1
        return self.seq

    def _load(self) -> bool:
        try:
            with open(self.file, encoding="utf-8") as f:
                snap = json.load(f)
            self.seq = snap.get("seq", 0)
            for o in snap.get("orders", []):
                self.orders[o["id"]] = o
            return len(self.orders) > 0
        except Exception:
            return False

    def _save(self):
        if not self.file:
            return
        snap = {"seq": self.seq, "orders": list(self.orders.values())}
        try:
            tmp = self.file + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(snap, f, ensure_ascii=False, indent=1)
            os.replace(tmp, self.file)
        except Exception as e:
            print("save failed:", e, flush=True)

    def _mk(self, title, location, category, description, requester, department, priority,
            status, assignee, created, updated, history):
        return {
            "id": self._next(), "title": title, "location": location, "category": category,
            "description": description, "requester": requester, "department": department,
            "priority": priority, "status": status, "assignee": assignee,
            "dueDate": compute_due(created, priority), "cost": None, "completionNote": None,
            "heldFrom": None, "holdStart": None, "attachments": [],
            "createdAt": created, "updatedAt": updated, "history": history,
        }

    def _seed(self):
        for o in [
            self._mk("본관 3층 여자화장실 누수", "본관/3F/화장실", "배관",
                     "세면대 하부 배관에서 물이 새어 바닥이 젖습니다.", "김도현", "교육지원과", "HIGH",
                     "ASSIGNED", "시설관리 이기사", "2026-08-22 09:00:00", "2026-08-22 10:00:00",
                     [{"at": "2026-08-22 09:00:00", "actor": "김도현", "act": "접수"},
                      {"at": "2026-08-22 10:00:00", "actor": "관리자", "act": "배정", "memo": "시설관리 이기사"}]),
            self._mk("급식실 냉장고 온도 이상", "급식실/1F", "냉난방",
                     "냉장고 내부 온도가 12도로 상승, 식자재 보관 위험.", "윤민아", "행정지원과", "URGENT",
                     "IN_PROGRESS", "설비업체 A", "2026-08-24 08:30:00", "2026-08-24 09:10:00",
                     [{"at": "2026-08-24 08:30:00", "actor": "윤민아", "act": "접수"},
                      {"at": "2026-08-24 08:50:00", "actor": "관리자", "act": "배정", "memo": "설비업체 A"},
                      {"at": "2026-08-24 09:10:00", "actor": "설비업체 A", "act": "작업시작"}]),
            self._mk("도서관 형광등 5개 교체", "별관/2F/도서관", "전기",
                     "천장 형광등 5개가 점멸하거나 꺼져 있습니다.", "이준호", "기획예산과", "NORMAL",
                     "RECEIVED", None, "2026-08-25 09:00:00", "2026-08-25 09:00:00",
                     [{"at": "2026-08-25 09:00:00", "actor": "이준호", "act": "접수"}]),
        ]:
            self.orders[o["id"]] = o


store: Store
app = FastAPI(title="facility-maint")


@app.exception_handler(ApiError)
async def _api_error(request: Request, exc: ApiError):
    return err(exc.status, exc.code, exc.message)


def get_order(oid: str) -> dict:
    try:
        i = int(oid)
    except ValueError:
        raise ApiError(400, "VALIDATION", "id가 올바르지 않습니다.")
    o = store.orders.get(i)
    if not o:
        raise ApiError(404, "NOT_FOUND", f"정비요청 {oid} 를 찾을 수 없습니다.")
    return o


async def body(request: Request) -> dict:
    try:
        b = await request.body()
        if not b:
            return {}
        return json.loads(b)
    except Exception:
        raise ApiError(400, "INVALID_JSON", "요청 본문을 해석할 수 없습니다.")


def hist(o, actor, act, memo=None):
    e = {"at": fmt(now()), "actor": actor or "system", "act": act}
    if memo:
        e["memo"] = memo
    o["history"].append(e)
    o["updatedAt"] = fmt(now())


def is_overdue(o) -> bool:
    if o["status"] in ("DONE", "REJECTED", "ON_HOLD"):  # 보류 중은 지연으로 보지 않음
        return False
    try:
        return now() > datetime.strptime(o["dueDate"], "%Y-%m-%d %H:%M:%S").replace(tzinfo=KST)
    except Exception:
        return False


@app.get("/healthz")
async def healthz():
    return {"status": "ok", "service": "facility-maint", "time": fmt(now())}


@app.get("/api/orders")
async def list_orders(request: Request):
    q = request.query_params
    status = q.get("status", "").upper()
    category = q.get("category", "")
    priority = q.get("priority", "").upper()
    assignee = q.get("assignee", "")
    kw = q.get("q", "")
    try:
        page = max(1, int(q.get("page", 1)))
        size = min(100, max(1, int(q.get("size", 10))))
    except ValueError:
        raise ApiError(400, "VALIDATION", "page/size는 정수여야 합니다.")
    with store.lock:
        items = []
        for o in store.orders.values():
            if status and o["status"] != status:
                continue
            if category and o["category"] != category:
                continue
            if priority and o["priority"] != priority:
                continue
            if assignee and (o["assignee"] or "") != assignee:
                continue
            if kw and kw not in o["title"] and kw not in (o["description"] or "") and kw not in o["location"]:
                continue
            items.append({**o, "overdue": is_overdue(o)})
        sort = q.get("sort", "id")
        if sort == "due":
            items.sort(key=lambda x: x["dueDate"])
        elif sort == "priority":
            rank = {"URGENT": 0, "HIGH": 1, "NORMAL": 2, "LOW": 3}
            items.sort(key=lambda x: (rank.get(x["priority"], 9), x["dueDate"]))
        else:
            items.sort(key=lambda x: x["id"], reverse=True)
        total = len(items)
        start = (page - 1) * size
        return {"page": page, "size": size, "total": total, "items": items[start:start + size]}


@app.post("/api/orders")
async def create(request: Request):
    b = await body(request)
    title = (b.get("title") or "").strip()
    requester = (b.get("requester") or "").strip()
    location = (b.get("location") or "").strip()
    if not title or not requester or not location:
        raise ApiError(400, "VALIDATION", "title, requester, location은 필수입니다.")
    if len(title) > 120:
        raise ApiError(400, "VALIDATION", "title은 120자 이하여야 합니다.")
    category = (b.get("category") or "기타").strip()
    if category not in CATEGORIES:
        raise ApiError(400, "VALIDATION", f"category는 {sorted(CATEGORIES)} 중 하나여야 합니다.")
    priority = (b.get("priority") or "NORMAL").strip().upper()
    if priority not in PRIORITIES:
        raise ApiError(400, "VALIDATION", "priority는 LOW/NORMAL/HIGH/URGENT 중 하나여야 합니다.")
    atts = norm_attachments(b.get("attachments"))
    with store.lock:
        created = fmt(now())
        o = store._mk(title, location, category, (b.get("description") or "").strip(),
                      requester, (b.get("department") or "").strip(), priority,
                      "RECEIVED", None, created, created,
                      [{"at": created, "actor": requester, "act": "접수"}])
        o["attachments"] = atts
        store.orders[o["id"]] = o
        store._save()
    return JSONResponse(status_code=201, content=o)


@app.get("/api/orders/{oid}")
async def get_one(oid: str):
    with store.lock:
        o = get_order(oid)
        return {**o, "overdue": is_overdue(o)}


@app.get("/api/orders/{oid}/history")
async def get_history(oid: str):
    with store.lock:
        o = get_order(oid)
        return {"id": o["id"], "history": o["history"]}


@app.patch("/api/orders/{oid}")
async def edit(oid: str, request: Request):
    b = await body(request)
    with store.lock:
        o = get_order(oid)
        if o["status"] not in ("RECEIVED", "ON_HOLD"):
            raise ApiError(409, "INVALID_STATE", "접수/보류 상태의 요청만 수정할 수 있습니다.")
        if "title" in b:
            t = (b["title"] or "").strip()
            if not t or len(t) > 120:
                raise ApiError(400, "VALIDATION", "title은 1~120자여야 합니다.")
            o["title"] = t
        if "category" in b:
            if b["category"] not in CATEGORIES:
                raise ApiError(400, "VALIDATION", "category가 올바르지 않습니다.")
            o["category"] = b["category"]
        if "priority" in b:
            p = (b["priority"] or "").upper()
            if p not in PRIORITIES:
                raise ApiError(400, "VALIDATION", "priority가 올바르지 않습니다.")
            o["priority"] = p
            o["dueDate"] = compute_due(o["createdAt"], p)  # 우선순위 변경 시 SLA 기한 재산정
        for f in ("location", "description", "department"):
            if f in b:
                o[f] = (b[f] or "").strip()
        if "attachments" in b:
            o["attachments"] = norm_attachments(b["attachments"])
        hist(o, b.get("actor"), "수정")
        store._save()
        return o


@app.post("/api/orders/{oid}/assign")
async def assign(oid: str, request: Request):
    b = await body(request)
    assignee = (b.get("assignee") or "").strip()
    if not assignee:
        raise ApiError(400, "VALIDATION", "assignee는 필수입니다.")
    with store.lock:
        o = get_order(oid)
        if o["status"] not in ("RECEIVED", "ASSIGNED"):
            raise ApiError(409, "INVALID_STATE", "접수/배정 상태의 요청만 배정할 수 있습니다.")
        o["assignee"] = assignee
        o["status"] = "ASSIGNED"
        hist(o, b.get("actor"), "배정", assignee)
        store._save()
        return o


@app.post("/api/orders/{oid}/start")
async def start(oid: str, request: Request):
    b = await body(request)
    with store.lock:
        o = get_order(oid)
        if o["status"] != "ASSIGNED":
            raise ApiError(409, "INVALID_STATE", "배정된 요청만 작업을 시작할 수 있습니다.")
        if not o["assignee"]:
            raise ApiError(409, "NO_ASSIGNEE", "담당자가 없습니다.")
        o["status"] = "IN_PROGRESS"
        hist(o, b.get("actor") or o["assignee"], "작업시작")
        store._save()
        return o


@app.post("/api/orders/{oid}/hold")
async def hold(oid: str, request: Request):
    b = await body(request)
    reason = (b.get("reason") or "").strip()
    if not reason:
        raise ApiError(400, "VALIDATION", "보류 사유(reason)는 필수입니다.")
    with store.lock:
        o = get_order(oid)
        if o["status"] not in ("ASSIGNED", "IN_PROGRESS"):
            raise ApiError(409, "INVALID_STATE", "배정/작업중 요청만 보류할 수 있습니다.")
        o["heldFrom"] = o["status"]
        o["holdStart"] = fmt(now())
        o["status"] = "ON_HOLD"
        hist(o, b.get("actor"), "보류", reason)
        store._save()
        return o


@app.post("/api/orders/{oid}/resume")
async def resume(oid: str, request: Request):
    b = await body(request)
    with store.lock:
        o = get_order(oid)
        if o["status"] != "ON_HOLD":
            raise ApiError(409, "INVALID_STATE", "보류 상태의 요청만 재개할 수 있습니다.")
        if o.get("holdStart"):  # 보류 시간만큼 SLA 기한 연장(대기시간 제외)
            o["dueDate"] = fmt(parse(o["dueDate"]) + (now() - parse(o["holdStart"])))
            o["holdStart"] = None
        o["status"] = o.get("heldFrom") or "ASSIGNED"
        o["heldFrom"] = None
        hist(o, b.get("actor"), "재개")
        store._save()
        return o


@app.post("/api/orders/{oid}/complete")
async def complete(oid: str, request: Request):
    b = await body(request)
    note = (b.get("completionNote") or "").strip()
    if not note:
        raise ApiError(400, "VALIDATION", "완료 내용(completionNote)은 필수입니다.")
    cost = b.get("cost")
    if cost is not None and (not isinstance(cost, (int, float)) or cost < 0):
        raise ApiError(400, "VALIDATION", "cost는 0 이상의 숫자여야 합니다.")
    with store.lock:
        o = get_order(oid)
        if o["status"] != "IN_PROGRESS":
            raise ApiError(409, "INVALID_STATE", "작업중 요청만 완료할 수 있습니다.")
        o["status"] = "DONE"
        o["completionNote"] = note
        o["cost"] = cost
        hist(o, b.get("actor") or o["assignee"], "완료", note)
        store._save()
        return o


@app.post("/api/orders/{oid}/reject")
async def reject(oid: str, request: Request):
    b = await body(request)
    reason = (b.get("reason") or "").strip()
    if not reason:
        raise ApiError(400, "VALIDATION", "반려 사유(reason)는 필수입니다.")
    with store.lock:
        o = get_order(oid)
        if o["status"] in ("DONE", "REJECTED", "IN_PROGRESS"):
            raise ApiError(409, "INVALID_STATE", "작업중/완료/이미 반려된 요청은 반려할 수 없습니다.")
        o["status"] = "REJECTED"
        hist(o, b.get("actor"), "반려", reason)
        store._save()
        return o


@app.post("/api/orders/{oid}/reopen")
async def reopen(oid: str, request: Request):
    b = await body(request)
    reason = (b.get("reason") or "").strip()
    if not reason:
        raise ApiError(400, "VALIDATION", "재오픈 사유(reason)는 필수입니다.")
    with store.lock:
        o = get_order(oid)
        if o["status"] != "DONE":
            raise ApiError(409, "INVALID_STATE", "완료된 요청만 재오픈할 수 있습니다.(하자/재발)")
        o["status"] = "IN_PROGRESS" if o["assignee"] else "RECEIVED"
        o["completionNote"] = None
        o["dueDate"] = compute_due(fmt(now()), o["priority"])  # 재작업 기한 재산정
        hist(o, b.get("actor"), "재오픈", reason)
        store._save()
        return o


@app.get("/api/stats")
async def stats():
    with store.lock:
        by_status = {s: 0 for s in STATUS}
        by_category, by_priority, cost_by_category = {}, {}, {}
        overdue = 0
        total_cost = 0
        for o in store.orders.values():
            by_status[o["status"]] = by_status.get(o["status"], 0) + 1
            by_category[o["category"]] = by_category.get(o["category"], 0) + 1
            by_priority[o["priority"]] = by_priority.get(o["priority"], 0) + 1
            if is_overdue(o):
                overdue += 1
            if o["status"] == "DONE" and o.get("cost"):
                total_cost += o["cost"]
                cost_by_category[o["category"]] = cost_by_category.get(o["category"], 0) + o["cost"]
        return {"total": len(store.orders), "byStatus": by_status,
                "byCategory": by_category, "byPriority": by_priority, "overdue": overdue,
                "totalCost": total_cost, "costByCategory": cost_by_category}


INDEX = """<!doctype html><html lang=ko><head><meta charset=utf-8>
<meta name=viewport content="width=device-width, initial-scale=1"><title>시설 유지보수 관리</title>
<style>
:root{--line:#e2e8f0;--ink:#1e293b;--mut:#64748b;--blue:#2563eb;--bg:#f8fafc}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,'Malgun Gothic',sans-serif;color:var(--ink);background:var(--bg)}
header{background:#fff;border-bottom:1px solid var(--line);padding:16px 24px}header h1{font-size:20px;margin:0}header p{margin:4px 0 0;color:var(--mut);font-size:13px}
.wrap{max-width:1100px;margin:0 auto;padding:20px 24px}
.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:18px}
.card{background:#fff;border:1px solid var(--line);border-radius:12px;padding:14px}.card .lbl{font-size:12px;color:var(--mut)}.card .val{font-size:22px;font-weight:800;margin-top:4px}
.toolbar{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:12px}
input,select,button{font:inherit;padding:8px 10px;border:1px solid var(--line);border-radius:8px;background:#fff;color:var(--ink)}button{cursor:pointer}.btn-primary{background:var(--blue);color:#fff;border-color:var(--blue);font-weight:600}.btn-sm{padding:4px 8px;font-size:12px}
table{width:100%;border-collapse:collapse;background:#fff;border:1px solid var(--line);border-radius:12px;overflow:hidden;font-size:13px}
th,td{text-align:left;padding:10px 12px;border-bottom:1px solid var(--line)}th{background:#f1f5f9;color:var(--mut);font-size:11px;text-transform:uppercase}tr:last-child td{border-bottom:none}
.badge{display:inline-block;padding:2px 8px;border-radius:999px;font-size:11px;font-weight:700}
.s-RECEIVED{background:#e0e7ff;color:#3730a3}.s-ASSIGNED{background:#dbeafe;color:#1e40af}.s-IN_PROGRESS{background:#fef3c7;color:#92400e}.s-ON_HOLD{background:#e2e8f0;color:#475569}.s-DONE{background:#dcfce7;color:#166534}.s-REJECTED{background:#fee2e2;color:#991b1b}
.pri-URGENT{color:#dc2626;font-weight:700}.pri-HIGH{color:#ea580c;font-weight:600}.ovd{color:#dc2626;font-size:11px;font-weight:700}
dialog{border:none;border-radius:14px;max-width:440px;width:92%;padding:0}form{padding:20px}form h3{margin:0 0 14px}.fld{margin-bottom:10px}.fld label{display:block;font-size:12px;color:var(--mut);margin-bottom:4px}.fld input,.fld select{width:100%}.rw{display:flex;gap:8px}.rw>*{flex:1}
.modal-actions{display:flex;gap:8px;justify-content:flex-end;margin-top:16px}
</style></head><body>
<header><h1>학교 시설 유지보수 관리</h1><p>접수 → 배정 → 작업 → 완료(보류/반려) · 우선순위별 SLA</p></header>
<div class="wrap">
<div class="stats" id="stats"></div>
<div class="toolbar"><input id="q" placeholder="제목·위치 검색" style="min-width:200px">
<select id="fstatus"><option value="">전체 상태</option></select><button onclick="load()">조회</button>
<span style="flex:1"></span><button class="btn-primary" onclick="reg.showModal()">+ 정비 접수</button></div>
<table><thead><tr><th>제목</th><th>분류</th><th>우선순위</th><th>상태</th><th>담당자</th><th>기한</th><th>처리</th></tr></thead><tbody id="rows"></tbody></table>
</div>
<dialog id="reg"><form onsubmit="return submitReg(event)"><h3>정비 접수</h3>
<div class="fld"><label>제목 *</label><input id="r-title" required></div>
<div class="rw"><div class="fld"><label>요청자 *</label><input id="r-req" required></div><div class="fld"><label>위치 *</label><input id="r-loc" required></div></div>
<div class="rw"><div class="fld"><label>분류</label><select id="r-cat"></select></div><div class="fld"><label>우선순위</label><select id="r-pri"></select></div></div>
<div class="modal-actions"><button type="button" onclick="reg.close()">취소</button><button class="btn-primary" type="submit">접수</button></div>
</form></dialog>
<script>
var CATS=['전기','배관','냉난방','목공','도장','기타'],PRIS=['LOW','NORMAL','HIGH','URGENT'];
var STS={RECEIVED:'접수',ASSIGNED:'배정',IN_PROGRESS:'작업중',ON_HOLD:'보류',DONE:'완료',REJECTED:'반려'};
function esc(s){s=s||'';return s.replace(/[&<>]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;'}[c];});}
function opt(sel,arr,lbl){for(var i=0;i<arr.length;i++){var o=document.createElement('option');o.value=arr[i];o.textContent=lbl?(lbl[arr[i]]||arr[i]):arr[i];sel.appendChild(o);}}
opt(document.getElementById('fstatus'),Object.keys(STS),STS);opt(document.getElementById('r-cat'),CATS);opt(document.getElementById('r-pri'),PRIS);
function jget(u){return fetch(u).then(function(r){return r.json();});}
function jpost(u,b){return fetch(u,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b||{})}).then(function(r){return r.json().then(function(d){return {ok:r.ok,d:d};});});}
function acts(o){var b=function(t,f){return '<button class="btn-sm" onclick="'+f+'">'+t+'</button> ';};var id=o.id;
 if(o.status==='RECEIVED')return b('배정',"assign("+id+")")+b('반려',"reason("+id+",'reject')");
 if(o.status==='ASSIGNED')return b('작업시작',"act("+id+",'start')")+b('보류',"reason("+id+",'hold')");
 if(o.status==='IN_PROGRESS')return b('완료',"complete("+id+")")+b('보류',"reason("+id+",'hold')");
 if(o.status==='ON_HOLD')return b('재개',"act("+id+",'resume')");
 if(o.status==='DONE')return b('재오픈',"reason("+id+",'reopen')");
 return '-';}
function load(){
 var q=new URLSearchParams();var qq=document.getElementById('q').value.trim();if(qq)q.set('q',qq);
 var st=document.getElementById('fstatus').value;if(st)q.set('status',st);q.set('size','100');
 jget('/api/orders?'+q).then(function(d){var rows=document.getElementById('rows');rows.innerHTML='';
  if(!d.items.length)rows.innerHTML='<tr><td colspan=7 style="text-align:center;color:#94a3b8;padding:30px">요청이 없습니다.</td></tr>';
  d.items.forEach(function(o){var tr=document.createElement('tr');
   tr.innerHTML='<td><b>'+esc(o.title)+'</b><br><span style=color:#94a3b8;font-size:11px>'+esc(o.location)+'</span></td><td>'+o.category+'</td>'+
    '<td class="pri-'+o.priority+'">'+o.priority+'</td><td><span class="badge s-'+o.status+'">'+STS[o.status]+'</span></td>'+
    '<td>'+esc(o.assignee||'-')+'</td><td>'+(o.overdue?'<span class=ovd>기한초과</span>':(o.dueDate||'').slice(5,10))+'</td><td>'+acts(o)+'</td>';
   rows.appendChild(tr);});});
 jget('/api/stats').then(function(s){document.getElementById('stats').innerHTML=
  card('전체',s.total+'건')+card('작업중',(s.byStatus.IN_PROGRESS||0)+'건')+card('완료',(s.byStatus.DONE||0)+'건')+card('기한초과',s.overdue+'건');});
}
function card(l,v){return '<div class="card"><div class="lbl">'+l+'</div><div class="val">'+v+'</div></div>';}
function act(id,kind){jpost('/api/orders/'+id+'/'+kind,{actor:'담당자'}).then(function(r){if(!r.ok)alert('오류: '+(r.d.error?r.d.error.message:''));load();});}
function assign(id){var a=prompt('담당자');if(!a)return;jpost('/api/orders/'+id+'/assign',{assignee:a,actor:'관리자'}).then(function(r){if(!r.ok)alert('오류');load();});}
function complete(id){var n=prompt('완료 내용');if(!n)return;var c=prompt('비용(원, 없으면 0)','0');jpost('/api/orders/'+id+'/complete',{completionNote:n,cost:Number(c)||0,actor:'담당자'}).then(function(r){if(!r.ok)alert('오류');load();});}
function reason(id,kind){var r=prompt('사유');if(!r)return;jpost('/api/orders/'+id+'/'+kind,{reason:r,actor:'담당자'}).then(function(x){if(!x.ok)alert('오류: '+(x.d.error?x.d.error.message:''));load();});}
function submitReg(e){e.preventDefault();
 jpost('/api/orders',{title:document.getElementById('r-title').value,requester:document.getElementById('r-req').value,location:document.getElementById('r-loc').value,category:document.getElementById('r-cat').value,priority:document.getElementById('r-pri').value}).then(function(r){
  if(!r.ok){alert('오류: '+(r.d.error?r.d.error.message:''));return;}reg.close();document.getElementById('r-title').value='';load();});return false;}
load();
</script></body></html>"""


@app.get("/", response_class=HTMLResponse)
async def index():
    return INDEX


store = Store()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8080")))
