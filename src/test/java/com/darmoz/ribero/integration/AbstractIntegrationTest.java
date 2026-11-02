package com.darmoz.ribero.integration;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * NOTA: a diferencia del AbstractIntegrationTest de darmoz-auth (que usa
 * @Testcontainers + @Container), acá el contenedor de Postgres se arranca a
 * mano en un bloque estático y nunca se detiene explícitamente ("singleton
 * container pattern"). Con @Container, el field estático se comparte por
 * herencia entre todas las clases *IT (SyncControllerIT, etc.):
 * la extensión de JUnit5 lo para en el afterAll() de la PRIMERA clase que
 * termina y lo reinicia (con un container Docker nuevo, otro puerto) para la
 * siguiente, pero Spring cachea el ApplicationContext de la primera clase con
 * el DataSource apuntando al container viejo ya parado -> Connection refused
 * en la segunda clase. Arrancarlo una sola vez y dejar que Ryuk lo mate al
 * terminar la JVM evita el problema.
 */
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Path PUBLIC_KEY_PATH;
    static final PrivateKey TEST_PRIVATE_KEY;
    static final PublicKey TEST_PUBLIC_KEY;

    static {
        try {
            POSTGRES.start();

            KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            TEST_PRIVATE_KEY = keyPair.getPrivate();
            TEST_PUBLIC_KEY = keyPair.getPublic();

            Path tempDir = Files.createTempDirectory("ribero-be-test-keys");
            PUBLIC_KEY_PATH = tempDir.resolve("public_key.pem");
            Files.writeString(PUBLIC_KEY_PATH, toPem("PUBLIC KEY", TEST_PUBLIC_KEY.getEncoded()));
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("No se pudieron generar las llaves RSA de test", e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jwt.public-key-path", () -> PUBLIC_KEY_PATH.toString());
    }

    private static String toPem(String type, byte[] encoded) {
        String base64 = Base64.getEncoder().encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
