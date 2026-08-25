// civil-desk · 학생·학부모 민원 처리 (TypeScript, Fastify)
// 접수 → 배정 → 처리 → 답변 → 종결 흐름을 상태 전이로 관리하고 SLA·에스컬레이션·만족도를 제공한다.
import Fastify from 'fastify';
import { readFileSync, writeFileSync, renameSync, existsSync } from 'node:fs';

const KST = 9 * 3600 * 1000;
const FMT = (ms: number) => new Date(ms + KST).toISOString().slice(0, 19).replace('T', ' ');
const nowMs = () => Date.now();
const nowStr = () => FMT(nowMs());
const parseKst = (s: string) => Date.parse(s.replace(' ', 'T') + '+09:00');

const CATEGORIES = ['학사', '급식', '시설', '교통', '교권', '기타'];
const CHANNELS = ['전화', '방문', '온라인', '서면'];
const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];
const SLA_HOURS: Record<string, number> = { URGENT: 24, HIGH: 72, NORMAL: 168, LOW: 336 };
const STATUSES = ['RECEIVED', 'ASSIGNED', 'IN_PROGRESS', 'ANSWERED', 'CLOSED', 'REJECTED', 'WITHDRAWN'];

// 목록 노출 시 개인정보(연락처) 마스킹. 상세(담당자)에서는 원본 노출.
function maskContact(v: string): string {
  if (!v) return '';
  if (v.includes('@')) { const [a, d] = v.split('@'); return a.slice(0, 2) + '***@' + d; }
  return v.replace(/(\d{2,3})[-\d]*(\d{2})$/, '$1****$2');
}

class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message); }
}

interface Answer { at: string; by: string; content: string; }
interface Hist { at: string; actor: string; act: string; memo?: string; }
interface Notification { at: string; to: string; channel: string; content: string; status: string; }
interface Complaint {
  id: number; title: string; content: string; category: string; channel: string;
  complainant: string; contact: string; anonymous: boolean; priority: string;
  status: string; assignee: string | null; dueDate: string; escalated: boolean;
  satisfaction: number | null; answers: Answer[]; notifications: Notification[]; history: Hist[];
  createdAt: string; updatedAt: string;
}

const DATA_FILE = process.env.DATA_FILE || '';
let seq = 0;
const store = new Map<number, Complaint>();

function dueFrom(createdMs: number, priority: string): string {
  return FMT(createdMs + SLA_HOURS[priority] * 3600 * 1000);
}
function isOverdue(c: Complaint): boolean {
  if (['CLOSED', 'REJECTED', 'WITHDRAWN'].includes(c.status)) return false;
  return parseKst(c.dueDate) < nowMs();
}
function hist(c: Complaint, actor: string | undefined, act: string, memo?: string) {
  c.history.push({ at: nowStr(), actor: actor || 'system', act, ...(memo ? { memo } : {}) });
  c.updatedAt = nowStr();
}

function save() {
  if (!DATA_FILE) return;
  try {
    const snap = { seq, complaints: [...store.values()] };
    writeFileSync(DATA_FILE + '.tmp', JSON.stringify(snap, null, 1));
    renameSync(DATA_FILE + '.tmp', DATA_FILE);
  } catch (e) { console.error('save failed', (e as Error).message); }
}
function load(): boolean {
  try {
    const snap = JSON.parse(readFileSync(DATA_FILE, 'utf-8'));
    seq = snap.seq || 0;
    for (const c of snap.complaints || []) store.set(c.id, c);
    return store.size > 0;
  } catch { return false; }
}

function seed() {
  const mk = (o: Partial<Complaint>): Complaint => {
    const id = ++seq;
    const created = o.createdAt || nowStr();
    const c: Complaint = {
      id, title: o.title!, content: o.content || '', category: o.category || '기타',
      channel: o.channel || '온라인', complainant: o.complainant || '민원인', contact: o.contact || '',
      anonymous: o.anonymous || false, priority: o.priority || 'NORMAL', status: o.status || 'RECEIVED',
      assignee: o.assignee ?? null, dueDate: o.dueDate || dueFrom(parseKst(created), o.priority || 'NORMAL'),
      escalated: o.escalated || false, satisfaction: o.satisfaction ?? null,
      answers: o.answers || [], notifications: o.notifications || [],
      history: o.history || [{ at: created, actor: o.complainant || '민원인', act: '접수' }],
      createdAt: created, updatedAt: o.updatedAt || created,
    };
    store.set(id, c);
    return c;
  };
  mk({ title: '통학버스 노선 변경 문의', content: '아파트 신축으로 정류장 추가 요청', category: '교통',
    channel: '전화', complainant: '학부모 김OO', contact: '010-1111-2222', priority: 'HIGH',
    status: 'IN_PROGRESS', assignee: '교통행정 이주무관', createdAt: '2026-08-22 09:00:00',
    history: [{ at: '2026-08-22 09:00:00', actor: '학부모 김OO', act: '접수' },
      { at: '2026-08-22 10:00:00', actor: '민원팀', act: '배정', memo: '교통행정 이주무관' },
      { at: '2026-08-22 14:00:00', actor: '교통행정 이주무관', act: '처리시작' }] });
  mk({ title: '급식 알레르기 표시 개선 건의', content: '알레르기 유발식품 표시가 불명확', category: '급식',
    channel: '온라인', complainant: '학부모 박OO', contact: 'park@example.com', priority: 'NORMAL',
    status: 'CLOSED', assignee: '영양교사 최OO', satisfaction: 5, createdAt: '2026-08-10 11:00:00',
    answers: [{ at: '2026-08-12 09:00:00', by: '영양교사 최OO', content: '9월부터 알레르기 유발식품 19종 색상표시 적용 예정입니다.' }],
    history: [{ at: '2026-08-10 11:00:00', actor: '학부모 박OO', act: '접수' },
      { at: '2026-08-11 09:00:00', actor: '민원팀', act: '배정', memo: '영양교사 최OO' },
      { at: '2026-08-12 09:00:00', actor: '영양교사 최OO', act: '답변' },
      { at: '2026-08-12 15:00:00', actor: '민원팀', act: '종결' }] });
  mk({ title: '운동장 야간 소음 민원', content: '야간 외부인 이용으로 소음 발생', category: '시설',
    channel: '방문', complainant: '인근 주민', contact: '010-3333-4444', priority: 'NORMAL',
    status: 'RECEIVED', createdAt: '2026-08-25 08:30:00' });
}

// ---- Fastify ----
const app = Fastify({ logger: { level: 'warn' } });

app.setErrorHandler((err, _req, reply) => {
  if (err instanceof ApiError) reply.status(err.status).send({ error: { code: err.code, message: err.message } });
  else if ((err as any).statusCode === 400) reply.status(400).send({ error: { code: 'INVALID_JSON', message: '요청 본문을 해석할 수 없습니다.' } });
  else reply.status(500).send({ error: { code: 'INTERNAL', message: err.message } });
});
app.setNotFoundHandler((_req, reply) => reply.status(404).send({ error: { code: 'NOT_FOUND', message: '경로를 찾을 수 없습니다.' } }));

const getC = (idRaw: string): Complaint => {
  const id = Number(idRaw);
  if (!Number.isInteger(id)) throw new ApiError(400, 'VALIDATION', 'id가 올바르지 않습니다.');
  const c = store.get(id);
  if (!c) throw new ApiError(404, 'NOT_FOUND', `민원 ${idRaw} 를 찾을 수 없습니다.`);
  return c;
};
const s = (v: any) => (v == null ? '' : String(v).trim());
const bodyOf = (req: any): any => (req.body && typeof req.body === 'object' ? req.body : {});

app.get('/healthz', async () => ({ status: 'ok', service: 'civil-desk', time: nowStr() }));
app.get('/', async (_req, reply) => reply.type('text/html').send(INDEX));

app.get('/api/complaints', async (req) => {
  const q = req.query as any;
  const status = s(q.status).toUpperCase(), category = s(q.category), assignee = s(q.assignee);
  const priority = s(q.priority).toUpperCase(), kw = s(q.q);
  let page = parseInt(q.page) || 1; if (page < 1) page = 1;
  let size = parseInt(q.size) || 10; if (size < 1 || size > 100) size = 10;
  let items = [...store.values()].filter(c =>
    (!status || c.status === status) && (!category || c.category === category) &&
    (!assignee || c.assignee === assignee) && (!priority || c.priority === priority) &&
    (!kw || c.title.includes(kw) || c.content.includes(kw) || c.complainant.includes(kw)));
  const sort = s(q.sort);
  if (sort === 'due') items.sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  else if (sort === 'priority') { const r: any = { URGENT: 0, HIGH: 1, NORMAL: 2, LOW: 3 }; items.sort((a, b) => r[a.priority] - r[b.priority]); }
  else items.sort((a, b) => b.id - a.id);
  const total = items.length, start = (page - 1) * size;
  return {
    page, size, total,
    items: items.slice(start, start + size).map(c => ({
      ...c, contact: maskContact(c.contact),
      notifications: c.notifications.map(n => ({ ...n, to: maskContact(n.to) })),
      overdue: isOverdue(c),
    })),
  };
});

app.post('/api/complaints', async (req, reply) => {
  const b = bodyOf(req);
  const title = s(b.title), content = s(b.content), complainant = s(b.complainant);
  if (!title || !content) throw new ApiError(400, 'VALIDATION', 'title, content는 필수입니다.');
  if (title.length > 150) throw new ApiError(400, 'VALIDATION', 'title은 150자 이하여야 합니다.');
  const category = s(b.category) || '기타';
  if (!CATEGORIES.includes(category)) throw new ApiError(400, 'VALIDATION', `category는 ${CATEGORIES.join('/')} 중 하나여야 합니다.`);
  const channel = s(b.channel) || '온라인';
  if (!CHANNELS.includes(channel)) throw new ApiError(400, 'VALIDATION', `channel은 ${CHANNELS.join('/')} 중 하나여야 합니다.`);
  const priority = (s(b.priority) || 'NORMAL').toUpperCase();
  if (!PRIORITIES.includes(priority)) throw new ApiError(400, 'VALIDATION', 'priority가 올바르지 않습니다.');
  const anonymous = b.anonymous === true;
  if (!anonymous && !complainant) throw new ApiError(400, 'VALIDATION', '익명이 아니면 complainant는 필수입니다.');
  if (!anonymous && !s(b.contact)) throw new ApiError(400, 'VALIDATION', '비익명 민원은 contact(연락처)가 필수입니다.');
  const created = nowStr();
  const id = ++seq;
  const c: Complaint = {
    id, title, content, category, channel, complainant: anonymous ? '익명' : complainant,
    contact: s(b.contact), anonymous, priority, status: 'RECEIVED', assignee: null,
    dueDate: dueFrom(parseKst(created), priority), escalated: false, satisfaction: null,
    answers: [], notifications: [], history: [], createdAt: created, updatedAt: created,
  };
  hist(c, c.complainant, '접수');
  store.set(id, c);
  save();
  reply.status(201).send(c);
});

app.get('/api/complaints/:id', async (req) => ({ ...getC((req.params as any).id), overdue: isOverdue(getC((req.params as any).id)) }));
app.get('/api/complaints/:id/history', async (req) => { const c = getC((req.params as any).id); return { id: c.id, history: c.history }; });

app.patch('/api/complaints/:id', async (req) => {
  const c = getC((req.params as any).id); const b = bodyOf(req);
  if (!['RECEIVED'].includes(c.status)) throw new ApiError(409, 'INVALID_STATE', '접수 상태의 민원만 수정할 수 있습니다.');
  if ('title' in b) { const t = s(b.title); if (!t || t.length > 150) throw new ApiError(400, 'VALIDATION', 'title은 1~150자'); c.title = t; }
  if ('content' in b) c.content = s(b.content);
  if ('category' in b) { if (!CATEGORIES.includes(s(b.category))) throw new ApiError(400, 'VALIDATION', 'category 오류'); c.category = s(b.category); }
  if ('priority' in b) {
    const p = s(b.priority).toUpperCase(); if (!PRIORITIES.includes(p)) throw new ApiError(400, 'VALIDATION', 'priority 오류');
    c.priority = p; c.dueDate = dueFrom(parseKst(c.createdAt), p);
  }
  if ('contact' in b) c.contact = s(b.contact);
  hist(c, s(b.actor) || undefined, '수정'); save(); return c;
});

app.post('/api/complaints/:id/assign', async (req) => {
  const c = getC((req.params as any).id); const b = bodyOf(req);
  const assignee = s(b.assignee);
  if (!assignee) throw new ApiError(400, 'VALIDATION', 'assignee는 필수입니다.');
  if (!['RECEIVED', 'ASSIGNED'].includes(c.status)) throw new ApiError(409, 'INVALID_STATE', '접수/배정 상태만 배정할 수 있습니다.');
  c.assignee = assignee; c.status = 'ASSIGNED';
  hist(c, s(b.actor) || undefined, '배정', assignee); save(); return c;
});

app.post('/api/complaints/:id/start', async (req) => {
  const c = getC((req.params as any).id); const b = bodyOf(req);
  if (c.status !== 'ASSIGNED') throw new ApiError(409, 'INVALID_STATE', '배정된 민원만 처리 시작할 수 있습니다.');
  c.status = 'IN_PROGRESS'; hist(c, s(b.actor) || c.assignee || undefined, '처리시작'); save(); return c;
});

app.post('/api/complaints/:id/answer', async (req) => {
  const c = getC((req.params as any).id); const b = bodyOf(req);
  const content = s(b.content);
  if (!content) throw new ApiError(400, 'VALIDATION', '답변 내용(content)은 필수입니다.');
  if (!['ASSIGNED', 'IN_PROGRESS', 'ANSWERED'].includes(c.status)) throw new ApiError(409, 'INVALID_STATE', '배정/처리중/답변 상태만 답변할 수 있습니다.');
  const by = s(b.actor) || c.assignee || 'system';
  c.answers.push({ at: nowStr(), by, content });
  c.status = 'ANSWERED';
  // 회신 통지 아웃박스에 적재(실제 SMS/메일 발송은 플랫폼 알림 서비스가 QUEUED 건을 처리)
  if (c.contact) c.notifications.push({ at: nowStr(), to: c.contact, channel: c.channel, content, status: 'QUEUED' });
  hist(c, by, '답변'); save(); return c;
});

app.post('/api/complaints/:id/close', async (req) => {
  const c = getC((req.params as any).id); const b = bodyOf(req);
  if (c.status !== 'ANSWERED') throw new ApiError(409, 'INVALID_STATE', '답변된 민원만 종결할 수 있습니다.');
  if (c.answers.length === 0) throw new ApiError(409, 'NO_ANSWER', '답변이 없어 종결할 수 없습니다.');
  c.status = 'CLOSED'; hist(c, s(b.actor) || undefined, '종결'); save(); return c;
});

app.post('/api/complaints/:id/reject', async (req) => {
  const c = getC((req.params as any).id); const b = bodyOf(req);
  const reason = s(b.reason);
  if (!reason) throw new ApiError(400, 'VALIDATION', '반려 사유(reason)는 필수입니다.');
  if (!['RECEIVED', 'ASSIGNED'].includes(c.status)) throw new ApiError(409, 'INVALID_STATE', '접수/배정 상태만 반려할 수 있습니다.');
  c.status = 'REJECTED'; hist(c, s(b.actor) || undefined, '반려', reason); save(); return c;
});

app.post('/api/complaints/:id/escalate', async (req) => {
  const c = getC((req.params as any).id); const b = bodyOf(req);
  const reason = s(b.reason);
  if (!reason) throw new ApiError(400, 'VALIDATION', '에스컬레이션 사유(reason)는 필수입니다.');
  if (['CLOSED', 'REJECTED', 'WITHDRAWN'].includes(c.status)) throw new ApiError(409, 'INVALID_STATE', '종결/반려/취하 민원은 에스컬레이션할 수 없습니다.');
  c.escalated = true;
  if (c.priority === 'LOW') c.priority = 'NORMAL'; else if (c.priority === 'NORMAL') c.priority = 'HIGH'; else if (c.priority === 'HIGH') c.priority = 'URGENT';
  c.dueDate = dueFrom(nowMs(), c.priority); // 상향 시점 기준 SLA 재산정
  if (s(b.to)) c.assignee = s(b.to);
  hist(c, s(b.actor) || undefined, '에스컬레이션', reason); save(); return c;
});

app.post('/api/complaints/:id/reopen', async (req) => {
  const c = getC((req.params as any).id); const b = bodyOf(req);
  const reason = s(b.reason);
  if (!reason) throw new ApiError(400, 'VALIDATION', '재처리 사유(reason)는 필수입니다.');
  if (c.status === 'CLOSED') { c.status = 'IN_PROGRESS'; c.satisfaction = null; }
  else if (c.status === 'REJECTED') { c.status = 'RECEIVED'; }
  else throw new ApiError(409, 'INVALID_STATE', '종결/반려된 민원만 재개할 수 있습니다.');
  c.dueDate = dueFrom(nowMs(), c.priority);
  hist(c, s(b.actor) || undefined, '재개', reason); save(); return c;
});

app.post('/api/complaints/:id/withdraw', async (req) => {
  const c = getC((req.params as any).id); const b = bodyOf(req);
  if (['CLOSED', 'REJECTED', 'WITHDRAWN'].includes(c.status)) throw new ApiError(409, 'INVALID_STATE', '종결/반려/취하된 민원은 취하할 수 없습니다.');
  c.status = 'WITHDRAWN'; hist(c, s(b.actor) || c.complainant, '취하', s(b.reason) || undefined); save(); return c;
});

app.post('/api/complaints/:id/satisfaction', async (req) => {
  const c = getC((req.params as any).id); const b = bodyOf(req);
  const score = Number(b.score);
  if (!Number.isInteger(score) || score < 1 || score > 5) throw new ApiError(400, 'VALIDATION', 'score는 1~5 정수여야 합니다.');
  if (c.status !== 'CLOSED') throw new ApiError(409, 'INVALID_STATE', '종결된 민원만 만족도를 등록할 수 있습니다.');
  c.satisfaction = score; hist(c, s(b.actor) || undefined, '만족도', String(score)); save(); return c;
});

app.get('/api/stats', async () => {
  const byStatus: Record<string, number> = {}; STATUSES.forEach(x => byStatus[x] = 0);
  const byCategory: Record<string, number> = {};
  let overdue = 0, satSum = 0, satCnt = 0, escalated = 0;
  for (const c of store.values()) {
    byStatus[c.status] = (byStatus[c.status] || 0) + 1;
    byCategory[c.category] = (byCategory[c.category] || 0) + 1;
    if (isOverdue(c)) overdue++;
    if (c.escalated) escalated++;
    if (c.satisfaction != null) { satSum += c.satisfaction; satCnt++; }
  }
  return { total: store.size, byStatus, byCategory, overdue, escalated,
    avgSatisfaction: satCnt ? Math.round((satSum / satCnt) * 100) / 100 : null };
});

const INDEX = `<!doctype html><html lang=ko><meta charset=utf-8>
<title>민원 처리 · civil-desk</title>
<style>body{font-family:system-ui,'Malgun Gothic',sans-serif;max-width:820px;margin:32px auto;padding:0 16px;color:#1e293b}
h1{font-size:22px}code{background:#eef2f8;padding:2px 6px;border-radius:5px}li{margin:5px 0}</style>
<h1>학생·학부모 민원 처리 (civil-desk)</h1>
<p>접수 → 배정 → 처리 → 답변 → 종결 흐름과 SLA·에스컬레이션·만족도를 제공합니다.</p><ul>
<li><code>GET /healthz</code></li>
<li><code>GET /api/complaints?status=&category=&assignee=&priority=&q=&sort=&page=&size=</code></li>
<li><code>POST /api/complaints</code> 접수 · <code>PATCH /api/complaints/{id}</code> 수정</li>
<li><code>POST /api/complaints/{id}/assign|start|answer|close|reject|escalate|reopen|withdraw|satisfaction</code></li>
<li><code>GET /api/complaints/{id}/history</code> · <code>GET /api/stats</code></li>
</ul><p>샘플 민원 3건 시드. 배포 경로 <code>/svc/civil-desk</code>.</p></html>`;

// ---- 시작 ----
if (DATA_FILE && existsSync(DATA_FILE)) {
  if (load()) console.log(`loaded ${store.size} complaints from ${DATA_FILE}`);
  else { console.warn('load 실패 — 인메모리 시드'); seed(); }
} else { seed(); save(); }

const port = parseInt(process.env.PORT || '8080');
app.listen({ host: '0.0.0.0', port }).then(() => console.log(`civil-desk listening on :${port}`));
