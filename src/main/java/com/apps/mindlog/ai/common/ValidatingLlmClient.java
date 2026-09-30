package com.apps.mindlog.ai.common;

import jakarta.validation.Validator;
import java.util.Objects;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Construct with the real transport after provider/model selection; not auto-registered. */
public class ValidatingLlmClient implements LlmClient {
    private static final int MAX_OUTPUT_CHARS=100_000;
    private final LlmTransport transport;
    private final Validator validator;
    private final JsonMapper json=JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    public ValidatingLlmClient(LlmTransport transport,Validator validator){
        this.transport=Objects.requireNonNull(transport);this.validator=Objects.requireNonNull(validator);
    }
    @Override public <T> T generate(Request<T> request){
        String raw;
        try { raw=transport.complete(request); }
        catch(InvalidLlmOutputException invalid){throw invalid;}
        catch(RuntimeException unavailable){throw new LlmUnavailableException();}
        try {
            if(raw==null||raw.length()>MAX_OUTPUT_CHARS)throw new InvalidLlmOutputException();
            var tree=json.readTree(raw);
            if(!tree.isObject())throw new InvalidLlmOutputException();
            T result=json.treeToValue(tree,request.resultType());
            if(!validator.validate(result).isEmpty())throw new InvalidLlmOutputException();
            return result;
        } catch(RuntimeException invalid){throw new InvalidLlmOutputException();}
    }
}
