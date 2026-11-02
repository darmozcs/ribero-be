package com.darmoz.ribero.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Este servicio solo verifica JWTs (nunca los firma), asi que a diferencia
 * de darmoz-auth.PemUtils solo lee llaves publicas.
 */
public final class PemUtils {

    private PemUtils() {
    }

    public static PublicKey readPublicKey(Path path) {
        try {
            byte[] der = decodePem(Files.readString(path));
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePublic(new X509EncodedKeySpec(der));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo leer la llave publica en " + path, e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "La llave publica en " + path + " no es un RSA X.509 valido", e);
        }
    }

    private static byte[] decodePem(String pem) {
        String base64 = pem
                .replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
