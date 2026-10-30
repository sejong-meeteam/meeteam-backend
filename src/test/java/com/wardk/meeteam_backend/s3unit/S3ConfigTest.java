package com.wardk.meeteam_backend.s3unit;

import com.amazonaws.services.s3.AmazonS3;
import com.wardk.meeteam_backend.global.config.S3Config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class S3ConfigTest {
    @Test
    void createsClientWithoutRequiringStaticAccessAndSecretKeyProperties() {
        new ApplicationContextRunner()
                .withUserConfiguration(S3Config.class)
                .withPropertyValues("cloud.aws.region.static=ap-northeast-2")
                .run(context -> assertThat(context).hasSingleBean(AmazonS3.class));
    }
}
