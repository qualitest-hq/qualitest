package com.qualitest.flow.snapshot;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测 HttpEndpointSnapshotAdapter：对 /test-support 的 snapshot / restore HTTP 契约。
 * 边界：MockWebServer，不连真实环境。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=HttpEndpointSnapshotAdapterTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpEndpointSnapshotAdapterTest {

    private MockWebServer server;
    private HttpEndpointSnapshotAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        adapter = new HttpEndpointSnapshotAdapter();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    /**
     * 前提：服务端返回 ready 快照；请求 scope=tables、表 mall_order。
     * 期望：解析 snapshotId；POST 路径与 body 字段正确。
     */
    @Test
    @Order(1)
    @DisplayName("snapshot POST 契约并解析响应")
    void snapshot_postsContractAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"snapshotId\":\"snap-abc\",\"createdAt\":\"2026-07-01T00:00:00Z\","
                        + "\"scope\":\"tables\",\"status\":\"ready\"}"));

        String base = server.url("/test-support").toString().replaceAll("/$", "");
        SnapshotRef ref = adapter.snapshot(SnapshotRequest.builder()
                .resetEndpointBase(base)
                .scope("tables")
                .tables(List.of("mall_order"))
                .label("run-1:node-1")
                .meta(Map.of("env", "test"))
                .timeoutMs(5_000L)
                .build());

        assertEquals("snap-abc", ref.getSnapshotId());
        assertEquals("ready", ref.getStatus());

        RecordedRequest request = server.takeRequest();
        assertEquals("/test-support/snapshot", request.getPath());
        assertEquals("POST", request.getMethod());
        String body = request.getBody().readUtf8();
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"scope\":\"tables\""));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("mall_order"));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("run-1:node-1"));
    }

    /**
     * 前提：连续两次 restore 均返回 restored。
     * 期望：两次均不抛错，服务端收到 2 次请求。
     */
    @Test
    @Order(2)
    @DisplayName("restore 幂等且不抛错")
    void restore_postsSnapshotId_andIsIdempotent() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"snapshotId\":\"snap-abc\",\"status\":\"restored\"}"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"snapshotId\":\"snap-abc\",\"status\":\"restored\"}"));

        String base = server.url("/test-support").toString().replaceAll("/$", "");
        assertDoesNotThrow(() -> adapter.restore(base, "snap-abc", 5_000L));
        assertDoesNotThrow(() -> adapter.restore(base, "snap-abc", 5_000L));
        assertEquals(2, server.getRequestCount());
    }
}
