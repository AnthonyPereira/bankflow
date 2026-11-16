package com.bankflow.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AnalyticsFraudServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
