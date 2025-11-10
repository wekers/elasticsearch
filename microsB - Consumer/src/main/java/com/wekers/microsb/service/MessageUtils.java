package com.wekers.microsb.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class MessageUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String formatMessageBody(byte[] body) {
        if (body == null || body.length == 0) {
            return "[Empty message]";
        }

        try {
            String rawText = new String(body);

            // Remove escapes desnecessários
            String cleaned = rawText
                    .replace("\\\\", "\\")
                    .replace("\\\"", "\"")
                    .trim();

            // Tenta formatar como JSON bonito
            if (isValidJson(cleaned)) {
                try {
                    Object jsonObj = OBJECT_MAPPER.readValue(cleaned, Object.class);
                    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObj);
                } catch (JsonProcessingException e) {
                    return cleaned; // Retorna o JSON sem formatação se não conseguir
                }
            }

            return cleaned;

        } catch (Exception e) {
            log.debug("Error formatting message", e);
            return new String(body);
        }
    }

    private static boolean isValidJson(String text) {
        if (text == null || text.isEmpty()) return false;
        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
}