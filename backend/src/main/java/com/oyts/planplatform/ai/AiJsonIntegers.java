package com.oyts.planplatform.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** Administrator input must not silently truncate a decimal or coerce a string. */
final class AiJsonIntegers {
    private AiJsonIntegers() {}
    public static final class IntValue extends StdDeserializer<Integer> {
        public IntValue() { super(Integer.class); }
        @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) throw JsonMappingException.from(parser,"Expected an integer");
            return parser.getIntValue();
        }
    }
    public static final class LongValue extends StdDeserializer<Long> {
        public LongValue() { super(Long.class); }
        @Override public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) throw JsonMappingException.from(parser,"Expected an integer version");
            return parser.getLongValue();
        }
    }
}
