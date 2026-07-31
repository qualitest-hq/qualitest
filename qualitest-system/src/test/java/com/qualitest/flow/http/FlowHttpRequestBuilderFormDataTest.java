package com.qualitest.flow.http;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 FlowHttpRequestBuilder 对 form-data / binary 的组装。
 * 边界：本地临时文件；无网络；不依赖 QualitestConfig.profile。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowHttpRequestBuilderFormDataTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowHttpRequestBuilderFormDataTest {

    @TempDir
    Path tempDir;

    /**
     * 前提：API body.mode=form-data，含 string 字段与 type=file（绝对路径）。
     * 期望：DebugBodySpec.kind=formData，fields 含 bizType，files 含可读 base64。
     */
    @Test
    @Order(1)
    @DisplayName("form-data：文本字段 + 本地文件")
    void build_formData_textAndFile() throws Exception {
        Path png = tempDir.resolve("sample-cover.png");
        byte[] bytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        Files.write(png, bytes);
        String abs = png.toAbsolutePath().toString().replace('\\', '/');

        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(2L)
                .testProjectId(1L)
                .apiPath("/api/file/upload")
                .requestConfig("""
                        {
                          "configVersion":1,
                          "method":"POST",
                          "queryParams":[],
                          "pathParams":[],
                          "declaredHeaders":[],
                          "body":{
                            "mode":"form-data",
                            "formData":[
                              {"name":"bizType","type":"string","value":"product-image","_enabled":true},
                              {"name":"file","type":"file","value":"%s","_enabled":true}
                            ]
                          }
                        }
                        """.formatted(abs))
                .build();
        TestProjectApi effective = TestProjectApiEffectiveConfigResolver.resolve(api).toApiView(api);

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "2");

        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost:8081"))
                .build();

        FlowHttpRequestBuilder.BuiltHttpRequest built =
                FlowHttpRequestBuilder.buildFromProject(ctx, effective, nodeData);

        assertEquals("POST", built.getMethod());
        DebugHttpForwardParams.DebugBodySpec body = built.getForwardParams().getBody();
        assertNotNull(body);
        assertEquals("formData", body.getKind());
        assertTrue(body.getFields().stream().anyMatch(f -> "bizType".equals(f.get(0)) && "product-image".equals(f.get(1))));
        assertEquals(1, body.getFiles().size());
        DebugHttpForwardParams.DebugBodySpec.FormFile file = body.getFiles().get(0);
        assertEquals("file", file.getName());
        assertEquals("sample-cover.png", file.getFileName());
        assertEquals("image/png", file.getContentType());
        assertEquals(Base64.getEncoder().encodeToString(bytes), file.getBase64());
    }

    /**
     * 前提：API body.mode=binary，value 指向临时文件。
     * 期望：kind=binary，raw 为文件 base64，Content-Type 按扩展名。
     */
    @Test
    @Order(2)
    @DisplayName("binary：按路径读文件")
    void build_binary_readsFile() throws Exception {
        Path bin = tempDir.resolve("payload.bin");
        byte[] bytes = "hello-binary".getBytes(StandardCharsets.UTF_8);
        Files.write(bin, bytes);
        String abs = bin.toAbsolutePath().toString().replace('\\', '/');

        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(3L)
                .testProjectId(1L)
                .apiPath("/api/raw")
                .requestConfig("""
                        {
                          "configVersion":1,
                          "method":"POST",
                          "queryParams":[],
                          "pathParams":[],
                          "declaredHeaders":[],
                          "body":{
                            "mode":"binary",
                            "binary":{"type":"file","value":"%s"}
                          }
                        }
                        """.formatted(abs))
                .build();
        TestProjectApi effective = TestProjectApiEffectiveConfigResolver.resolve(api).toApiView(api);

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "3");

        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost:8081"))
                .build();

        FlowHttpRequestBuilder.BuiltHttpRequest built =
                FlowHttpRequestBuilder.buildFromProject(ctx, effective, nodeData);

        DebugHttpForwardParams.DebugBodySpec body = built.getForwardParams().getBody();
        assertNotNull(body);
        assertEquals("binary", body.getKind());
        assertEquals("payload.bin", body.getFileName());
        assertEquals(Base64.getEncoder().encodeToString(bytes), body.getRaw());
        List<DebugHttpForwardParams.HeaderPair> headers = built.getForwardParams().getHeaders();
        assertTrue(headers.stream().anyMatch(h ->
                "Content-Type".equalsIgnoreCase(h.getName())
                        && "application/octet-stream".equals(h.getValue())));
    }
}
