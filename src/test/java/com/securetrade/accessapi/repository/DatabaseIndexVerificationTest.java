package com.securetrade.accessapi.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DatabaseIndexVerificationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void requiredPostgreSqlIndexesExist() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName())
                    .isEqualTo("PostgreSQL");

            assertIndex(
                    connection,
                    "users",
                    "uk_users_username",
                    true,
                    List.of(new ExpectedColumn("username", "A")));
            assertIndex(
                    connection,
                    "trading_agents",
                    "uk_trading_agents_user_id",
                    true,
                    List.of(new ExpectedColumn("user_id", "A")));
            assertIndex(
                    connection,
                    "trading_agents",
                    "uk_trading_agents_agent_code",
                    true,
                    List.of(new ExpectedColumn("agent_code", "A")));
            assertIndex(
                    connection,
                    "access_requests",
                    "idx_access_requests_agent_id",
                    false,
                    List.of(new ExpectedColumn("agent_id", "A")));
            assertIndex(
                    connection,
                    "access_requests",
                    "uk_access_requests_agent_idempotency",
                    true,
                    List.of(
                            new ExpectedColumn("agent_id", "A"),
                            new ExpectedColumn("idempotency_key", "A")));
            assertIndex(
                    connection,
                    "audit_logs",
                    "idx_audit_logs_timestamp",
                    false,
                    List.of(new ExpectedColumn("timestamp", "D")));
        }
    }

    private void assertIndex(
            Connection connection,
            String tableName,
            String indexName,
            boolean unique,
            List<ExpectedColumn> expectedColumns) throws SQLException {

        List<IndexColumn> indexColumns = readIndexColumns(
                connection,
                tableName,
                indexName);

        assertThat(indexColumns)
                .as("index %s on table %s", indexName, tableName)
                .isNotEmpty()
                .allMatch(column -> column.unique() == unique);
        assertThat(indexColumns)
                .extracting(IndexColumn::name, IndexColumn::direction)
                .containsExactlyElementsOf(expectedColumns.stream()
                        .map(column -> org.assertj.core.groups.Tuple.tuple(
                                column.name(),
                                column.direction()))
                        .toList());
    }

    private List<IndexColumn> readIndexColumns(
            Connection connection,
            String tableName,
            String indexName) throws SQLException {

        DatabaseMetaData metadata = connection.getMetaData();
        List<IndexColumn> columns = new ArrayList<>();

        try (ResultSet resultSet = metadata.getIndexInfo(
                connection.getCatalog(),
                connection.getSchema(),
                tableName,
                false,
                false)) {

            while (resultSet.next()) {
                if (!indexName.equals(resultSet.getString("INDEX_NAME"))) {
                    continue;
                }

                columns.add(new IndexColumn(
                        resultSet.getString("COLUMN_NAME"),
                        !resultSet.getBoolean("NON_UNIQUE"),
                        resultSet.getShort("ORDINAL_POSITION"),
                        resultSet.getString("ASC_OR_DESC")));
            }
        }

        columns.sort(Comparator.comparingInt(IndexColumn::position));
        return columns;
    }

    private record IndexColumn(
            String name,
            boolean unique,
            int position,
            String direction) {
    }

    private record ExpectedColumn(String name, String direction) {
    }
}
