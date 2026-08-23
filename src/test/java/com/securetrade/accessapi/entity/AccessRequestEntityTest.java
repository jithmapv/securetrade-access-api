package com.securetrade.accessapi.entity;

import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccessRequestEntityTest {

    @Test
    void storesFieldsAndSetsCreateTime() {
        TradingAgentEntity agent = new TradingAgentEntity();
        AccessRequestEntity request = new AccessRequestEntity(
                agent,
                "AAPL",
                TradeType.BUY,
                new BigDecimal("5000.00"),
                new BigDecimal("0.25"),
                DecisionResult.APPROVED,
                "TEST_RULE",
                null);

        assertThat(request.getId()).isNull();
        assertThat(request.getAgent()).isSameAs(agent);
        assertThat(request.getSymbol()).isEqualTo("AAPL");
        assertThat(request.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(request.getRequestedVolume()).isEqualByComparingTo("5000.00");
        assertThat(request.getRiskScore()).isEqualByComparingTo("0.25");
        assertThat(request.getOutcome()).isEqualTo(DecisionResult.APPROVED);
        assertThat(request.getReasonCode()).isEqualTo("TEST_RULE");
        assertThat(request.getIdempotencyKey()).isNull();
        assertThat(request.getCreatedAt()).isNull();

        request.setCreateTime();

        assertThat(request.getCreatedAt()).isNotNull();
    }

    @Test
    void keepsExistingCreateTime() {
        Instant createTime = Instant.parse("2026-08-23T07:00:00Z");
        AccessRequestEntity request = new AccessRequestEntity();
        request.setCreatedAt(createTime);

        request.setCreateTime();

        assertThat(request.getCreatedAt()).isEqualTo(createTime);
    }

    @Test
    void usesExpectedDatabaseMappings() throws NoSuchFieldException {
        Table table = AccessRequestEntity.class.getAnnotation(Table.class);
        ManyToOne agentRelation = field("agent").getAnnotation(ManyToOne.class);
        Enumerated tradeType = field("tradeType").getAnnotation(Enumerated.class);
        Enumerated outcome = field("outcome").getAnnotation(Enumerated.class);
        Column requestedVolume = field("requestedVolume").getAnnotation(Column.class);
        Column riskScore = field("riskScore").getAnnotation(Column.class);

        assertThat(table.name()).isEqualTo("access_requests");
        assertThat(agentRelation.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(tradeType.value()).isEqualTo(EnumType.STRING);
        assertThat(outcome.value()).isEqualTo(EnumType.STRING);
        assertThat(requestedVolume.precision()).isEqualTo(15);
        assertThat(requestedVolume.scale()).isEqualTo(2);
        assertThat(riskScore.precision()).isEqualTo(3);
        assertThat(riskScore.scale()).isEqualTo(2);
    }

    private Field field(String name) throws NoSuchFieldException {
        return AccessRequestEntity.class.getDeclaredField(name);
    }
}
