package com.apps.mindlog.ai.after;
import com.apps.mindlog.after.dto.response.AnalysisResult;
import com.apps.mindlog.reference.service.ReferenceQueryService.DistortionDefinition;
import com.apps.mindlog.ai.common.InvalidLlmOutputException;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AfterAnalysisValidatorTest {
    @Test void exactQuoteAndCanonicalDetectedTagsAreRequired(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var validator=new AfterAnalysisValidator(factory.getValidator());
            var catalog=List.of(new DistortionDefinition(1,"예측","설명"));
            var good=new AnalysisResult.Distortion(1,"예측","실패할 거야","설명");
            var card=new AnalysisResult.Card("예측",1L,"제목","내용");
            validator.validate("나는 실패할 거야",new AnalysisResult("요약",1,List.of(good),List.of(card)),catalog);
            for(var bad:List.of(new AnalysisResult("요약",2,List.of(good),List.of(card)),
                    new AnalysisResult("요약",1,List.of(new AnalysisResult.Distortion(1,"예측","성공할 거야","설명")),List.of(card)),
                    new AnalysisResult("요약",0,List.of(),List.of(card)),
                    new AnalysisResult("요약",2,List.of(good,good),List.of(card)),
                    new AnalysisResult("요약",0,List.of(),List.of(new AnalysisResult.Card("회복력",null,"제목","내용")))))
                assertThatThrownBy(()->validator.validate("나는 실패할 거야",bad,catalog)).isInstanceOf(InvalidLlmOutputException.class);
        }
    }
}
