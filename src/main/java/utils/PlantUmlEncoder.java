package utils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

/**
 * Encode du texte PlantUML au format attendu par le serveur PlantUML en ligne.
 * <p>
 * L'algorithme est : UTF-8 → Deflate(raw) → base64 modifié (alphabet PlantUML).
 * Le résultat peut être concaténé à {@code https://www.plantuml.com/plantuml/svg/}
 * pour obtenir un SVG du diagramme.
 * </p>
 */
public final class PlantUmlEncoder {

    /** URL de base du serveur PlantUML public. */
    public static final String PLANTUML_SERVER = "https://www.plantuml.com/plantuml/svg/";

    private PlantUmlEncoder() {
    }

    /**
     * Encode une chaîne PlantUML pour le serveur en ligne.
     *
     * @param text le texte PlantUML (avec ou sans {@code @startuml/@enduml})
     * @return la chaîne encodée à ajouter à l'URL du serveur
     */
    public static String encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        byte[] compressed = deflate(data);
        return toPlantUmlBase64(compressed);
    }

    /**
     * Retourne l'URL SVG complète pour un diagramme PlantUML.
     *
     * @param text le texte PlantUML
     * @return l'URL vers le SVG rendu
     */
    public static String toSvgUrl(String text) {
        return PLANTUML_SERVER + encode(text);
    }

    // ---- Deflate (raw, pas GZIP ni ZLIB) ----

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true); // raw deflate
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buf);
            bos.write(buf, 0, count);
        }
        deflater.end();
        return bos.toByteArray();
    }

    // ---- Base64 modifié (alphabet PlantUML) ----

    private static final String PLANTUML_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_";

    private static String toPlantUmlBase64(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < data.length) {
            int b1 = data[i++] & 0xFF;
            if (i < data.length) {
                int b2 = data[i++] & 0xFF;
                if (i < data.length) {
                    int b3 = data[i++] & 0xFF;
                    append3Bytes(sb, b1, b2, b3);
                } else {
                    append3Bytes(sb, b1, b2, 0);
                }
            } else {
                append3Bytes(sb, b1, 0, 0);
            }
        }
        return sb.toString();
    }

    private static void append3Bytes(StringBuilder sb, int b1, int b2, int b3) {
        int c1 = b1 >> 2;
        int c2 = ((b1 & 0x3) << 4) | (b2 >> 4);
        int c3 = ((b2 & 0xF) << 2) | (b3 >> 6);
        int c4 = b3 & 0x3F;
        sb.append(PLANTUML_ALPHABET.charAt(c1 & 0x3F));
        sb.append(PLANTUML_ALPHABET.charAt(c2 & 0x3F));
        sb.append(PLANTUML_ALPHABET.charAt(c3 & 0x3F));
        sb.append(PLANTUML_ALPHABET.charAt(c4 & 0x3F));
    }
}
