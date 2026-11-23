package com.apps.mindlog.ai.common;

import com.apps.mindlog.after.dto.response.AnalysisResult;
import com.apps.mindlog.ai.after.DiaryDraftResult;
import com.apps.mindlog.ai.after.RecordInsightWorker;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class RecordJsonSchemaTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void analysisSchemaRequiresFieldsAndAllowsOnlyTheNullableCopingCardId() {
        var schema = json.valueToTree(RecordJsonSchema.forRecord(AnalysisResult.class));
        assertThat(schema.path("additionalProperties").booleanValue()).isFalse();
        assertThat(schema.path("required").toString()).contains("summary", "patternCount", "distortions", "analysisCards");
        assertThat(schema.path("properties").path("summary").path("type").asText()).isEqualTo("string");
        var card = schema.path("properties").path("analysisCards").path("items");
        assertThat(card.path("additionalProperties").booleanValue()).isFalse();
        assertThat(card.path("properties").path("distortionTagId").path("type").toString()).isEqualTo("[\"integer\",\"null\"]");
        assertThat(card.path("properties").path("tagLabel").path("type").asText()).isEqualTo("string");
    }

    @Test void bothOtherAfterResultRecordsAreSupportedWithoutProviderDependencies() {
        for (Class<?> type : new Class<?>[]{DiaryDraftResult.class, RecordInsightWorker.Reflection.class}) {
            var schema = json.valueToTree(RecordJsonSchema.forRecord(type));
            assertThat(schema.path("type").asText()).isEqualTo("object");
            assertThat(schema.path("required").size()).isEqualTo(1);
        }
        assertThatIllegalArgumentException().isThrownBy(() -> RecordJsonSchema.forRecord(String.class));
    }
}
