package com.apps.mindlog.after;
import com.apps.mindlog.after.dto.response.FinalDiaryResponse.Comparison;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FinalDiaryComparisonTest {
    @Test void increaseAndEqualityUseActualNumbers(){
        var up=Comparison.of(19,82,"예선",null,null);
        assertThat(up.direction()).isEqualTo("UP");assertThat(up.changeAmount()).isEqualTo(63);
        assertThat(up.message()).contains("63점 높아졌어요").doesNotContain("실패");
        var same=Comparison.of(52,52,"예선",null,null);
        assertThat(same.direction()).isEqualTo("SAME");assertThat(same.changeAmount()).isZero();
        assertThat(same.message()).contains("같아요");
    }
}
