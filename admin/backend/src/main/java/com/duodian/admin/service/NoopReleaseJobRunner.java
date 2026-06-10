package com.duodian.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.release.runner", havingValue = "noop", matchIfMissing = true)
public class NoopReleaseJobRunner implements ReleaseJobRunner {
    private static final Logger log = LoggerFactory.getLogger(NoopReleaseJobRunner.class);

    @Override
    public void start(ReleaseJobRunContext context) {
        log.info(
                "Release job runner placeholder invoked: job={} channel={} sourceBranch={} channelBranch={}",
                context.jobId(),
                context.channelCode(),
                context.sourceReleaseBranch(),
                context.channelReleaseBranch()
        );
    }
}
