package com.darmoz.ribero.config;

import com.darmoz.ribero.util.PemUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.security.PublicKey;

@Configuration
@EnableConfigurationProperties({JwtVerificationProperties.class, AppProperties.class})
public class RsaPublicKeyConfig {

    @Bean
    public PublicKey jwtVerificationKey(JwtVerificationProperties properties) {
        return PemUtils.readPublicKey(Path.of(properties.getPublicKeyPath()));
    }
}
