package com.apps.mindlog.ai.after;
import com.apps.mindlog.after.repository.RecordInsightHistoryQuery.History;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RecordInsightTextTest {
    @Test void comparisonHandlesIncreaseDecreaseAndEqualIncludingZero(){
        assertThat(RecordInsightWorker.comparison(0,0)).contains("같아요");
        assertThat(RecordInsightWorker.comparison(20,40)).contains("20점 높았어요");
        assertThat(RecordInsightWorker.comparison(40,0)).contains("40점 낮았어요");
    }
    @Test void historyRoundsToOneDecimalAndDoesNotClaimReductionWhenActualWasHigher(){
        assertThat(RecordInsightWorker.pattern(new History(3,new BigDecimal("-12.345")))).contains("12.3점 낮았어요");
        assertThat(RecordInsightWorker.pattern(new History(2,BigDecimal.ZERO))).contains("평균 차이는 0점");
        assertThat(RecordInsightWorker.pattern(new History(0,null))).contains("아직 없어요");
    }
}
