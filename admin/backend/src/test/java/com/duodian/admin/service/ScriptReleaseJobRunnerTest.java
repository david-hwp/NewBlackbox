package com.duodian.admin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptReleaseJobRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void startsScriptWithSeparateAdminAndCallbackTokens() throws Exception {
        Path output = tempDir.resolve("env.txt");
        Path script = tempDir.resolve("runner.sh");
        Files.writeString(script, """
                #!/usr/bin/env bash
                set -euo pipefail
                {
                  echo "JOB_ID=$JOB_ID"
                  echo "CHANNEL_CODE=$CHANNEL_CODE"
                  echo "APP_APPLICATION_ID=$APP_APPLICATION_ID"
                  echo "ENGINE_APPLICATION_ID=$ENGINE_APPLICATION_ID"
                  echo "ENGINE_DATA_ROOT_NAME=$ENGINE_DATA_ROOT_NAME"
                  echo "API_BASE_URL=$API_BASE_URL"
                  echo "PROGRESS_CALLBACK_URL=$PROGRESS_CALLBACK_URL"
                  echo "COMPLETE_CALLBACK_URL=$COMPLETE_CALLBACK_URL"
                  echo "JOB_TOKEN=$JOB_TOKEN"
                  echo "ADMIN_AUTHORIZATION_TOKEN=$ADMIN_AUTHORIZATION_TOKEN"
                  echo "DRY_RUN=$DRY_RUN"
                  echo "PUSH_RELEASE_BRANCH=$PUSH_RELEASE_BRANCH"
                } > "__OUTPUT__"
                """.replace("__OUTPUT__", output.toString()));
        script.toFile().setExecutable(true);

        ScriptReleaseJobRunner runner = new ScriptReleaseJobRunner();
        ReflectionTestUtils.setField(runner, "scriptPath", script.toString());
        ReflectionTestUtils.setField(runner, "repoUrl", "file:///tmp/repo.git");
        ReflectionTestUtils.setField(runner, "workdir", tempDir.toString());
        ReflectionTestUtils.setField(runner, "dryRun", true);
        ReflectionTestUtils.setField(runner, "pushReleaseBranch", true);
        ReflectionTestUtils.setField(runner, "workRoot", tempDir.resolve("work").toString());
        ReflectionTestUtils.setField(runner, "lockDir", tempDir.resolve("locks").toString());
        ReflectionTestUtils.setField(runner, "apiBaseUrl", "http://local.test/api/");

        ReleaseJobRunContext context = new ReleaseJobRunContext(
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
        );

        runner.start(context);
        awaitFile(output);
        String env = Files.readString(output);
        runner.shutdown();

        assertThat(env).contains("JOB_ID=99");
        assertThat(env).contains("CHANNEL_CODE=beta");
        assertThat(env).contains("APP_APPLICATION_ID=com.example.beta");
        assertThat(env).contains("ENGINE_APPLICATION_ID=com.example.beta.engine");
        assertThat(env).contains("ENGINE_DATA_ROOT_NAME=blackbox-beta");
        assertThat(env).contains("API_BASE_URL=http://local.test/api/");
        assertThat(env).contains("PROGRESS_CALLBACK_URL=http://backend:8080/api/release-jobs/99/callback/progress");
        assertThat(env).contains("COMPLETE_CALLBACK_URL=http://backend:8080/api/release-jobs/99/callback/complete");
        assertThat(env).contains("JOB_TOKEN=job-token");
        assertThat(env).contains("ADMIN_AUTHORIZATION_TOKEN=admin-token");
        assertThat(env).contains("DRY_RUN=true");
        assertThat(env).contains("PUSH_RELEASE_BRANCH=true");
    }

    private void awaitFile(Path output) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(output)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("script output was not created");
    }
}
