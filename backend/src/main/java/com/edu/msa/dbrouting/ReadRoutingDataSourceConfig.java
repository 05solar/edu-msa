package com.edu.msa.dbrouting;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 읽기 전용 replica 라우팅 데이터소스 — DB_RO_URL(edu.datasource.read-url)이 설정된
 * 환경에서만 활성화된다(미설정 시 기존 단일 데이터소스 자동구성 그대로 — 로컬/테스트 무영향).
 *
 * - primary: 기존 spring.datasource(DB_URL → 풀러 rw)
 * - replica: edu.datasource.read-url (K8s: edu-db-pooler-ro → CNPG replica)
 * - 라우팅 키 결정은 "커넥션 최초 사용 시점"이어야 하므로 LazyConnectionDataSourceProxy 로
 *   감싼다(트랜잭션 시작 시점엔 readOnly 여부가 이미 확정되어 있다).
 * - Flyway/시더 등 트랜잭션 밖·쓰기 경로는 항상 primary(default).
 */
@Configuration
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${edu.datasource.read-url:}')")
public class ReadRoutingDataSourceConfig {

    static final String PRIMARY = "primary";
    static final String REPLICA = "replica";

    @Bean
    @Primary
    public DataSource dataSource(
            DataSourceProperties props,
            ObjectProvider<MeterRegistry> registry,
            @Value("${edu.datasource.read-url}") String readUrl,
            @Value("${spring.datasource.hikari.maximum-pool-size:20}") int primaryMaxPool,
            @Value("${edu.datasource.read-pool-max-size:10}") int replicaMaxPool,
            @Value("${spring.datasource.hikari.minimum-idle:5}") int minIdle,
            @Value("${spring.datasource.hikari.connection-timeout:5000}") long connTimeoutMs,
            @Value("${spring.datasource.hikari.max-lifetime:1800000}") long maxLifetimeMs) {

        HikariDataSource primary = pool("edu-primary", props.getUrl(), props, primaryMaxPool,
                minIdle, connTimeoutMs, maxLifetimeMs, registry);
        HikariDataSource replica = pool("edu-replica", readUrl, props, replicaMaxPool,
                minIdle, connTimeoutMs, maxLifetimeMs, registry);
        // replica 는 읽기 전용 세션 — 실수로 쓰기가 흘러도 DB 가 거부하게 한다
        replica.setReadOnly(true);

        AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                boolean readOnlyTx = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                return (readOnlyTx && ReadReplica.isMarked()) ? REPLICA : PRIMARY;
            }
        };
        routing.setTargetDataSources(Map.of(PRIMARY, primary, REPLICA, replica));
        routing.setDefaultTargetDataSource(primary);
        routing.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(routing);
    }

    private HikariDataSource pool(String name, String url, DataSourceProperties props,
                                  int maxPool, int minIdle, long connTimeoutMs, long maxLifetimeMs,
                                  ObjectProvider<MeterRegistry> registry) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(name);
        ds.setJdbcUrl(url);
        ds.setUsername(props.getUsername());
        ds.setPassword(props.getPassword());
        ds.setMaximumPoolSize(maxPool);
        ds.setMinimumIdle(minIdle);
        ds.setConnectionTimeout(connTimeoutMs);
        ds.setMaxLifetime(maxLifetimeMs);
        // 풀별 hikaricp_* 메트릭 유지(pool 태그로 primary/replica 구분)
        registry.ifAvailable(ds::setMetricRegistry);
        return ds;
    }
}
