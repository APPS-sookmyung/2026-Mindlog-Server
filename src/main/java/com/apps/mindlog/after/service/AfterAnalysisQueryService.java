package com.apps.mindlog.after.service;

import com.apps.mindlog.after.dto.response.*;
import com.apps.mindlog.after.entity.*;
import com.apps.mindlog.after.repository.*;
import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.reference.service.ReferenceQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class AfterAnalysisQueryService {
    private final AfterAccess access;
    private final AfterLogRepository logs;
    private final AfterAiFeedbackRepository feedbacks;
    private final AfterDistortionRepository distortions;
    private final AfterAnalysisCardRepository cards;
    private final ReferenceQueryService reference;
    public AfterAnalysisQueryService(AfterAccess access,AfterLogRepository logs,AfterAiFeedbackRepository feedbacks,
            AfterDistortionRepository distortions,AfterAnalysisCardRepository cards,ReferenceQueryService reference){
        this.access=access;this.logs=logs;this.feedbacks=feedbacks;this.distortions=distortions;this.cards=cards;this.reference=reference;
    }
    public LatestAnalysisResponse latest(long id){
        var account=access.current();var after=logs.findById(id).orElseThrow(AfterAccess::notFound);
        access.beforePath(after.getBeforeLogId(),account.id());
        var found=feedbacks.findByAfterLogId(id);
        if(found.isEmpty())return new LatestAnalysisResponse(AnalysisStatus.NOT_REQUESTED,after.getInputVersion(),null,null);
        var feedback=found.get();
        var status=feedback.getInputVersion()==after.getInputVersion()?feedback.getAnalysisStatus():AnalysisStatus.INVALIDATED;
        if(status!=AnalysisStatus.COMPLETED)return new LatestAnalysisResponse(status,after.getInputVersion(),null,null);
        var evidence=distortions.findByAfterLogIdOrderByIdAsc(id);
        var names=reference.distortionNames(evidence.stream().map(AfterDistortion::getDistortionTagId).toList());
        var result=new AnalysisResult(feedback.getSummary(),evidence.size(),evidence.stream().map(value->new AnalysisResult.Distortion(
                value.getDistortionTagId(),names.get(value.getDistortionTagId()),value.getEvidence(),value.getExplanation())).toList(),
                cards.findByAfterLogIdOrderByDisplayOrderAscIdAsc(id).stream().filter(value->value.getInputVersion()==after.getInputVersion())
                        .map(value->new AnalysisResult.Card(value.getTagLabel(),value.getDistortionTagId(),value.getTitle(),value.getContent())).toList());
        return new LatestAnalysisResponse(status,after.getInputVersion(),feedback.getCompletedAt()==null?null:
                feedback.getCompletedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime(),result);
    }
}
