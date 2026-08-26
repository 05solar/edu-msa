package main

import (
	_ "embed"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"regexp"
	"strings"
	"time"
)

//go:embed og.png
var ogPNG []byte

// ---- 규칙 정의 (오프라인·규칙기반) ----

type corr struct{ W, R, Rule string }

// 자주 틀리는 맞춤법·행정 오탈자 (명백히 교정 가능한 것만 수록). 어간(부분일치)도 포함해
// 활용형까지 잡는다. 위에서 아래 순서로 부분 문자열 치환한다.
var corrections = []corr{
	// --- 인사·흔한 오탈자 ---
	{"안여하세요", "안녕하세요", "맞춤법"}, {"안녕하십니가", "안녕하십니까", "맞춤법"},
	{"않녕하세요", "안녕하세요", "맞춤법"}, {"반갑읍니다", "반갑습니다", "맞춤법"},
	// --- 자주 틀리는 어휘 ---
	{"몇일", "며칠", "맞춤법"}, {"오랫만", "오랜만", "맞춤법"}, {"오랜동안", "오랫동안", "맞춤법"},
	{"금새", "금세", "맞춤법"}, {"역활", "역할", "맞춤법"}, {"희안", "희한", "맞춤법"},
	{"웬지", "왠지", "맞춤법"}, {"왠일", "웬일", "맞춤법"}, {"왠만", "웬만", "맞춤법"},
	{"곰곰히", "곰곰이", "맞춤법"}, {"일일히", "일일이", "맞춤법"}, {"깨끗히", "깨끗이", "맞춤법"},
	{"꼼꼼이", "꼼꼼히", "맞춤법"}, {"도데체", "도대체", "맞춤법"}, {"어의없", "어이없", "맞춤법"},
	{"할려고", "하려고", "맞춤법"}, {"갈려고", "가려고", "맞춤법"}, {"그럴려고", "그러려고", "맞춤법"},
	{"될려고", "되려고", "맞춤법"}, {"됬", "됐", "맞춤법"}, {"됫", "됐", "맞춤법"},
	{"뵈요", "봬요", "맞춤법"}, {"어떻해", "어떡해", "맞춤법"}, {"어떡게", "어떻게", "맞춤법"},
	{"설레임", "설렘", "맞춤법"}, {"연애인", "연예인", "맞춤법"}, {"문안하", "무난하", "맞춤법"},
	{"재데로", "제대로", "맞춤법"}, {"제대로하", "제대로 하", "띄어쓰기"},
	{"일우어", "이루어", "맞춤법"}, {"이루워", "이루어", "맞춤법"},
	{"승락", "승낙", "맞춤법"}, {"폭팔", "폭발", "맞춤법"}, {"갯수", "개수", "맞춤법"},
	{"촛점", "초점", "맞춤법"}, {"요컨데", "요컨대", "맞춤법"}, {"구지", "굳이", "맞춤법"},
	{"서슴치", "서슴지", "맞춤법"}, {"삼가해", "삼가", "맞춤법"}, {"눈쌀", "눈살", "맞춤법"},
	{"눈꼽", "눈곱", "맞춤법"}, {"부시시", "부스스", "맞춤법"}, {"되물림", "대물림", "맞춤법"},
	{"아니예요", "아니에요", "맞춤법"}, {"가르켜", "가르쳐", "맞춤법"},
	// --- '읍니다' → '습니다' ---
	{"읍니다", "습니다", "맞춤법"}, {"있읍니다", "있습니다", "맞춤법"}, {"없읍니다", "없습니다", "맞춤법"},
	{"했읍니다", "했습니다", "맞춤법"},
	// --- 'ㄹ께' → 'ㄹ게' ---
	{"할께", "할게", "맞춤법"}, {"갈께", "갈게", "맞춤법"}, {"줄께", "줄게", "맞춤법"},
	{"올께", "올게", "맞춤법"}, {"살께", "살게", "맞춤법"}, {"볼께", "볼게", "맞춤법"},
	// --- 율/률 ---
	{"확율", "확률", "맞춤법"}, {"출석율", "출석률", "맞춤법"}, {"합격율", "합격률", "맞춤법"},
	{"참석율", "참석률", "맞춤법"}, {"성공율", "성공률", "맞춤법"},
	// --- 오탈자(중복) ---
	{"및및", "및", "오탈자"}, {"그리고그리고", "그리고", "오탈자"},
	// --- 띄어쓰기: 의존명사 '중' ---
	{"재학중", "재학 중", "띄어쓰기"}, {"근무중", "근무 중", "띄어쓰기"}, {"회의중", "회의 중", "띄어쓰기"},
	{"출장중", "출장 중", "띄어쓰기"}, {"진행중", "진행 중", "띄어쓰기"}, {"사용중", "사용 중", "띄어쓰기"},
	{"검토중", "검토 중", "띄어쓰기"}, {"처리중", "처리 중", "띄어쓰기"},
	// --- 띄어쓰기: 'X바랍니다/바람' ---
	{"제출바랍니다", "제출 바랍니다", "띄어쓰기"}, {"협조바랍니다", "협조 바랍니다", "띄어쓰기"},
	{"참고바랍니다", "참고 바랍니다", "띄어쓰기"}, {"확인바랍니다", "확인 바랍니다", "띄어쓰기"},
	{"신청바랍니다", "신청 바랍니다", "띄어쓰기"}, {"회신바랍니다", "회신 바랍니다", "띄어쓰기"},
	{"조치바랍니다", "조치 바랍니다", "띄어쓰기"}, {"검토바랍니다", "검토 바랍니다", "띄어쓰기"},
	{"제출바람", "제출 바람", "띄어쓰기"}, {"협조바람", "협조 바람", "띄어쓰기"},
	// --- 띄어쓰기: '수 있/없' ---
	{"할수있", "할 수 있", "띄어쓰기"}, {"할수없", "할 수 없", "띄어쓰기"},
	{"볼수있", "볼 수 있", "띄어쓰기"}, {"볼수없", "볼 수 없", "띄어쓰기"},
	{"될수있", "될 수 있", "띄어쓰기"}, {"될수없", "될 수 없", "띄어쓰기"},
	{"갈수있", "갈 수 있", "띄어쓰기"}, {"안되겠", "안 되겠", "띄어쓰기"},
	// --- 띄어쓰기: '지 않' ---
	{"지않", "지 않", "띄어쓰기"},
}

// 문맥 확인이 필요한 표현 (자동교정하지 않고 표시만). Rule 로 유형 구분.
type homo struct{ Word, Note, Rule string }

var homonyms = []homo{
	{"결제", "결재(승인)/결제(대금 지불) 문맥 확인", "동음이의"},
	{"지양", "지양(하지 않음)/지향(추구) 문맥 확인", "동음이의"},
	{"갱신", "갱신(고쳐 새로)/경신(기록 깸) 문맥 확인", "동음이의"},
	{"제고", "제고(끌어올림)/재고(다시 고려/재고품) 문맥 확인", "동음이의"},
	{"계발", "계발(능력)/개발(새로 만듦) 문맥 확인", "동음이의"},
	{"가르키", "가리키다(지시)/가르치다(교육) 중 문맥에 맞게 확인", "확인"},
	{"바램", "바람(희망)/바램(색이 바램) 문맥 확인", "확인"},
	{"로써", "로서(자격·지위)/로써(수단·도구) 문맥 확인", "확인"},
}

type issue struct {
	Original   string `json:"original"`
	Suggestion string `json:"suggestion"`
	Rule       string `json:"rule"`
	Kind       string `json:"kind"` // correction | review
	Count      int    `json:"count"`
	Context    string `json:"context"`
}

var (
	reMultiSpace  = regexp.MustCompile(`[ \t]{2,}`)
	reSpaceBefore = regexp.MustCompile(` +([,.!?;:)\]}])`)
	reTrailing    = regexp.MustCompile(`[ \t]+\n`)
)

// 첫 등장 위치 주변 문맥 (해당 표현을 《》로 표시)
func contextOf(text, sub string) string {
	rs := []rune(text)
	ss := []rune(sub)
	idx := runeIndex(rs, ss)
	if idx < 0 {
		return ""
	}
	from := idx - 14
	if from < 0 {
		from = 0
	}
	to := idx + len(ss) + 14
	if to > len(rs) {
		to = len(rs)
	}
	pre := strings.ReplaceAll(string(rs[from:idx]), "\n", " ")
	mid := string(rs[idx : idx+len(ss)])
	post := strings.ReplaceAll(string(rs[idx+len(ss):to]), "\n", " ")
	lead, tail := "", ""
	if from > 0 {
		lead = "…"
	}
	if to < len(rs) {
		tail = "…"
	}
	return lead + pre + "《" + mid + "》" + post + tail
}

func runeIndex(hay, needle []rune) int {
	if len(needle) == 0 || len(needle) > len(hay) {
		return -1
	}
	for i := 0; i+len(needle) <= len(hay); i++ {
		match := true
		for j := range needle {
			if hay[i+j] != needle[j] {
				match = false
				break
			}
		}
		if match {
			return i
		}
	}
	return -1
}

func runCheck(text string) (map[string]interface{}, error) {
	issues := []issue{}
	c := text

	for _, cr := range corrections {
		if cr.W == cr.R {
			continue
		}
		if n := strings.Count(c, cr.W); n > 0 {
			issues = append(issues, issue{cr.W, cr.R, cr.Rule, "correction", n, contextOf(c, cr.W)})
			c = strings.ReplaceAll(c, cr.W, cr.R)
		}
	}

	// 공백 규칙
	if n := len(reMultiSpace.FindAllString(c, -1)); n > 0 {
		ex := reMultiSpace.FindString(c)
		issues = append(issues, issue{fmt.Sprintf("연속 공백 %d칸", len(ex)), "공백 1칸", "공백", "correction", n, ""})
		c = reMultiSpace.ReplaceAllString(c, " ")
	}
	if n := len(reSpaceBefore.FindAllString(c, -1)); n > 0 {
		issues = append(issues, issue{"문장부호 앞 공백", "공백 제거", "공백", "correction", n, ""})
		c = reSpaceBefore.ReplaceAllString(c, "$1")
	}
	if n := len(reTrailing.FindAllString(c, -1)); n > 0 {
		issues = append(issues, issue{"줄 끝 공백", "공백 제거", "공백", "correction", n, ""})
		c = reTrailing.ReplaceAllString(c, "\n")
	}

	// 문맥 확인(자동교정 없음)
	for _, h := range homonyms {
		if n := strings.Count(c, h.Word); n > 0 {
			issues = append(issues, issue{h.Word, h.Note, h.Rule, "review", n, contextOf(c, h.Word)})
		}
	}

	corrCount := 0
	for _, is := range issues {
		if is.Kind == "correction" {
			corrCount += is.Count
		}
	}

	return map[string]interface{}{
		"issues":    issues,
		"corrected": c,
		"stats": map[string]interface{}{
			"chars":       len([]rune(text)),
			"issueGroups": len(issues),
			"corrections": corrCount,
			"changed":     c != text,
		},
	}, nil
}

// ---- HTTP ----

func writeJSON(w http.ResponseWriter, code int, v interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(v)
}

func apiError(w http.ResponseWriter, code int, ecode, msg string) {
	writeJSON(w, code, map[string]interface{}{"error": map[string]string{"code": ecode, "message": msg}})
}

func checkHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		apiError(w, 405, "METHOD", "POST 만 허용합니다.")
		return
	}
	var body struct {
		Text string `json:"text"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		apiError(w, 400, "INVALID_JSON", "요청 본문을 해석할 수 없습니다.")
		return
	}
	if strings.TrimSpace(body.Text) == "" {
		apiError(w, 400, "VALIDATION", "검사할 텍스트를 입력하세요.")
		return
	}
	if len([]rune(body.Text)) > 50000 {
		apiError(w, 400, "VALIDATION", "한 번에 5만 자까지 검사할 수 있습니다.")
		return
	}
	res, _ := runCheck(body.Text)
	writeJSON(w, 200, res)
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]string{"status": "ok", "service": "doc-proofreader", "time": time.Now().Format(time.RFC3339)})
	})
	mux.HandleFunc("/api/check", checkHandler)
	mux.HandleFunc("/og.png", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "image/png")
		w.Write(ogPNG)
	})
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Write([]byte(indexHTML))
	})
	log.Printf("doc-proofreader listening on :%s", port)
	log.Fatal(http.ListenAndServe(":"+port, mux))
}
