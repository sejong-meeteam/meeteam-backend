package com.wardk.meeteam_backend.s3unit;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import com.wardk.meeteam_backend.global.config.S3ImageSerializationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class S3ImageSerializationConfigTest {
    private static final String STORED = "https://bucket.example.invalid/images/a.png";
    private static final String RESPONSE = "https://delivery.example.invalid/a.png";

    public record Images(String imageUrl, String profileImageUrl, String creatorImageUrl,
                         String projectImageUrl, String questionerProfileImageUrl,
                         String writerProfileImageUrl, String fileUrl, String description) {}
    public record Envelope(List<Images> items) {}

    @Test
    void signsAllSevenFieldsInNestedListsOnlyOnHttpMapperLeavingSharedAndRedisCopiesUntouched() throws Exception {
        AmazonS3 s3 = mock(AmazonS3.class);
        when(s3.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(new URL(RESPONSE));
        ObjectMapper shared = new ObjectMapper();
        ObjectMapper redisBefore = shared.copy();
        MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter(shared);
        StringHttpMessageConverter text = new StringHttpMessageConverter();
        List<HttpMessageConverter<?>> converters = new ArrayList<>(List.of(text, json));
        new S3ImageSerializationConfig(new S3ImageUrlResolver(s3, "test-bucket", "https://bucket.example.invalid", 60))
                .extendMessageConverters(converters);
        Images images = new Images(STORED, STORED, STORED, STORED, STORED, STORED, STORED, STORED);
        Envelope dto = new Envelope(List.of(images, images));
        JsonNode body = shared.readTree(json.getObjectMapper().writeValueAsString(dto));
        for (JsonNode item : body.get("items")) {
            for (String name : List.of("imageUrl", "profileImageUrl", "creatorImageUrl", "projectImageUrl",
                    "questionerProfileImageUrl", "writerProfileImageUrl", "fileUrl")) {
                assertEquals(RESPONSE, item.get(name).asText(), name);
            }
            assertEquals(STORED, item.get("description").asText());
        }
        verify(s3, times(14)).generatePresignedUrl(any(GeneratePresignedUrlRequest.class));
        assertNotSame(shared, json.getObjectMapper());
        assertSame(text, converters.get(0));
        for (ObjectMapper mapper : List.of(shared, redisBefore, shared.copy())) {
            assertEquals(STORED, mapper.readTree(mapper.writeValueAsString(dto)).at("/items/0/imageUrl").asText());
        }
        assertEquals(STORED, images.imageUrl());
        verifyNoMoreInteractions(s3);
    }
}
