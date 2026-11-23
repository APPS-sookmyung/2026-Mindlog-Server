package com.apps.mindlog.after.dto.response;

import com.apps.mindlog.after.entity.RecordStatus;
import com.apps.mindlog.ai.job.JobStatus;
import com.apps.mindlog.global.validation.VisibleText;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

public record FinalDiaryResponse(long id,String finalDiary,int expectedScore,int actualScore,int scoreDiff,
        RecordStatus recordStatus,long inputVersion,Comparison comparison,JobStatus insightStatus,OffsetDateTime finalizedAt){
    public record Comparison(String direction,int changeAmount,String message,String beforeComment,String afterComment){
        private static final Pattern LINE_BREAK = Pattern.compile("\\R");

        private static String firstLine(String source) {
            if (source == null) return null;
            return LINE_BREAK.splitAsStream(source).map(VisibleText::strip)
                    .filter(line -> !line.isEmpty()).findFirst().orElse(null);
        }

        public static Comparison of(int expected,int actual,String nickname,String worstScenario,String freeWriting){
            int diff=actual-expected;int amount=Math.abs(diff);
            String direction=diff<0?"DOWN":diff>0?"UP":"SAME";
            String message=diff<0?"비포 기록과 비교했을 때, 불안 수치가 "+amount+"점 낮아졌어요! 잘하고 있어요"
                    +(nickname==null||nickname.isBlank()?"!":" "+nickname+"님!"):
                    diff>0?"비포 기록과 비교했을 때, 불안 수치가 "+amount+"점 높아졌어요.":
                    "비포 기록과 비교했을 때, 불안 수치가 같아요.";
            return new Comparison(direction,amount,message,firstLine(worstScenario),firstLine(freeWriting));
        }
    }
}
