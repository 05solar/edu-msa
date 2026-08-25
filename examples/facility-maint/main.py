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


INDEX = """<!doctype html><html lang=ko><meta charset=utf-8>
<title>시설 유지보수 · facility-maint</title>
<style>body{font-family:system-ui,"Malgun Gothic",sans-serif;max-width:820px;margin:32px auto;padding:0 16px;color:#1e293b}
h1{font-size:22px}code{background:#eef2f8;padding:2px 6px;border-radius:5px}li{margin:5px 0}</style>
<h1>학교 시설 유지보수 관리 (facility-maint)</h1>
<p>접수 → 배정 → 작업시작 → 완료(보류/재개/반려) 흐름과 SLA·통계를 제공합니다.</p>
<ul>
<li><code>GET /healthz</code></li>
<li><code>GET /api/orders?status=&category=&priority=&assignee=&q=&page=&size=</code></li>
<li><code>POST /api/orders</code> 접수 · <code>PATCH /api/orders/{id}</code> 수정</li>
<li><code>POST /api/orders/{id}/assign|start|hold|resume|complete|reject|reopen</code></li>
<li><code>GET /api/orders/{id}/history</code> · <code>GET /api/stats</code></li>
</ul>
<p>샘플 정비요청 3건이 시드되어 있습니다. 배포 경로 <code>/svc/facility-maint</code>.</p></html>"""


@app.get("/", response_class=HTMLResponse)
async def index():
    return INDEX


store = Store()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8080")))
