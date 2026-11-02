package com.darmoz.ribero.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public class JwtVerificationProperties {

    /** Ruta al PEM con la llave publica RSA (X.509) que emite darmoz-auth. */
    private String publicKeyPath = "./keys-dev/public_key.pem";

    /** Issuer esperado en el claim "iss" de los tokens. */
    private String issuer = "darmoz-auth";

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
