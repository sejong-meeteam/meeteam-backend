package com.wardk.meeteam_backend.domain.file.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

/** Signs response URLs only. Never persist temporary credentials in database URLs. */
@Component
public class S3ImageUrlResolver {
    private final AmazonS3 amazonS3;
    private final String bucket;
    private final String baseUrl;
    private final long expirySeconds;

    public S3ImageUrlResolver(AmazonS3 amazonS3,
            @Value("${cloud.aws.s3.bucket}") String bucket,
            @Value("${file.upload.s3.base-url}") String baseUrl,
            @Value("${file.upload.s3.presigned-expiry-seconds:3600}") long expirySeconds) {
        if (expirySeconds < 60 || expirySeconds > 3600) {
            throw new IllegalArgumentException("Image URL lifetime must be between 60 and 3600 seconds");
        }
        this.amazonS3 = amazonS3;
        this.bucket = bucket;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.expirySeconds = expirySeconds;
    }

    public String resolve(String storedUrl) {
        if (storedUrl == null || !storedUrl.startsWith(baseUrl + "/")) {
            return storedUrl;
        }
        String key = storedUrl.substring(baseUrl.length() + 1);
        // Legacy profile URLs remain readable when permitted by the bucket policy.
        if (!S3ImageKeys.isAllowed(key)) {
            return storedUrl;
        }
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key)
                .withMethod(HttpMethod.GET)
                .withExpiration(Date.from(Instant.now().plusSeconds(expirySeconds)));
        return amazonS3.generatePresignedUrl(request).toExternalForm();
    }
}
