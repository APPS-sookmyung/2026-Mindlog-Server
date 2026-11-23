package com.apps.mindlog.ai.after;

import com.apps.mindlog.ai.common.LlmClient;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods=false)
public class WorkerTestClient {
    @Bean FakeClient fakeClient(){return new FakeClient();}
    public static class FakeClient implements LlmClient {
        public final AtomicInteger calls=new AtomicInteger();
        public Function<Request<?>,Object> answer;
        public String modelVersion(){return "test-model";}
        public <T>T generate(Request<T> request){calls.incrementAndGet();return request.resultType().cast(answer.apply(request));}
        public void reset(){calls.set(0);answer=r->{throw new IllegalStateException("test provider unavailable");};}
    }
}
