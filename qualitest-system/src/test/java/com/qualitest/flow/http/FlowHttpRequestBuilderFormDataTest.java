package com.qualitest.flow.http;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.common.config.QualitestConfig;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowExecutionException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 FlowHttpRequestBuilder 对 form-data / binary 的组装。
 * 边界：本地临时文件与 profile 下上传路径；无网络。
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
                .env(Map.of("baseUrl", "http://localhost:8801"))
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
     * 前提：qualitest.profile 指向临时目录，其下有 upload/.../cover.png；
     * form-data 的 file 字段 value 为 /profile/upload/.../cover.png。
     * 期望：能读到该文件并组装为 multipart 文件 part（base64 内容正确）。
     */
    @Test
    @Order(3)
    @DisplayName("form-data：/profile/upload 路径可读")
    void build_formData_profileUploadPath() throws Exception {
        QualitestConfig cfg = new QualitestConfig();
        cfg.setProfile(tempDir.toAbsolutePath().toString());

        Path relative = Path.of("upload", "2026", "08", "04", "cover.png");
        Path png = tempDir.resolve(relative);
        Files.createDirectories(png.getParent());
        byte[] bytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        Files.write(png, bytes);
        String profilePath = "/profile/" + relative.toString().replace('\\', '/');

        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(4L)
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
                              {"name":"file","type":"file","value":"%s","_enabled":true}
                            ]
                          }
                        }
                        """.formatted(profilePath))
                .build();
        TestProjectApi effective = TestProjectApiEffectiveConfigResolver.resolve(api).toApiView(api);

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "4");

        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost:8801"))
                .build();

        FlowHttpRequestBuilder.BuiltHttpRequest built =
                FlowHttpRequestBuilder.buildFromProject(ctx, effective, nodeData);

        DebugHttpForwardParams.DebugBodySpec body = built.getForwardParams().getBody();
        assertNotNull(body);
        assertEquals("formData", body.getKind());
        assertEquals(1, body.getFiles().size());
        DebugHttpForwardParams.DebugBodySpec.FormFile file = body.getFiles().get(0);
        assertEquals("file", file.getName());
        assertEquals("cover.png", file.getFileName());
        assertEquals(Base64.getEncoder().encodeToString(bytes), file.getBase64());
    }

    /**
     * 前提：跑流上下文 asset.cover.storagePath 指向 /profile/upload/... 下的真实文件；
     * form-data file 字段 value 为 {{asset.cover.storagePath}}。
     * 期望：占位符解析后读盘，组装 multipart 文件 part。
     */
    @Test
    @Order(4)
    @DisplayName("form-data：asset.storagePath 占位符")
    void build_formData_assetStoragePathPlaceholder() throws Exception {
        QualitestConfig cfg = new QualitestConfig();
        cfg.setProfile(tempDir.toAbsolutePath().toString());

        Path relative = Path.of("upload", "2026", "08", "04", "avatar.png");
        Path png = tempDir.resolve(relative);
        Files.createDirectories(png.getParent());
        byte[] bytes = "png-bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(png, bytes);
        String profilePath = "/profile/" + relative.toString().replace('\\', '/');

        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(5L)
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
                              {"name":"file","type":"file","value":"{{asset.cover.storagePath}}","_enabled":true}
                            ]
                          }
                        }
                        """)
                .build();
        TestProjectApi effective = TestProjectApiEffectiveConfigResolver.resolve(api).toApiView(api);

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "5");

        Map<String, Object> cover = new HashMap<>();
        cover.put("type", "file");
        cover.put("fileName", "avatar.png");
        cover.put("storagePath", profilePath);

        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost:8801"))
                .asset(Map.of("cover", cover))
                .build();

        FlowHttpRequestBuilder.BuiltHttpRequest built =
                FlowHttpRequestBuilder.buildFromProject(ctx, effective, nodeData);

        DebugHttpForwardParams.DebugBodySpec body = built.getForwardParams().getBody();
        assertNotNull(body);
        assertEquals(1, body.getFiles().size());
        assertEquals(Base64.getEncoder().encodeToString(bytes), body.getFiles().get(0).getBase64());
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
                .env(Map.of("baseUrl", "http://localhost:8801"))
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

    /**
     * 前提：API body.mode=form。
     * 期望：组装抛步骤失败，错误信息含 body.mode 与 form。
     */
    @Test
    @Order(5)
    @DisplayName("未知 mode=form 显式失败")
    void build_unknownModeForm_throws() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(6L)
                .testProjectId(1L)
                .apiPath("/api/login/login")
                .requestConfig("""
                        {
                          "configVersion":1,
                          "method":"POST",
                          "queryParams":[],
                          "pathParams":[],
                          "declaredHeaders":[],
                          "body":{
                            "mode":"form",
                            "form":{"schema":{"type":"object"}}
                          }
                        }
                        """)
                .build();
        TestProjectApi effective = TestProjectApiEffectiveConfigResolver.resolve(api).toApiView(api);

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "6");

        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost:8801"))
                .build();

        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> FlowHttpRequestBuilder.buildFromProject(ctx, effective, nodeData));
        assertTrue(ex.getMessage().contains("body.mode"));
        assertTrue(ex.getMessage().contains("form"));
    }

    /**
     * 前提：标准 urlencoded + bodyExample 叠出行 value。
     * 期望：发出 urlencoded fields 含 phone/code 解析后的值。
     */
    @Test
    @Order(6)
    @DisplayName("urlencoded：bodyExample 叠层后发出字段")
    void build_urlencoded_sendsBodyExampleFields() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(7L)
                .testProjectId(1L)
                .apiPath("/api/login/login")
                .requestConfig("""
                        {
                          "configVersion":1,
                          "method":"POST",
                          "queryParams":[],
                          "pathParams":[],
                          "declaredHeaders":[],
                          "body":{
                            "mode":"x-www-form-urlencoded",
                            "urlencoded":[
                              {"name":"phone","example":""},
                              {"name":"code","example":""}
                            ]
                          }
                        }
                        """)
                .testValueConfig("""
                        {"request":{"bodyExample":{"phone":"{{asset.clientAuth.phone}}","code":"{{asset.clientAuth.code}}"}}}
                        """)
                .build();
        TestProjectApi effective = TestProjectApiEffectiveConfigResolver.resolve(api).toApiView(api);

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "7");

        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost:8801"))
                .asset(Map.of("clientAuth", Map.of("phone", "13800000001", "code", "123456")))
                .build();

        FlowHttpRequestBuilder.BuiltHttpRequest built =
                FlowHttpRequestBuilder.buildFromProject(ctx, effective, nodeData);

        DebugHttpForwardParams.DebugBodySpec body = built.getForwardParams().getBody();
        assertNotNull(body);
        assertEquals("urlencoded", body.getKind());
        assertEquals(2, body.getFields().size());
        assertTrue(body.getFields().stream().anyMatch(f -> "phone".equals(f.get(0)) && "13800000001".equals(f.get(1))));
        assertTrue(body.getFields().stream().anyMatch(f -> "code".equals(f.get(0)) && "123456".equals(f.get(1))));
    }
}
