package com.apps.mindlog.ai.after;

import com.apps.mindlog.after.dto.response.AnalysisResult;
import com.apps.mindlog.ai.common.InvalidLlmOutputException;
import com.apps.mindlog.reference.service.ReferenceQueryService.DistortionDefinition;
import jakarta.validation.Validator;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AfterAnalysisValidator {
    private final Validator validator;
    public AfterAnalysisValidator(Validator validator){this.validator=validator;}
    public void validate(String original,AnalysisResult result,List<DistortionDefinition> catalog){
        if(original==null||result==null||!validator.validate(result).isEmpty()
                ||result.patternCount()!=result.distortions().size())throw invalid();
        var names=catalog.stream().collect(Collectors.toMap(DistortionDefinition::id,DistortionDefinition::name));
        Set<Long> detected=new HashSet<>();Set<String> quotes=new HashSet<>();
        for(var evidence:result.distortions()){
            if(!Objects.equals(names.get(evidence.distortionTagId()),evidence.distortionTagName())
                    ||!original.contains(evidence.evidence())
                    ||!quotes.add(evidence.distortionTagId()+":"+evidence.evidence()))throw invalid();
            detected.add(evidence.distortionTagId());
        }
        for(var card:result.analysisCards()){
            if(card.distortionTagId()==null){if(!"돌아보기".equals(card.tagLabel()))throw invalid();}
            else if(!detected.contains(card.distortionTagId())||!Objects.equals(names.get(card.distortionTagId()),card.tagLabel()))throw invalid();
        }
    }
    private static InvalidLlmOutputException invalid(){return new InvalidLlmOutputException();}
}
