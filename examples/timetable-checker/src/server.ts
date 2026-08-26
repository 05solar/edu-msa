import Fastify from 'fastify'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const app = Fastify({ logger: false })
const PORT = Number(process.env.PORT || 8080)

const DAYS_ALL = ['월', '화', '수', '목', '금', '토']

interface Session {
  day: string
  period: number
  subject: string
  teacher?: string
  klass?: string
  room?: string
}

function fail(reply: any, code: string, message: string, status = 400) {
  reply.code(status).send({ error: { code, message } })
}

function cleanSessions(raw: any): Session[] {
  if (!Array.isArray(raw)) return []
  const out: Session[] = []
  for (const s of raw) {
    const day = String(s?.day ?? '').trim()
    const period = Number(s?.period)
    const subject = String(s?.subject ?? '').trim()
    if (!DAYS_ALL.includes(day) || !Number.isInteger(period) || period < 1 || period > 15 || !subject) continue
    out.push({
      day, period, subject,
      teacher: String(s?.teacher ?? '').trim(),
      klass: String(s?.klass ?? '').trim(),
      room: String(s?.room ?? '').trim(),
    })
  }
  return out
}

const FIELD_LABEL: Record<string, string> = { teacher: '교사', room: '교실', klass: '학급' }

function findConflicts(sessions: Session[]) {
  const groups = new Map<string, Session[]>()
  for (const s of sessions) {
    const k = `${s.day}|${s.period}`
    if (!groups.has(k)) groups.set(k, [])
    groups.get(k)!.push(s)
  }
  const conflicts: any[] = []
  for (const [k, list] of groups) {
    const [day, periodStr] = k.split('|')
    for (const field of ['teacher', 'room', 'klass'] as const) {
      const byVal = new Map<string, Session[]>()
      for (const s of list) {
        const v = (s[field] || '').trim()
        if (!v) continue
        if (!byVal.has(v)) byVal.set(v, [])
        byVal.get(v)!.push(s)
      }
      for (const [v, ss] of byVal) {
        if (ss.length > 1) {
          conflicts.push({
            day, period: Number(periodStr),
            type: field, typeLabel: FIELD_LABEL[field], value: v,
            members: ss.map((x) => `${x.klass || '-'} ${x.subject}${x.teacher ? ' (' + x.teacher + ')' : ''}`),
          })
        }
      }
    }
  }
  conflicts.sort((a, b) => a.period - b.period || a.day.localeCompare(b.day))
  return conflicts
}

function esc(s: string) {
  return String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c] as string))
}

function buildSvg(sessions: Session[], view: 'klass' | 'teacher' | 'room', key: string) {
  const days = DAYS_ALL.filter((d) => sessions.some((s) => s.day === d))
  const useDays = days.length ? days : ['월', '화', '수', '목', '금']
  const maxP = Math.max(5, ...sessions.map((s) => s.period))
  const cellW = 150, cellH = 58, labW = 56, headH = 40
  const W = labW + useDays.length * cellW
  const H = headH + maxP * cellH
  const mine = sessions.filter((s) => (s[view] || '') === key)
  // 셀별 세션(충돌 시 복수)
  const cellMap = new Map<string, Session[]>()
  for (const s of mine) {
    const k = `${s.day}|${s.period}`
    if (!cellMap.has(k)) cellMap.set(k, [])
    cellMap.get(k)!.push(s)
  }
  let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" font-family="'Malgun Gothic',sans-serif">`
  svg += `<rect width="${W}" height="${H}" fill="#ffffff"/>`
  // 헤더
  svg += `<rect x="0" y="0" width="${W}" height="${headH}" fill="#f1f5f9"/>`
  svg += `<rect x="0" y="0" width="${labW}" height="${H}" fill="#f8fafc"/>`
  useDays.forEach((d, i) => {
    const x = labW + i * cellW
    svg += `<text x="${x + cellW / 2}" y="${headH / 2 + 5}" text-anchor="middle" font-size="15" font-weight="700" fill="#334155">${d}</text>`
  })
  for (let p = 1; p <= maxP; p++) {
    const y = headH + (p - 1) * cellH
    svg += `<text x="${labW / 2}" y="${y + cellH / 2 + 5}" text-anchor="middle" font-size="13" font-weight="700" fill="#64748b">${p}</text>`
    useDays.forEach((d, i) => {
      const x = labW + i * cellW
      const list = cellMap.get(`${d}|${p}`) || []
      const clash = list.length > 1
      const fill = clash ? '#fee2e2' : list.length ? '#eff6ff' : '#ffffff'
      svg += `<rect x="${x}" y="${y}" width="${cellW}" height="${cellH}" fill="${fill}" stroke="#e2e8f0"/>`
      if (list.length) {
        const s = list[0]
        const sub = clash ? list.map((z) => z.subject).join(' / ') : s.subject
        const meta = view === 'teacher' ? (s.klass || '') : view === 'room' ? (s.klass || '') : (s.teacher || '')
        const roomTxt = view === 'room' ? '' : (s.room || '')
        svg += `<text x="${x + cellW / 2}" y="${y + 24}" text-anchor="middle" font-size="14" font-weight="700" fill="${clash ? '#b91c1c' : '#1e3a8a'}">${esc(sub)}</text>`
        const sub2 = [meta, roomTxt].filter(Boolean).join(' · ')
        if (sub2) svg += `<text x="${x + cellW / 2}" y="${y + 43}" text-anchor="middle" font-size="12" fill="#64748b">${esc(sub2)}</text>`
      }
    })
  }
  // 외곽선
  svg += `<rect x="0" y="0" width="${W}" height="${H}" fill="none" stroke="#cbd5e1"/>`
  svg += `<line x1="${labW}" y1="0" x2="${labW}" y2="${H}" stroke="#cbd5e1"/>`
  svg += `<line x1="0" y1="${headH}" x2="${W}" y2="${headH}" stroke="#cbd5e1"/>`
  svg += `</svg>`
  return svg
}

app.get('/healthz', async () => ({ status: 'ok', service: 'timetable-checker' }))

app.post('/api/check', async (req, reply) => {
  const sessions = cleanSessions((req.body as any)?.sessions)
  if (!sessions.length) return fail(reply, 'VALIDATION', '유효한 수업(요일·교시·과목)을 1개 이상 입력하세요.')
  const conflicts = findConflicts(sessions)
  const keys = {
    klass: [...new Set(sessions.map((s) => s.klass).filter(Boolean))],
    teacher: [...new Set(sessions.map((s) => s.teacher).filter(Boolean))],
    room: [...new Set(sessions.map((s) => s.room).filter(Boolean))],
  }
  return { count: sessions.length, conflicts, keys }
})

app.post('/api/timetable', async (req, reply) => {
  const body = req.body as any
  const sessions = cleanSessions(body?.sessions)
  const view = ['klass', 'teacher', 'room'].includes(body?.view) ? body.view : 'klass'
  const key = String(body?.key ?? '').trim()
  if (!sessions.length) return fail(reply, 'VALIDATION', '수업 데이터가 없습니다.')
  if (!key) return fail(reply, 'VALIDATION', '시간표를 만들 대상(학급/교사/교실)을 선택하세요.')
  return { svg: buildSvg(sessions, view, key), view, key }
})

const html = readFileSync(join(__dirname, 'index.html'), 'utf-8')
const ogImg = readFileSync(join(__dirname, 'og.png'))
app.get('/', async (_req, reply) => {
  reply.type('text/html; charset=utf-8').send(html)
})
app.get('/og.png', async (_req, reply) => {
  reply.type('image/png').send(ogImg)
})

app.listen({ port: PORT, host: '0.0.0.0' }).then(() => {
  console.log(`timetable-checker listening on :${PORT}`)
})
