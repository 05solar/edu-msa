package com.edu.msa.catalog;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 분류 체계(정적) 제공 — 프론트엔드 catalog와 동일한 토큰/아이콘 키. */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    public record Category(String id, String name, String icon, String color) {}
    public record Purpose(String id, String name, String icon) {}
    public record RunType(String id, String name, String icon, String desc) {}
    public record CatalogResponse(
            List<Category> categories, List<Purpose> purposes,
            List<RunType> runTypes, List<String> techs, Map<String, String> scopes) {}

    private static final List<Category> CATEGORIES = List.of(
            new Category("doc", "문서·공문", "doc", "#1D4ED8"),
            new Category("student", "학생·성적", "student", "#B42318"),
            new Category("curri", "교육과정", "curri", "#7A3E9D"),
            new Category("budget", "예산·회계", "budget", "#9A6300"),
            new Category("facil", "시설·안전", "facil", "#0B7A4B"),
            new Category("data", "데이터", "data", "#1B3149"),
            new Category("civil", "민원", "civil", "#5B6B7F"));

    private static final List<Purpose> PURPOSES = List.of(
            new Purpose("auto", "자동화", "auto"),
            new Purpose("gen", "생성", "gen"),
            new Purpose("verify", "검증", "verify"),
            new Purpose("analyze", "분석", "analyze"),
            new Purpose("summary", "요약", "summary"),
            new Purpose("search", "검색", "search"),
            new Purpose("dash", "대시보드", "dash"));

    private static final List<RunType> RUN_TYPES = List.of(
            new RunType("web", "웹에서 바로 사용", "web", "설치 없이 브라우저에서 바로 사용합니다."),
            new RunType("download", "파일 다운로드", "download", "실행 파일·스크립트를 내려받아 업무용 PC에서 사용합니다."),
            new RunType("installer", "설치 프로그램", "installer", "설치 파일을 내려받아 업무용 PC에 설치합니다."),
            new RunType("gitea", "Gitea 저장소", "gitea", "소스코드를 확인하거나 내려받습니다. (내부망 전용)"),
            new RunType("manual", "사용 매뉴얼", "manual", "첨부된 매뉴얼·안내 문서를 확인합니다."));

    private static final List<String> TECHS = List.of(
            "Python", "Excel", "HWP", "LLM", "OCR", "Streamlit", "Gitea", "나이스", "STT", "Pandas", "PPT", "크롤링", "모바일");

    private static final Map<String, String> SCOPES = Map.of(
            "all", "전체 공개 (교육청 전 직원)", "dept", "부서 공개 (같은 부서 직원)");

    @GetMapping
    public CatalogResponse catalog() {
        return new CatalogResponse(CATEGORIES, PURPOSES, RUN_TYPES, TECHS, SCOPES);
    }
}
