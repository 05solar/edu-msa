package com.edu.msa.program;

import com.edu.msa.common.ProgramStatus;
import com.edu.msa.common.Scope;
import com.edu.msa.program.domain.Program;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * 카탈로그 목록의 필터/검색을 전부 DB 쿼리로 내리는 Specification 모음.
 * 컬렉션 조건(purposes/tech/tags)은 EXISTS 상관 서브쿼리로 처리해
 * 조인으로 인한 행 중복(카테시안 곱)과 페이지 수 왜곡이 없다.
 */
public final class ProgramSpecs {

    private ProgramSpecs() {}

    public static Specification<Program> hasStatus(ProgramStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** cat 미지정·"all" 이면 필터 없음. */
    public static Specification<Program> hasCat(String cat) {
        if (cat == null || cat.isBlank() || "all".equals(cat)) return null;
        return (root, query, cb) -> cb.equal(root.get("cat"), cat);
    }

    /** scope 미지정·"any" 이면 필터 없음. 코드("all"/"dept")로 받는다. */
    public static Specification<Program> hasScope(String scope) {
        if (scope == null || scope.isBlank() || "any".equals(scope)) return null;
        Scope s = Scope.from(scope);
        return (root, query, cb) -> cb.equal(root.get("scope"), s);
    }

    /** 지정한 값을 컬렉션이 전부 포함(containsAll)해야 한다 — 값마다 EXISTS 서브쿼리 AND. */
    public static Specification<Program> containsAll(String collection, List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return (root, query, cb) -> cb.and(values.stream()
                .map(v -> existsElement(root, query, cb, collection, v))
                .toArray(Predicate[]::new));
    }

    /** name/summary/description/tags/tech 대소문자 무시 부분 일치 검색(기존 자바 검색과 동일 의미). */
    public static Specification<Program> matchesQuery(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("summary")), like),
                cb.like(cb.lower(root.get("description")), like),
                existsElementLike(root, query, cb, "tags", like),
                existsElementLike(root, query, cb, "tech", like));
    }

    private static Predicate existsElement(Root<Program> root, CommonAbstractCriteria query,
                                           CriteriaBuilder cb, String collection, String value) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<Program> sp = sq.from(Program.class);
        Join<Program, String> el = sp.join(collection);
        sq.select(cb.literal(1L))
                .where(cb.equal(sp.get("id"), root.get("id")), cb.equal(el, value));
        return cb.exists(sq);
    }

    private static Predicate existsElementLike(Root<Program> root, CommonAbstractCriteria query,
                                               CriteriaBuilder cb, String collection, String like) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<Program> sp = sq.from(Program.class);
        Join<Program, String> el = sp.join(collection);
        sq.select(cb.literal(1L))
                .where(cb.equal(sp.get("id"), root.get("id")), cb.like(cb.lower(el), like));
        return cb.exists(sq);
    }
}
