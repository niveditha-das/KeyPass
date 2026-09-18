package com.keypass.server.key;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keypass.common.crypto.CredentialCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CredentialCodecConfig {

    @Bean
    public CredentialCodec credentialCodec(ObjectMapper objectMapper) {
        return new CredentialCodec(objectMapper);
    }
}
