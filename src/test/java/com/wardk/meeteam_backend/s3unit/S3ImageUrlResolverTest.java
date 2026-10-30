package com.wardk.meeteam_backend.s3unit;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URL;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class S3ImageUrlResolverTest {
    private static final String BASE = "https://bucket.example.invalid";

    @Test
    void signsOwnedImagesAndLegacyProfilesUsingGetAndBoundedExpiryWithoutChangingStoredUrl() throws Exception {
        for (long expiry : new long[]{60, 3600}) {
            for (String key : new String[]{"images/photo.png", "images/profiles/photo.png", "profiles/legacy.png"}) {
                AmazonS3 s3 = mock(AmazonS3.class);
                when(s3.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                        .thenReturn(new URL("https://delivery.example.invalid/image.png"));
                S3ImageUrlResolver resolver = new S3ImageUrlResolver(s3, "test-bucket", BASE + "/", expiry);
                String storedUrl = BASE + "/" + key;
                long before = System.currentTimeMillis();
                assertEquals("https://delivery.example.invalid/image.png", resolver.resolve(storedUrl));
                long after = System.currentTimeMillis();
                ArgumentCaptor<GeneratePresignedUrlRequest> request = ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
                verify(s3).generatePresignedUrl(request.capture());
                assertEquals("test-bucket", request.getValue().getBucketName());
                assertEquals(key, request.getValue().getKey());
                assertEquals(HttpMethod.GET, request.getValue().getMethod());
                assertTrue(request.getValue().getExpiration().getTime() >= before + expiry * 1000);
                assertTrue(request.getValue().getExpiration().getTime() <= after + expiry * 1000);
                assertEquals(BASE + "/" + key, storedUrl);
            }
        }
    }

    @Test
    void leavesNullExternalSpoofedAndUnsafeUrlsUntouched() {
        AmazonS3 s3 = mock(AmazonS3.class);
        S3ImageUrlResolver resolver = new S3ImageUrlResolver(s3, "test-bucket", BASE, 60);
        for (String url : new String[]{null, "", "https://external.example.invalid/images/a.png",
                BASE + ".evil.invalid/images/a.png", BASE + "/other/a.png", BASE + "/images/../a.png",
                BASE + "/images/a.png?download=1", BASE + "/images/a.png#part",
                BASE + "/images/", BASE + "/images/a\\b.png",
                BASE + "/images/%2e%2e/a.png", BASE + "/images//a.png",
                BASE + "/images/a.svg", BASE + "/images/a%2Fsecret.png"}) {
            assertEquals(url, resolver.resolve(url));
        }
        verifyNoInteractions(s3);
    }

    @Test
    void rejectsExpiryOutsideSupportedRange() {
        AmazonS3 s3 = mock(AmazonS3.class);
        for (long expiry : new long[]{-1, 0, 59, 3601}) {
            assertThrows(IllegalArgumentException.class, () -> new S3ImageUrlResolver(s3, "test-bucket", BASE, expiry));
        }
        verifyNoInteractions(s3);
    }
}
