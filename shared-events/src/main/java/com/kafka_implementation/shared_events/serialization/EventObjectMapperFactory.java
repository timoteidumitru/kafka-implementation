package com.kafka_implementation.shared_events.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class EventObjectMapperFactory {

    private EventObjectMapperFactory() {}

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    public static ObjectMapper get() {
        return OBJECT_MAPPER;
    }
}