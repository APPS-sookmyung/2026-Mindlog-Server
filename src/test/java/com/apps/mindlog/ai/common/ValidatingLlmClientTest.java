package com.apps.mindlog.ai.common;

import com.apps.mindlog.ai.job.JobKind;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ValidatingLlmClientTest {
    record Output(@NotBlank String content){}
    LlmClient.Request<Output> request(){return new LlmClient.Request<>(JobKind.DIARY_DRAFT,"system","{\"private\":\"원문\"}",Output.class,Duration.ofSeconds(30));}
    Output generate(String value){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            return new ValidatingLlmClient(r->value,factory.getValidator()).generate(request());
        }
    }
    @Test void validStructuredOutputIsReturned(){assertThat(generate("{\"content\":\"일기\"}").content()).isEqualTo("일기");}
    @Test void malformedNonObjectTrailingDuplicateAndUnknownFieldsAreRejected(){
        for(var value:new String[]{"not json","[]","null","{} {}","{\"content\":\"a\",\"content\":\"b\"}","{\"content\":\"a\",\"secret\":1}"})
            assertThatThrownBy(()->generate(value)).isInstanceOf(InvalidLlmOutputException.class)
                    .hasMessage("Invalid structured AI output").hasNoCause();
    }
    @Test void beanValidationRejectsMissingAndBlankFields(){
        for(var value:new String[]{"{}","{\"content\":null}","{\"content\":\" \"}"})
            assertThatThrownBy(()->generate(value)).isInstanceOf(InvalidLlmOutputException.class);
    }
    @Test void nullAndOversizedPayloadAreRejected(){
        assertThatThrownBy(()->generate(null)).isInstanceOf(InvalidLlmOutputException.class);
        assertThatThrownBy(()->generate("{\"content\":\""+"a".repeat(100001)+"\"}"))
                .isInstanceOf(InvalidLlmOutputException.class);
    }
    @Test void providerExceptionAndRequestDescriptionDoNotExposePrivateData(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var client=new ValidatingLlmClient(r->{throw new RuntimeException("provider key and diary");},factory.getValidator());
            assertThatThrownBy(()->client.generate(request())).isInstanceOf(LlmUnavailableException.class)
                    .hasMessage("AI provider unavailable").hasNoCause();
        }
        assertThat(request().toString()).doesNotContain("원문","private","system");
    }
    @Test void requestTimeoutMustFitWithinJobLease(){
        for(var duration:new Duration[]{Duration.ZERO,Duration.ofSeconds(-1),Duration.ofMinutes(10)})
            assertThatIllegalArgumentException().isThrownBy(()->new LlmClient.Request<>(JobKind.DIARY_DRAFT,"system","{}",Output.class,duration));
    }
}
