package com.edu.msa.deploy;

import com.edu.msa.deploy.domain.ServiceSpec;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/** service.yaml 문자열을 ServiceSpec 으로 파싱한다. */
@Component
public class SpecParser {

    @SuppressWarnings("unchecked")
    public ServiceSpec parse(String yamlContent) {
        Object loaded = new Yaml().load(yamlContent);
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("service.yaml 형식이 올바르지 않습니다.");
        }
        Map<String, Object> m = (Map<String, Object>) map;
        Map<String, Object> res = asMap(m.get("resources"));
        return new ServiceSpec(
                str(m.get("name")),
                str(m.get("slug")),
                str(m.get("category")),
                strList(m.get("purposes")),
                strList(m.get("tech")),
                str(m.get("summary")),
                intOr(m.get("port"), 0),
                str(m.get("health")),
                res != null ? str(res.get("cpu")) : null,
                res != null ? str(res.get("memory")) : null
        );
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static int intOr(Object o, int fallback) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return fallback;
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (NumberFormatException e) { return fallback; }
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object o) {
        if (o instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}
