package com.taskqueue.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class PayloadHasher {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String hash(String taskType, Map<String, Object> payload) {
        try {
            Map<String, Object> sortedPayload = new TreeMap<>(payload == null ? Map.of() : payload);
            String json = objectMapper.writeValueAsString(sortedPayload);
            String toHash = taskType + ":" + json;
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toHash.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            log.error("Error hashing payload", e);
            return String.valueOf(System.currentTimeMillis()); // fallback
        }
    }
}
