package com.wekers.microsb.config;

public class RabbitMQConstants {

    // Headers
    public static final String RETRY_HEADER = "x-retry-count";
    public static final String ORIGINAL_EXCHANGE_HEADER = "x-original-exchange";
    public static final String ORIGINAL_ROUTING_KEY_HEADER = "x-original-routing-key";

    // Content Types
    public static final String CONTENT_TYPE_JSON = "application/json";

    // Error Messages
    public static final String ERROR_MAX_RETRIES_EXCEEDED = "Max retries exceeded";
    public static final String ERROR_PROCESSING_MESSAGE = "Error processing message";

    // Log Messages
    public static final String LOG_RETRY_ATTEMPT = "Retry attempt {}/{}";
    public static final String LOG_SENT_TO_DLQ = "Message sent to DLQ after {} retries";

    private RabbitMQConstants() {}
}