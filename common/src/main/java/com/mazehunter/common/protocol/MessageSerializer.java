package com.mazehunter.common.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Thread-safe utility for serializing and deserializing protocol messages.
 */
public final class MessageSerializer {

    private static final ObjectMapper MAPPER = createObjectMapper();

    private MessageSerializer() {
        // Utility class
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /**
     * Serializes a message envelope to a single JSON line without line terminators.
     *
     * @param envelope the message envelope to serialize
     * @return the serialized JSON string
     * @throws JsonProcessingException if serialization fails
     */
    public static String serialize(MessageEnvelope envelope) throws JsonProcessingException {
        return MAPPER.writeValueAsString(envelope);
    }

    /**
     * Deserializes a raw JSON string into a MessageEnvelope.
     *
     * @param json the raw JSON string
     * @return the parsed MessageEnvelope
     * @throws JsonProcessingException if parsing fails or JSON is malformed
     */
    public static MessageEnvelope deserialize(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, MessageEnvelope.class);
    }

    /**
     * Converts a Java object into a Jackson JsonNode tree.
     */
    public static JsonNode toJsonNode(Object object) {
        if (object == null) {
            return MAPPER.nullNode();
        }
        if (object instanceof JsonNode node) {
            return node;
        }
        return MAPPER.valueToTree(object);
    }

    /**
     * Converts a JsonNode tree into a strongly-typed Java class.
     */
    public static <T> T fromJsonNode(JsonNode node, Class<T> clazz) throws JsonProcessingException {
        return MAPPER.treeToValue(node, clazz);
    }
}
