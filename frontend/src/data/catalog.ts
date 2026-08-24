import type {
  AiModel, AiSource, AppUser, Category, NavItem, Purpose, Role, RunType, Scope,
} from '../types'

/* 분류 체계 — 아이콘은 SVG 키(이모지 아님) */

/* (1) 업무 분야 */
export const CATEGORIES: Category[] = [
  { id: 'doc', name: '문서·공문', icon: 'doc', color: '#1D4ED8' },
  { id: 'student', name: '학생·성적', icon: 'student', color: '#B42318' },
  { id: 'curri', name: '교육과정', icon: 'curri', color: '#7A3E9D' },
  { id: 'budget', name: '예산·회계', icon: 'budget', color: '#9A6300' },
  { id: 'facil', name: '시설·안전', icon: 'facil', color: '#0B7A4B' },
  { id: 'data', name: '데이터', icon: 'data', color: '#1B3149' },
  { id: 'civil', name: '민원', icon: 'civil', color: '#5B6B7F' },
]
export const FIELD_LABEL = '업무 분야'

/* (2) 기능 유형 */
export const PURPOSES: Purpose[] = [
  { id: 'auto', name: '자동화', icon: 'auto' },
  { id: 'gen', name: '생성', icon: 'gen' },
  { id: 'verify', name: '검증', icon: 'verify' },
  { id: 'analyze', name: '분석', icon: 'analyze' },
  { id: 'summary', name: '요약', icon: 'summary' },
  { id: 'search', name: '검색', icon: 'search' },
  { id: 'dash', name: '대시보드', icon: 'dash' },
]
export const FUNC_LABEL = '기능 유형'

/* (3) 기술 태그 */
export const TECHS = ['Python', 'Excel', 'HWP', 'LLM', 'OCR', 'Streamlit', 'Gitea', '나이스', 'STT', 'Pandas', 'PPT', '크롤링', '모바일']

/* 제공 방식 */
export const RUN_TYPES: RunType[] = [
  { id: 'web', name: '웹에서 바로 사용', icon: 'web', desc: '설치 없이 브라우저에서 바로 사용합니다.' },
  { id: 'download', name: '파일 다운로드', icon: 'download', desc: '실행 파일·스크립트를 내려받아 업무용 PC에서 사용합니다.' },
  { id: 'installer', name: '설치 프로그램', icon: 'installer', desc: '설치 파일을 내려받아 업무용 PC에 설치합니다.' },
  { id: 'gitea', name: 'Gitea 저장소', icon: 'gitea', desc: '소스코드를 확인하거나 내려받습니다. (내부망 전용)' },
  { id: 'manual', name: '사용 매뉴얼', icon: 'manual', desc: '첨부된 매뉴얼·안내 문서를 확인합니다.' },
]
export const RUN_LABEL = '제공 방식'

/* 공개 범위 */
export const SCOPES: Record<Scope, string> = {
  all: '전체 공개 (교육청 전 직원)',
  dept: '부서 공개 (같은 부서 직원)',
}
export const SCOPE_SHORT: Record<Scope, string> = { all: '전체 공개', dept: '부서 공개' }

/* 역할 */
export const ROLE_LABEL: Record<Role, string> = {
  user: '일반 사용자', coder: '바이브 코더', admin: '운영 관리자',
}
export const ROLE_USER: Record<Role, AppUser> = {
  user: { name: '윤하늘', dept: '교육과정과', role: 'user' },
  coder: { name: '김도현', dept: '행정지원과', role: 'coder' },
  admin: { name: '정우성', dept: '정보화담당관', role: 'admin' },
}

/* 좌측 사이드바 메뉴 */
export const NAV: NavItem[] = [
  { view: 'home', label: '홈', icon: 'home', group: 'main', roles: ['user', 'coder', 'admin'], sub: '업무 프로그램 검색·바로가기' },
  { view: 'list', label: '프로그램 탐색', icon: 'list', group: 'main', roles: ['user', 'coder', 'admin'], sub: '업무 분야·기능 유형·기술로 찾기' },
  { view: 'ai', label: 'AI로 프로그램 찾기', icon: 'ai', group: 'main', roles: ['user', 'coder', 'admin'], sub: '업무를 설명하면 적합한 프로그램을 추천' },
  { view: 'register', label: '프로그램 등록', icon: 'register', group: 'main', roles: ['coder', 'admin'], sub: '등록 요청 후 운영 관리자 검토' },
  { view: 'my', label: '내 프로그램', icon: 'my', group: 'main', roles: ['user', 'coder', 'admin'], sub: '등록 현황·승인 상태·즐겨찾기·알림' },
  { view: 'admin', label: '운영 관리자', icon: 'admin', group: 'ops', roles: ['admin'], sub: '등록 검토·처리 이력·운영 현황·카테고리' },
]

/* 일반 사용자에게는 '내 프로그램' 대신 '마이페이지' */
export const NAV_ALT: Partial<Record<string, Partial<Record<Role, { label: string; sub: string }>>>> = {
  my: { user: { label: '마이페이지', sub: '즐겨찾기·알림 확인' } },
}

export const VIEW_TITLE: Partial<Record<string, { label: string; sub: string }>> = {
  detail: { label: '프로그램 상세', sub: '소개·사용 방법·업데이트 내역·의견' },
}

/* AI 참고 자료 / 모델 (내부망 전용) */
export const AI_SOURCES: AiSource[] = [
  { id: 'readme', name: 'README', desc: '설치·구성·요구사항' },
  { id: 'manual', name: '사용자 매뉴얼', desc: '화면 조작 순서·자주 발생하는 오류' },
  { id: 'code', name: '소스코드', desc: '실행 함수·옵션·오류 발생 지점' },
  { id: 'files', name: '첨부문서', desc: 'PDF·HWP 등 배포 자료' },
]
export const AI_MODELS: AiModel[] = [
  { id: 'code', name: '내부 코드 특화 모델', sub: '내부망 Ollama · 설치/실행/오류 안내에 적합' },
  { id: 'gen', name: '내부 일반 LLM', sub: '내부망 vLLM · 업무 설명과 요약에 적합' },
]
