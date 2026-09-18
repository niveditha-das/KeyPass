package com.keypass.server.key;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keypass.common.crypto.CredentialCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The credential signature covers the exact JSON bytes produced here, so this ObjectMapper is
 * deliberately its own instance rather than the shared web ObjectMapper: a later change to how
 * API responses are serialized (e.g. a global Jackson feature toggle) must never be able to
 * silently change how credentials sign and verify.
 */
@Configuration
public class CredentialCodecConfig {

    @Bean
    public CredentialCodec credentialCodec() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new CredentialCodec(mapper);
    }
}
