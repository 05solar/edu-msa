// doc-approval · 공문/업무요청 결재 워크플로 서비스 (Go, net/http)
// 기안 → 상신 → (결재선) 검토/승인 → 승인/반려, 감사이력·통계 제공.
package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

// ---- 도메인 모델 ----

type StepStatus string
type DocStatus string

const (
	StepPending  StepStatus = "PENDING"
	StepApproved StepStatus = "APPROVED"
	StepRejected StepStatus = "REJECTED"
	StepSkipped  StepStatus = "SKIPPED"

	StatusDraft    DocStatus = "DRAFT"
	StatusInReview DocStatus = "IN_REVIEW"
	StatusApproved  DocStatus = "APPROVED"
	StatusRejected  DocStatus = "REJECTED"
	StatusWithdrawn DocStatus = "WITHDRAWN"
)

// 결재 단계(결재선 한 칸)
type Step struct {
	Order    int        `json:"order"`
	Approver string     `json:"approver"` // 결재자 이름
	Role     string     `json:"role"`     // 검토 | 승인 | 전결
	Status   StepStatus `json:"status"`
	Comment  string     `json:"comment,omitempty"`
	DecidedAt string    `json:"decidedAt,omitempty"`
}

type Audit struct {
	At    string `json:"at"`
	Actor string `json:"actor"`
	Act   string `json:"act"`
	Memo  string `json:"memo,omitempty"`
}

type Document struct {
	ID          int       `json:"id"`
	Title       string    `json:"title"`
	DocType     string    `json:"docType"` // 공문 | 업무요청 | 품의
	Drafter     string    `json:"drafter"`
	Department  string    `json:"department"`
	Content     string    `json:"content"`
	Priority    string    `json:"priority"` // NORMAL | URGENT
	Status      DocStatus `json:"status"`
	Line        []*Step   `json:"line"`        // 결재선
	CurrentStep int       `json:"currentStep"` // 진행 중 단계 order (0=미상신)
	DueDate     string    `json:"dueDate,omitempty"`
	CreatedAt   string    `json:"createdAt"`
	UpdatedAt   string    `json:"updatedAt"`
	Audit       []Audit   `json:"audit"`
}

// ---- 저장소(인메모리, 스레드 안전) ----

type Store struct {
	mu   sync.Mutex
	seq  int
	docs map[int]*Document
	file string // DATA_FILE 지정 시 파일 영속화(볼륨 마운트 시 재시작에도 보존)
}

var kst = time.FixedZone("KST", 9*3600)

func now() string { return time.Now().In(kst).Format("2006-01-02 15:04:05") }

func NewStore() *Store {
	s := &Store{docs: map[int]*Document{}, file: os.Getenv("DATA_FILE")}
	if s.file != "" {
		if _, err := os.Stat(s.file); err == nil { // 파일이 이미 있으면 로드 시도
			if s.load() {
				log.Printf("loaded %d docs from %s", len(s.docs), s.file)
			} else {
				log.Printf("경고: %s 로드 실패(손상 가능) — 덮어쓰지 않고 인메모리 시드로 시작", s.file)
				s.seed()
			}
			return s
		}
	}
	s.seed()
	s.save()
	return s
}

type snapshot struct {
	Seq  int         `json:"seq"`
	Docs []*Document `json:"docs"`
}

func (s *Store) load() bool {
	b, err := os.ReadFile(s.file)
	if err != nil {
		return false
	}
	var snap snapshot
	if json.Unmarshal(b, &snap) != nil {
		return false
	}
	s.seq = snap.Seq
	for _, d := range snap.Docs {
		if d.Line == nil {
			d.Line = []*Step{}
		}
		s.docs[d.ID] = d
	}
	return len(snap.Docs) > 0
}

// save 는 호출자가 s.mu 를 보유한 상태에서 호출한다(또는 초기화 시).
func (s *Store) save() {
	if s.file == "" {
		return
	}
	snap := snapshot{Seq: s.seq}
	for _, d := range s.docs {
		snap.Docs = append(snap.Docs, d)
	}
	b, _ := json.MarshalIndent(snap, "", " ")
	tmp := s.file + ".tmp"
	if err := os.WriteFile(tmp, b, 0o644); err != nil {
		log.Printf("save failed: %v (인메모리로 계속)", err)
		return
	}
	_ = os.Rename(tmp, s.file)
}

func (s *Store) nextID() int { s.seq++; return s.seq }

func (s *Store) seed() {
	// 실제 업무를 가정한 샘플 데이터
	d1 := &Document{ID: s.nextID(), Title: "2026학년도 1학기 방과후학교 운영 계획", DocType: "공문",
		Drafter: "김도현", Department: "교육지원과", Content: "방과후학교 프로그램 운영 및 강사 위촉 계획을 붙임과 같이 제출합니다.",
		Priority: "NORMAL", Status: StatusInReview, CurrentStep: 1, DueDate: "2026-09-05",
		CreatedAt: "2026-08-20 09:12:00", UpdatedAt: "2026-08-21 10:00:00",
		Line: []*Step{
			{Order: 1, Approver: "박서준", Role: "검토", Status: StepPending},
			{Order: 2, Approver: "정우성", Role: "승인", Status: StepPending},
		},
		Audit: []Audit{{At: "2026-08-20 09:12:00", Actor: "김도현", Act: "기안"}, {At: "2026-08-21 10:00:00", Actor: "김도현", Act: "상신"}},
	}
	d2 := &Document{ID: s.nextID(), Title: "노후 냉난방기 교체 예산 품의", DocType: "품의",
		Drafter: "윤민아", Department: "행정지원과", Content: "본관 3층 냉난방기 5대 교체 비용 8,400천원 집행을 품의합니다.",
		Priority: "URGENT", Status: StatusApproved, CurrentStep: 2, DueDate: "2026-08-25",
		CreatedAt: "2026-08-10 14:00:00", UpdatedAt: "2026-08-12 16:30:00",
		Line: []*Step{
			{Order: 1, Approver: "박서준", Role: "검토", Status: StepApproved, Comment: "견적 3곳 확인함", DecidedAt: "2026-08-11 09:00:00"},
			{Order: 2, Approver: "정우성", Role: "승인", Status: StepApproved, Comment: "긴급 승인", DecidedAt: "2026-08-12 16:30:00"},
		},
		Audit: []Audit{{At: "2026-08-10 14:00:00", Actor: "윤민아", Act: "기안"}, {At: "2026-08-10 15:00:00", Actor: "윤민아", Act: "상신"},
			{At: "2026-08-11 09:00:00", Actor: "박서준", Act: "검토승인"}, {At: "2026-08-12 16:30:00", Actor: "정우성", Act: "최종승인"}},
	}
	d3 := &Document{ID: s.nextID(), Title: "교직원 워크숍 장소 대관 업무요청", DocType: "업무요청",
		Drafter: "이준호", Department: "기획예산과", Content: "9월 교직원 워크숍 장소 대관 및 차량 배차를 요청합니다.",
		Priority: "NORMAL", Status: StatusDraft, CurrentStep: 0,
		CreatedAt: "2026-08-22 11:00:00", UpdatedAt: "2026-08-22 11:00:00",
		Line:  []*Step{},
		Audit: []Audit{{At: "2026-08-22 11:00:00", Actor: "이준호", Act: "기안"}},
	}
	for _, d := range []*Document{d1, d2, d3} {
		s.docs[d.ID] = d
	}
}

// ---- 오류 응답(통일 형식) ----

type apiErr struct {
	Error struct {
		Code    string `json:"code"`
		Message string `json:"message"`
	} `json:"error"`
}

func writeErr(w http.ResponseWriter, status int, code, msg string) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	var e apiErr
	e.Error.Code = code
	e.Error.Message = msg
	_ = json.NewEncoder(w).Encode(e)
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func actor(r *http.Request) string {
	if a := strings.TrimSpace(r.Header.Get("X-Actor")); a != "" {
		return a
	}
	return "system"
}

// 처리자(actor)는 JSON 본문 우선(한글 UTF-8 안전), 없으면 X-Actor 헤더.
func pickActor(bodyActor string, r *http.Request) string {
	if a := strings.TrimSpace(bodyActor); a != "" {
		return a
	}
	return actor(r)
}

// ---- 핸들러 ----

type Server struct{ store *Store }

func (s *Server) health(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, 200, map[string]string{"status": "ok", "service": "doc-approval", "time": now()})
}

func (s *Server) list(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	status := strings.ToUpper(strings.TrimSpace(q.Get("status")))
	kw := strings.TrimSpace(q.Get("q"))
	page, _ := strconv.Atoi(q.Get("page"))
	if page < 1 {
		page = 1
	}
	size, _ := strconv.Atoi(q.Get("size"))
	if size < 1 || size > 100 {
		size = 10
	}
	s.store.mu.Lock()
	defer s.store.mu.Unlock() // 인코딩까지 잠금 유지(공유 포인터 레이스 방지)
	all := []*Document{}       // 빈 목록도 [] 로 직렬화
	for _, d := range s.store.docs {
		if status != "" && string(d.Status) != status {
			continue
		}
		if kw != "" && !strings.Contains(d.Title, kw) && !strings.Contains(d.Content, kw) && !strings.Contains(d.Drafter, kw) {
			continue
		}
		all = append(all, d)
	}
	sort.Slice(all, func(i, j int) bool { return all[i].ID > all[j].ID })
	total := len(all)
	start := (page - 1) * size
	if start > total {
		start = total
	}
	end := start + size
	if end > total {
		end = total
	}
	writeJSON(w, 200, map[string]any{
		"page": page, "size": size, "total": total, "items": all[start:end],
	})
}

type createReq struct {
	Title      string   `json:"title"`
	DocType    string   `json:"docType"`
	Drafter    string   `json:"drafter"`
	Department string   `json:"department"`
	Content    string   `json:"content"`
	Priority   string   `json:"priority"`
	DueDate    string   `json:"dueDate"`
	Approvers  []string `json:"approvers"` // 결재선(기본: 마지막=승인, 나머지=검토)
	Roles      []string `json:"roles"`     // 선택: 각 결재자 역할(검토/승인/전결)
}

var validDocType = map[string]bool{"공문": true, "업무요청": true, "품의": true}
var validRole = map[string]bool{"검토": true, "승인": true, "전결": true}

// 결재선 구성(역할 지정 시 검증, 미지정 시 마지막=승인/나머지=검토).
func buildLine(approvers, roles []string) ([]*Step, error) {
	var line []*Step
	for i, ap := range approvers {
		ap = strings.TrimSpace(ap)
		if ap == "" {
			continue
		}
		role := "검토"
		if i < len(roles) && strings.TrimSpace(roles[i]) != "" {
			role = strings.TrimSpace(roles[i])
			if !validRole[role] {
				return nil, fmt.Errorf("결재 역할은 검토/승인/전결 중 하나여야 합니다: %s", role)
			}
		} else if i == len(approvers)-1 {
			role = "승인"
		}
		line = append(line, &Step{Order: len(line) + 1, Approver: ap, Role: role, Status: StepPending})
	}
	return line, nil
}

func (s *Server) create(w http.ResponseWriter, r *http.Request) {
	var req createReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeErr(w, 400, "INVALID_JSON", "요청 본문을 해석할 수 없습니다.")
		return
	}
	req.Title = strings.TrimSpace(req.Title)
	req.Drafter = strings.TrimSpace(req.Drafter)
	if req.Title == "" || req.Drafter == "" {
		writeErr(w, 400, "VALIDATION", "title과 drafter는 필수입니다.")
		return
	}
	if len([]rune(req.Title)) > 120 {
		writeErr(w, 400, "VALIDATION", "title은 120자 이하여야 합니다.")
		return
	}
	if req.DocType == "" {
		req.DocType = "공문"
	}
	if !validDocType[req.DocType] {
		writeErr(w, 400, "VALIDATION", "docType은 공문/업무요청/품의 중 하나여야 합니다.")
		return
	}
	if req.Priority != "URGENT" {
		req.Priority = "NORMAL"
	}
	line, lerr := buildLine(req.Approvers, req.Roles)
	if lerr != nil {
		writeErr(w, 400, "VALIDATION", lerr.Error())
		return
	}
	s.store.mu.Lock()
	defer s.store.mu.Unlock()
	d := &Document{
		ID: s.store.nextID(), Title: req.Title, DocType: req.DocType, Drafter: req.Drafter,
		Department: strings.TrimSpace(req.Department), Content: strings.TrimSpace(req.Content),
		Priority: req.Priority, Status: StatusDraft, Line: line, CurrentStep: 0,
		DueDate: strings.TrimSpace(req.DueDate), CreatedAt: now(), UpdatedAt: now(),
		Audit: []Audit{{At: now(), Actor: req.Drafter, Act: "기안"}},
	}
	if d.Line == nil {
		d.Line = []*Step{}
	}
	s.store.docs[d.ID] = d
	s.store.save()
	log.Printf("created doc id=%d by=%s", d.ID, req.Drafter)
	writeJSON(w, 201, d)
}

func (s *Server) getID(w http.ResponseWriter, r *http.Request) (*Document, bool) {
	id, err := strconv.Atoi(r.PathValue("id"))
	if err != nil {
		writeErr(w, 400, "VALIDATION", "id가 올바르지 않습니다.")
		return nil, false
	}
	d, ok := s.store.docs[id]
	if !ok {
		writeErr(w, 404, "NOT_FOUND", fmt.Sprintf("문서 %d 를 찾을 수 없습니다.", id))
		return nil, false
	}
	return d, true
}

func (s *Server) get(w http.ResponseWriter, r *http.Request) {
	s.store.mu.Lock()
	defer s.store.mu.Unlock()
	if d, ok := s.getID(w, r); ok {
		writeJSON(w, 200, d)
	}
}

func (s *Server) audit(w http.ResponseWriter, r *http.Request) {
	s.store.mu.Lock()
	defer s.store.mu.Unlock()
	if d, ok := s.getID(w, r); ok {
		writeJSON(w, 200, map[string]any{"id": d.ID, "audit": d.Audit})
	}
}

func (s *Server) submit(w http.ResponseWriter, r *http.Request) {
	s.store.mu.Lock()
	defer s.store.mu.Unlock()
	d, ok := s.getID(w, r)
	if !ok {
		return
	}
	if d.Status != StatusDraft {
		writeErr(w, 409, "INVALID_STATE", "기안(DRAFT) 상태의 문서만 상신할 수 있습니다.")
		return
	}
	if len(d.Line) == 0 {
		writeErr(w, 409, "NO_APPROVAL_LINE", "결재선(approvers)이 지정되지 않아 상신할 수 없습니다.")
		return
	}
	var sb struct {
		Actor string `json:"actor"`
	}
	_ = json.NewDecoder(r.Body).Decode(&sb)
	act := pickActor(sb.Actor, r)
	d.Status = StatusInReview
	d.CurrentStep = 1
	d.UpdatedAt = now()
	d.Audit = append(d.Audit, Audit{At: now(), Actor: act, Act: "상신"})
	s.store.save()
	log.Printf("submit doc id=%d by=%s", d.ID, act)
	writeJSON(w, 200, d)
}

type decisionReq struct {
	Actor   string `json:"actor"`
	Comment string `json:"comment"`
}

func (s *Server) currentStep(d *Document) *Step {
	for _, st := range d.Line {
		if st.Order == d.CurrentStep {
			return st
		}
	}
	return nil
}

func (s *Server) approve(w http.ResponseWriter, r *http.Request) {
	s.store.mu.Lock()
	defer s.store.mu.Unlock()
	d, ok := s.getID(w, r)
	if !ok {
		return
	}
	if d.Status != StatusInReview {
		writeErr(w, 409, "INVALID_STATE", "결재 진행 중인 문서만 승인할 수 있습니다.")
		return
	}
	step := s.currentStep(d)
	if step == nil {
		writeErr(w, 409, "INVALID_STATE", "진행 중인 결재 단계가 없습니다.")
		return
	}
	var req decisionReq
	_ = json.NewDecoder(r.Body).Decode(&req)
	act := pickActor(req.Actor, r)
	if act != step.Approver {
		writeErr(w, 403, "NOT_APPROVER", fmt.Sprintf("현재 결재자(%s)만 처리할 수 있습니다. (요청자: %s)", step.Approver, act))
		return
	}
	step.Status = StepApproved
	step.Comment = strings.TrimSpace(req.Comment)
	step.DecidedAt = now()
	d.Audit = append(d.Audit, Audit{At: now(), Actor: act, Act: step.Role + "승인", Memo: step.Comment})
	// 전결이면 잔여 단계를 건너뛰고 최종 승인, 아니면 다음 단계 진행/최종 승인
	if step.Role == "전결" {
		for _, st := range d.Line {
			if st.Status == StepPending {
				st.Status = StepSkipped
			}
		}
		d.Status = StatusApproved
	} else if d.CurrentStep >= len(d.Line) {
		d.Status = StatusApproved
	} else {
		d.CurrentStep++
		d.Status = StatusInReview
	}
	d.UpdatedAt = now()
	s.store.save()
	log.Printf("approve doc id=%d step=%d by=%s -> %s", d.ID, step.Order, act, d.Status)
	writeJSON(w, 200, d)
}

func (s *Server) reject(w http.ResponseWriter, r *http.Request) {
	s.store.mu.Lock()
	defer s.store.mu.Unlock()
	d, ok := s.getID(w, r)
	if !ok {
		return
	}
	if d.Status != StatusInReview {
		writeErr(w, 409, "INVALID_STATE", "결재 진행 중인 문서만 반려할 수 있습니다.")
		return
	}
	step := s.currentStep(d)
	if step == nil {
		writeErr(w, 409, "INVALID_STATE", "진행 중인 결재 단계가 없습니다.")
		return
	}
	var req decisionReq
	_ = json.NewDecoder(r.Body).Decode(&req)
	act := pickActor(req.Actor, r)
	if act != step.Approver {
		writeErr(w, 403, "NOT_APPROVER", fmt.Sprintf("현재 결재자(%s)만 처리할 수 있습니다. (요청자: %s)", step.Approver, act))
		return
	}
	if strings.TrimSpace(req.Comment) == "" {
		writeErr(w, 400, "VALIDATION", "반려 사유(comment)는 필수입니다.")
		return
	}
	step.Status = StepRejected
	step.Comment = strings.TrimSpace(req.Comment)
	step.DecidedAt = now()
	d.Status = StatusRejected
	d.UpdatedAt = now()
	d.Audit = append(d.Audit, Audit{At: now(), Actor: act, Act: "반려", Memo: step.Comment})
	s.store.save()
	log.Printf("reject doc id=%d by=%s", d.ID, act)
	writeJSON(w, 200, d)
}

func (s *Server) withdraw(w http.ResponseWriter, r *http.Request) {
	s.store.mu.Lock()
	defer s.store.mu.Unlock()
	d, ok := s.getID(w, r)
	if !ok {
		return
	}
	if d.Status == StatusApproved || d.Status == StatusRejected {
		writeErr(w, 409, "INVALID_STATE", "이미 종결된 문서는 회수할 수 없습니다.")
		return
	}
	var wb struct {
		Actor string `json:"actor"`
	}
	_ = json.NewDecoder(r.Body).Decode(&wb)
	act := pickActor(wb.Actor, r)
	if act != d.Drafter {
		writeErr(w, 403, "NOT_DRAFTER", "기안자만 회수할 수 있습니다.")
		return
	}
	d.Status = StatusWithdrawn
	d.CurrentStep = 0
	d.UpdatedAt = now()
	d.Audit = append(d.Audit, Audit{At: now(), Actor: act, Act: "회수"})
	s.store.save()
	writeJSON(w, 200, d)
}

// 기안/반려/회수 상태의 문서를 기안자가 수정 → DRAFT로 되돌려 재상신 가능(막다른 상태 해소).
func (s *Server) edit(w http.ResponseWriter, r *http.Request) {
	s.store.mu.Lock()
	defer s.store.mu.Unlock()
	d, ok := s.getID(w, r)
	if !ok {
		return
	}
	if d.Status != StatusDraft && d.Status != StatusRejected && d.Status != StatusWithdrawn {
		writeErr(w, 409, "INVALID_STATE", "기안/반려/회수 상태의 문서만 수정할 수 있습니다.")
		return
	}
	var req createReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeErr(w, 400, "INVALID_JSON", "요청 본문을 해석할 수 없습니다.")
		return
	}
	act := actor(r)
	if drafter := strings.TrimSpace(req.Drafter); drafter != "" {
		act = drafter
	}
	if act != d.Drafter {
		writeErr(w, 403, "NOT_DRAFTER", "기안자만 수정할 수 있습니다.")
		return
	}
	if t := strings.TrimSpace(req.Title); t != "" {
		if len([]rune(t)) > 120 {
			writeErr(w, 400, "VALIDATION", "title은 120자 이하여야 합니다.")
			return
		}
		d.Title = t
	}
	if req.DocType != "" {
		if !validDocType[req.DocType] {
			writeErr(w, 400, "VALIDATION", "docType은 공문/업무요청/품의 중 하나여야 합니다.")
			return
		}
		d.DocType = req.DocType
	}
	if req.Department != "" {
		d.Department = strings.TrimSpace(req.Department)
	}
	if req.Content != "" {
		d.Content = strings.TrimSpace(req.Content)
	}
	if req.Priority != "" {
		if req.Priority == "URGENT" {
			d.Priority = "URGENT"
		} else {
			d.Priority = "NORMAL"
		}
	}
	if req.DueDate != "" {
		d.DueDate = strings.TrimSpace(req.DueDate)
	}
	if len(req.Approvers) > 0 {
		line, lerr := buildLine(req.Approvers, req.Roles)
		if lerr != nil {
			writeErr(w, 400, "VALIDATION", lerr.Error())
			return
		}
		d.Line = line
	} else {
		// 결재선 재사용: 이전 결재 상태 초기화
		for _, st := range d.Line {
			st.Status = StepPending
			st.Comment = ""
			st.DecidedAt = ""
		}
	}
	d.Status = StatusDraft
	d.CurrentStep = 0
	d.UpdatedAt = now()
	d.Audit = append(d.Audit, Audit{At: now(), Actor: act, Act: "수정"})
	s.store.save()
	writeJSON(w, 200, d)
}

func (s *Server) stats(w http.ResponseWriter, r *http.Request) {
	s.store.mu.Lock()
	defer s.store.mu.Unlock()
	byStatus := map[string]int{}
	byDept := map[string]int{}
	urgent := 0
	for _, d := range s.store.docs {
		byStatus[string(d.Status)]++
		byDept[d.Department]++
		if d.Priority == "URGENT" {
			urgent++
		}
	}
	writeJSON(w, 200, map[string]any{
		"total": len(s.store.docs), "byStatus": byStatus, "byDepartment": byDept, "urgent": urgent,
	})
}

const indexHTML = `<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>공문/업무요청 결재</title>
<style>
:root{--line:#e2e8f0;--ink:#1e293b;--mut:#64748b;--blue:#2563eb;--bg:#f8fafc}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,'Malgun Gothic',sans-serif;color:var(--ink);background:var(--bg)}
header{background:#fff;border-bottom:1px solid var(--line);padding:16px 24px}header h1{font-size:20px;margin:0}header p{margin:4px 0 0;color:var(--mut);font-size:13px}
.wrap{max-width:1080px;margin:0 auto;padding:20px 24px}
.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:18px}
.card{background:#fff;border:1px solid var(--line);border-radius:12px;padding:14px}.card .lbl{font-size:12px;color:var(--mut)}.card .val{font-size:22px;font-weight:800;margin-top:4px}
.toolbar{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:12px}
input,select,button{font:inherit;padding:8px 10px;border:1px solid var(--line);border-radius:8px;background:#fff;color:var(--ink)}button{cursor:pointer}.btn-primary{background:var(--blue);color:#fff;border-color:var(--blue);font-weight:600}.btn-sm{padding:4px 8px;font-size:12px}
table{width:100%;border-collapse:collapse;background:#fff;border:1px solid var(--line);border-radius:12px;overflow:hidden;font-size:13px}
th,td{text-align:left;padding:10px 12px;border-bottom:1px solid var(--line)}th{background:#f1f5f9;color:var(--mut);font-size:11px;text-transform:uppercase}tr:last-child td{border-bottom:none}
.badge{display:inline-block;padding:2px 8px;border-radius:999px;font-size:11px;font-weight:700}
.s-DRAFT{background:#e2e8f0;color:#475569}.s-IN_REVIEW{background:#fef3c7;color:#92400e}.s-APPROVED{background:#dcfce7;color:#166534}.s-REJECTED{background:#fee2e2;color:#991b1b}.s-WITHDRAWN{background:#e0e7ff;color:#3730a3}
dialog{border:none;border-radius:14px;max-width:440px;width:92%;padding:0}form{padding:20px}form h3{margin:0 0 14px}.fld{margin-bottom:10px}.fld label{display:block;font-size:12px;color:var(--mut);margin-bottom:4px}.fld input{width:100%}
.modal-actions{display:flex;gap:8px;justify-content:flex-end;margin-top:16px}
</style></head><body>
<header><h1>공문/업무요청 결재</h1><p>기안 → 상신 → 결재선(검토/승인/전결) → 승인/반려</p></header>
<div class="wrap">
<div class="stats" id="stats"></div>
<div class="toolbar"><input id="q" placeholder="제목·기안자 검색" style="min-width:200px">
<select id="fstatus"><option value="">전체 상태</option></select><button onclick="load()">조회</button>
<span style="flex:1"></span><button class="btn-primary" onclick="reg.showModal()">+ 기안 작성</button></div>
<table><thead><tr><th>제목</th><th>유형</th><th>기안자</th><th>상태</th><th>현재 결재자</th><th>처리</th></tr></thead><tbody id="rows"></tbody></table>
</div>
<dialog id="reg"><form onsubmit="return submitReg(event)"><h3>기안 작성</h3>
<div class="fld"><label>제목 *</label><input id="r-title" required></div>
<div class="fld"><label>기안자 *</label><input id="r-drafter" required></div>
<div class="fld"><label>결재선(쉼표로 구분, 마지막=승인) *</label><input id="r-approvers" placeholder="박서준, 정우성" required></div>
<div class="modal-actions"><button type="button" onclick="reg.close()">취소</button><button class="btn-primary" type="submit">등록</button></div>
</form></dialog>
<script>
var STS={DRAFT:'기안',IN_REVIEW:'결재중',APPROVED:'승인',REJECTED:'반려',WITHDRAWN:'회수'};
function esc(s){s=s||'';return s.replace(/[&<>]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;'}[c];});}
function opt(sel,arr){for(var i=0;i<arr.length;i++){var o=document.createElement('option');o.value=arr[i];o.textContent=STS[arr[i]]||arr[i];sel.appendChild(o);}}
opt(document.getElementById('fstatus'),Object.keys(STS));
function jget(u){return fetch(u).then(function(r){return r.json();});}
function jpost(u,b){return fetch(u,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b||{})}).then(function(r){return r.json().then(function(d){return {ok:r.ok,d:d};});});}
function curApprover(d){if(!d.line)return '-';for(var i=0;i<d.line.length;i++){if(d.line[i].order===d.currentStep)return esc(d.line[i].approver)+' ('+d.line[i].role+')';}return '-';}
function acts(d){var b=function(t,f){return '<button class="btn-sm" onclick="'+f+'">'+t+'</button> ';};
 if(d.status==='DRAFT')return b('상신',"act('"+d.id+"','submit')");
 if(d.status==='IN_REVIEW')return b('승인',"decide("+d.id+",'approve')")+b('반려',"decide("+d.id+",'reject')");
 return '-';}
function load(){
 var q=new URLSearchParams();var qq=document.getElementById('q').value.trim();if(qq)q.set('q',qq);
 var st=document.getElementById('fstatus').value;if(st)q.set('status',st);q.set('size','100');
 jget('/api/documents?'+q).then(function(d){
  var rows=document.getElementById('rows');rows.innerHTML='';
  if(!d.items.length)rows.innerHTML='<tr><td colspan=6 style="text-align:center;color:#94a3b8;padding:30px">문서가 없습니다.</td></tr>';
  d.items.forEach(function(x){var tr=document.createElement('tr');
   tr.innerHTML='<td><b>'+esc(x.title)+'</b></td><td>'+x.docType+'</td><td>'+esc(x.drafter)+'</td>'+
    '<td><span class="badge s-'+x.status+'">'+STS[x.status]+'</span></td><td>'+curApprover(x)+'</td><td>'+acts(x)+'</td>';
   rows.appendChild(tr);});
 });
 jget('/api/stats').then(function(s){document.getElementById('stats').innerHTML=
  card('전체',s.total+'건')+card('결재중',(s.byStatus.IN_REVIEW||0)+'건')+card('승인',(s.byStatus.APPROVED||0)+'건')+card('긴급',s.urgent+'건');});
}
function card(l,v){return '<div class="card"><div class="lbl">'+l+'</div><div class="val">'+v+'</div></div>';}
function act(id,kind){var b={actor:'담당자'};jpost('/api/documents/'+id+'/'+kind,b).then(function(r){if(!r.ok)alert('오류: '+(r.d.error?r.d.error.message:''));load();});}
function decide(id,kind){var who=prompt('결재자 이름');if(!who)return;var b={actor:who};if(kind==='reject'){var c=prompt('반려 사유');if(!c)return;b.comment=c;}else{b.comment=prompt('의견(선택)')||'';}
 jpost('/api/documents/'+id+'/'+kind,b).then(function(r){if(!r.ok)alert('오류: '+(r.d.error?r.d.error.message:''));load();});}
function submitReg(e){e.preventDefault();
 var ap=document.getElementById('r-approvers').value.split(',').map(function(s){return s.trim();}).filter(Boolean);
 jpost('/api/documents',{title:document.getElementById('r-title').value,drafter:document.getElementById('r-drafter').value,approvers:ap}).then(function(r){
  if(!r.ok){alert('오류: '+(r.d.error?r.d.error.message:''));return;}reg.close();document.getElementById('r-title').value='';load();});
 return false;}
load();
</script></body></html>`

func (s *Server) index(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/" {
		writeErr(w, 404, "NOT_FOUND", "경로를 찾을 수 없습니다.")
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = w.Write([]byte(indexHTML))
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	srv := &Server{store: NewStore()}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", srv.health)
	mux.HandleFunc("GET /api/documents", srv.list)
	mux.HandleFunc("POST /api/documents", srv.create)
	mux.HandleFunc("PATCH /api/documents/{id}", srv.edit)
	mux.HandleFunc("GET /api/documents/{id}", srv.get)
	mux.HandleFunc("GET /api/documents/{id}/audit", srv.audit)
	mux.HandleFunc("POST /api/documents/{id}/submit", srv.submit)
	mux.HandleFunc("POST /api/documents/{id}/approve", srv.approve)
	mux.HandleFunc("POST /api/documents/{id}/reject", srv.reject)
	mux.HandleFunc("POST /api/documents/{id}/withdraw", srv.withdraw)
	mux.HandleFunc("GET /api/stats", srv.stats)
	mux.HandleFunc("GET /", srv.index)

	log.Printf("doc-approval listening on :%s", port)
	if err := http.ListenAndServe(":"+port, logMW(mux)); err != nil {
		log.Fatal(err)
	}
}

func logMW(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		next.ServeHTTP(w, r)
		log.Printf("%s %s %s", r.Method, r.URL.Path, time.Since(start))
	})
}
