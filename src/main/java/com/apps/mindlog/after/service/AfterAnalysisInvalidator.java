package com.apps.mindlog.after.service;

import com.apps.mindlog.after.entity.*;
import com.apps.mindlog.after.repository.*;
import com.apps.mindlog.ai.job.*;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import org.springframework.stereotype.Service;

@Service
public class AfterAnalysisInvalidator {
    private final AfterAiFeedbackRepository feedbacks;
    private final AfterDistortionRepository distortions;
    private final AfterAnalysisCardRepository cards;
    private final AiInsightRepository insights;
    private final JobStore jobs;
    public AfterAnalysisInvalidator(AfterAiFeedbackRepository feedbacks,AfterDistortionRepository distortions,
            AfterAnalysisCardRepository cards,AiInsightRepository insights,JobStore jobs){
        this.feedbacks=feedbacks;this.distortions=distortions;this.cards=cards;this.insights=insights;this.jobs=jobs;
    }
    public void requireNoRunningAnalysis(long id){
        if(jobs.hasActive(id,JobKind.AFTER_ANALYSIS))throw new ApiException(ErrorType.ANALYSIS_IN_PROGRESS);
    }
    /** Caller holds account and After locks in the existing transaction. */
    public void invalidate(AfterLog after){
        var feedback=feedbacks.findByAfterLogId(after.getId()).orElseGet(()->new AfterAiFeedback(after.getId(),after.getInputVersion()));
        feedback.invalidate(after.getInputVersion());feedbacks.save(feedback);
        distortions.deleteByAfterLogId(after.getId());cards.deleteByAfterLogId(after.getId());insights.deleteByAfterLogId(after.getId());
        jobs.invalidateAfter(after.getId(),after.getInputVersion());
    }
}
