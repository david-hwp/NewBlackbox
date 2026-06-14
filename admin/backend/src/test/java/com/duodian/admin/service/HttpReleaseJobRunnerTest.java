package com.duodian.admin.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpReleaseJobRunnerTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsReleaseContextToWorker() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/release", exchange -> handle(exchange, authorization, body));
        server.start();

        HttpReleaseJobRunner runner = new HttpReleaseJobRunner();
        ReflectionTestUtils.setField(runner, "workerUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/release");
        ReflectionTestUtils.setField(runner, "workerToken", "worker-token");
        ReflectionTestUtils.setField(runner, "repoUrl", "file:///tmp/repo.git");
        ReflectionTestUtils.setField(runner, "dryRun", false);
        ReflectionTestUtils.setField(runner, "pushReleaseBranch", false);
        ReflectionTestUtils.setField(runner, "workRoot", "/tmp/work");
        ReflectionTestUtils.setField(runner, "lockDir", "/tmp/locks");
        ReflectionTestUtils.setField(runner, "apiBaseUrl", "http://local.test/api/");
        ReflectionTestUtils.setField(runner, "workerBackendBaseUrl", "http://localhost:8011/api");

        runner.start(new ReleaseJobRunContext(
                99L,
                "beta",
                "com.example.beta",
                "com.example.beta.engine",
                "测试应用",
                "测试引擎",
                "release/source",
                "release/beta",
                "1.0.0",
                100,
                "1.0.0",
                200,
                "admin-token",
                "http://backend:8080/api",
                "http://backend:8080/api/release-jobs/99/callback/progress",
                "http://backend:8080/api/release-jobs/99/callback/complete",
                "job-token"
        ));

        assertThat(authorization.get()).isEqualTo("Bearer worker-token");
        assertThat(body.get()).contains("\"JOB_ID\":\"99\"");
        assertThat(body.get()).contains("\"CHANNEL_CODE\":\"beta\"");
        assertThat(body.get()).contains("\"APP_APPLICATION_ID\":\"com.example.beta\"");
        assertThat(body.get()).contains("\"ENGINE_APPLICATION_ID\":\"com.example.beta.engine\"");
        assertThat(body.get()).contains("\"ENGINE_DATA_ROOT_NAME\":\"blackbox-beta\"");
        assertThat(body.get()).contains("\"API_BASE_URL\":\"http://local.test/api/\"");
        assertThat(body.get()).contains("\"BACKEND_BASE_URL\":\"http://localhost:8011/api\"");
        assertThat(body.get()).contains("\"PROGRESS_CALLBACK_URL\":\"http://localhost:8011/api/release-jobs/99/callback/progress\"");
        assertThat(body.get()).contains("\"COMPLETE_CALLBACK_URL\":\"http://localhost:8011/api/release-jobs/99/callback/complete\"");
        assertThat(body.get()).contains("\"ADMIN_AUTHORIZATION_TOKEN\":\"admin-token\"");
        assertThat(body.get()).contains("\"JOB_TOKEN\":\"job-token\"");
        assertThat(body.get()).contains("\"DRY_RUN\":\"false\"");
        assertThat(body.get()).contains("\"PUSH_RELEASE_BRANCH\":\"false\"");
        assertThat(body.get()).contains("\"WORK_ROOT\":\"/tmp/work\"");
        assertThat(body.get()).contains("\"LOCK_DIR\":\"/tmp/locks\"");
    }

    private void handle(
            HttpExchange exchange,
            AtomicReference<String> authorization,
            AtomicReference<String> body
    ) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(202, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
