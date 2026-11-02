package com.darmoz.ribero.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * UUID de la Application de darmoz-auth que este servicio acepta (claim
     * "applicationId"). Por defecto la de "ribero" (V9 de darmoz-auth); si
     * se deja en blanco (como en los tests) el chequeo se saltea, con un
     * warning al arrancar.
     */
    private String expectedApplicationId;

    public String getExpectedApplicationId() {
        return expectedApplicationId;
    }

    public void setExpectedApplicationId(String expectedApplicationId) {
        this.expectedApplicationId = expectedApplicationId;
    }
}
