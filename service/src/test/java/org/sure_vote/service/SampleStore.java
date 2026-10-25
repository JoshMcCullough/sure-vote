package org.sure_vote.service;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.sure_vote.repository.InMemorySureVoteRepository;
import org.sure_vote.repository.JdbcSureVoteRepository;
import org.sure_vote.repository.SureVoteRepository;
import org.testcontainers.containers.PostgreSQLContainer;

final class SampleStore {
    private static final String TEMPLATE = "flow_template";

    private static PostgreSQLContainer<?> postgres;
    private static int databases;

    private SampleStore() {
    }

    static boolean onPostgres() {
        final var store = System.getProperty("surevote.store");
        return "postgres".equals(store);
    }

    static SureVoteRepository open() {
        if (SampleStore.onPostgres()) {
            return SampleStore.fresh();
        }
        else {
            return new InMemorySureVoteRepository();
        }
    }

    private static synchronized SureVoteRepository fresh() {
        final var container = SampleStore.container();
        final var name = "flow_" + SampleStore.next();
        final var url = SampleStore.url(container, name);
        SampleStore.execute(container, "create database " + name + " template " + TEMPLATE);
        return JdbcSureVoteRepository.open(url, container.getUsername(), container.getPassword());
    }

    private static String url(final PostgreSQLContainer<?> container, final String name) {
        final var host = container.getHost();
        final var port = container.getMappedPort(5432);
        return "jdbc:postgresql://" + host + ":" + port + "/" + name;
    }

    private static int next() {
        databases = databases + 1;
        return databases;
    }

    private static PostgreSQLContainer<?> container() {
        if (postgres == null) {
            postgres = SampleStore.started();
            return postgres;
        }
        else {
            return postgres;
        }
    }

    private static PostgreSQLContainer<?> started() {
        final var container = new PostgreSQLContainer<>("postgres:16-alpine");
        container.start();
        SampleStore.execute(container, "create database " + TEMPLATE);
        JdbcSureVoteRepository.migrate(
            SampleStore.url(container, TEMPLATE),
            container.getUsername(),
            container.getPassword()
        );
        return container;
    }

    private static void execute(final PostgreSQLContainer<?> container, final String sql) {
        final var url = container.getJdbcUrl();
        try {
            final var connection = DriverManager.getConnection(url, container.getUsername(), container.getPassword());
            try {
                final var statement = connection.createStatement();
                statement.execute(sql);
                statement.close();
            }
            finally {
                connection.close();
            }
        }
        catch (final SQLException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }
}
