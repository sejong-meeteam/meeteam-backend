package com.wardk.meeteam_backend.global.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/** Resolve known image fields at the HTTP serialization boundary, not in persistence/cache. */
@Configuration
public class S3ImageSerializationConfig implements WebMvcConfigurer {
    private static final Set<String> IMAGE_FIELDS = Set.of(
            "imageUrl", "profileImageUrl", "creatorImageUrl", "projectImageUrl",
            "questionerProfileImageUrl", "writerProfileImageUrl", "fileUrl");
    private final S3ImageUrlResolver resolver;

    public S3ImageSerializationConfig(S3ImageUrlResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jsonConverter) {
                // Never register this module on the shared mapper: Redis copies it for persistence.
                jsonConverter.setObjectMapper(jsonConverter.getObjectMapper().copy()
                        .registerModule(s3ImageUrlModule()));
            }
        }
    }

    private SimpleModule s3ImageUrlModule() {
        JsonSerializer<Object> serializer = new JsonSerializer<>() {
            @Override
            public void serialize(Object value, JsonGenerator generator, SerializerProvider provider)
                    throws IOException {
                generator.writeString(resolver.resolve((String) value));
            }
        };
        SimpleModule module = new SimpleModule("s3-image-response-urls");
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                    BeanDescription description, List<BeanPropertyWriter> properties) {
                for (BeanPropertyWriter property : properties) {
                    if (property.getType().hasRawClass(String.class)
                            && IMAGE_FIELDS.contains(property.getName())) {
                        property.assignSerializer(serializer);
                    }
                }
                return properties;
            }
        });
        return module;
    }
}
