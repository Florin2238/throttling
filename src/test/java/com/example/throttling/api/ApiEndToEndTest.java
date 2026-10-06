package com.example.throttling.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end tests: start the Spring application (with the in-memory store) and call the
 * endpoints like a real client, through the whole chain of authentication, rate limiter and
 * controller.
 *
 * <p>The limits are fixed in the test properties so that the tests do not depend on
 * {@code application.yaml}. The application context is recreated after every test, so counters
 * always start from zero.
 */
@SpringBootTest(properties = {
        "throttling.store.type=memory",
        "throttling.clients.client-1.limit=5",
        "throttling.clients.client-1.window-seconds=10",
        "throttling.clients.client-2.limit=3",
        "throttling.clients.client-2.window-seconds=10"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApiEndToEndTest {

    private static final String SUCCESS_BODY = "{\"success\":true}";
    private static final String RATE_LIMIT_BODY = "{\"error\":\"rate limit exceeded\"}";
    private static final String UNAUTHORIZED_BODY = "{\"error\":\"unauthorized\"}";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private ResultActions call(String path, String clientId) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + clientId));
    }

    private int statusOf(String path, String clientId) throws Exception {
        return call(path, clientId).andReturn().getResponse().getStatus();
    }

    @Test
    void missingAuthorizationHeader_returns401() throws Exception {
        mvc.perform(get("/foo"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(UNAUTHORIZED_BODY));
        mvc.perform(get("/bar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownClient_returns401() throws Exception {
        call("/foo", "nobody")
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(UNAUTHORIZED_BODY));
    }

    @Test
    void foo_tokenBucket_allowsLimitThenReturns429() throws Exception {
        for (int i = 0; i < 5; i++) {
            call("/foo", "client-1")
                    .andExpect(status().isOk())
                    .andExpect(content().string(SUCCESS_BODY));
        }
        call("/foo", "client-1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().string(RATE_LIMIT_BODY));
    }

    @Test
    void bar_slidingWindow_allowsLimitThenReturns429() throws Exception {
        for (int i = 0; i < 5; i++) {
            call("/bar", "client-1")
                    .andExpect(status().isOk())
                    .andExpect(content().string(SUCCESS_BODY));
        }
        call("/bar", "client-1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().string(RATE_LIMIT_BODY));
    }

    @Test
    void clientsHaveDifferentLimits() throws Exception {
        // client-2 has a limit of 3.
        for (int i = 0; i < 3; i++) {
            assertEquals(200, statusOf("/bar", "client-2"));
        }
        assertEquals(429, statusOf("/bar", "client-2"));

        // client-1 (limit 5) is not affected by what client-2 used.
        for (int i = 0; i < 5; i++) {
            assertEquals(200, statusOf("/bar", "client-1"));
        }
        assertEquals(429, statusOf("/bar", "client-1"));
    }

    @Test
    void endpointsHaveSeparateCounters() throws Exception {
        for (int i = 0; i < 5; i++) {
            call("/foo", "client-1");
        }
        assertEquals(429, statusOf("/foo", "client-1"));

        // /foo is exhausted for client-1, but /bar has its own counter.
        assertEquals(200, statusOf("/bar", "client-1"));
    }
}