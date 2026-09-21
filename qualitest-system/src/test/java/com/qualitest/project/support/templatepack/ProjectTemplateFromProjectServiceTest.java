package com.qualitest.project.support.templatepack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualitest.ai.service.IAiPromptTemplateService;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.mapper.TestProjectTemplateMapper;
import com.qualitest.project.params.SaveAsAuthTemplateParams;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectApiService;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.service.ITestProjectService;
import com.qualitest.project.service.ITestProjectTemplateService;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.PackOpResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 ProjectTemplateFromProjectService：从项目另存完整鉴权模板。
 * 边界：流绑定接口强制打包与 apiId remap、素材裁剪、dryRun 不写库、无接口失败。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ProjectTemplateFromProjectServiceTest
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectTemplateFromProjectServiceTest {

    private static final Long PROJECT_ID = 9001L;
    private static final Long LOGIN_API_ID = 101L;
    private static final Long CAPTCHA_API_ID = 102L;
    private static final Long FLOW_ID = 201L;
    private static final String PROFILE_ID = "adminBearer";

    @Mock
    private ITestProjectService testProjectService;
    @Mock
    private ITestProjectApiService testProjectApiService;
    @Mock
    private ITestFlowService testFlowService;
    @Mock
    private ITestProjectEnvService testProjectEnvService;
    @Mock
    private IAiPromptTemplateService aiPromptTemplateService;
    @Mock
    private TestProjectMapper testProjectMapper;
    @Mock
    private ITestProjectTemplateService templateService;
    @Mock
    private TestProjectTemplateMapper templateMapper;

    private ProjectTemplateFromProjectService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ProjectTemplatePackService packService = new ProjectTemplatePackService(
                new ProjectTemplateSlimExpander(),
                new ProjectTemplateFullPackCodec(),
                templateService,
                templateMapper,
                objectMapper);
        service = new ProjectTemplateFromProjectService(
                testProjectService,
                testProjectApiService,
                testFlowService,
                testProjectEnvService,
                aiPromptTemplateService,
                testProjectMapper,
                packService,
                objectMapper);
    }

    @Test
    @Order(1)
    @DisplayName("dryRun：流绑定接口进包且 graph 换成合成 id")
    void save_dryRun_remapsFlowApiIds() throws Exception {
        // 前提：项目有 Profile + 登录流绑 LOGIN_API + adminAuth 素材
        stubProject();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of(
                loginApi(),
                captchaApi()));
        when(testFlowService.selectTestFlowById(FLOW_ID)).thenReturn(loginFlow(LOGIN_API_ID));
        when(testProjectMapper.selectAssetVariablesByTestProjectId(PROJECT_ID)).thenReturn(
                "[{\"id\":1,\"key\":\"adminAuth\",\"assets\":{\"username\":\"admin\",\"password\":\"x\"}}]");
        when(testProjectEnvService.selectTestProjectEnvList(any())).thenReturn(List.of(
                TestProjectEnv.builder()
                        .testProjectEnvId(1L)
                        .testProjectId(PROJECT_ID)
                        .envName("local")
                        .envUrl("http://127.0.0.1:8801")
                        .envVariables("[]")
                        .delStatus(0)
                        .build()));

        SaveAsAuthTemplateParams params = SaveAsAuthTemplateParams.builder()
                .templateName("另存管理端")
                .profileId(PROFILE_ID)
                .flowIds(List.of(FLOW_ID))
                .includeEnv(true)
                .dryRun(true)
                .build();

        PackOpResult result = service.saveAsAuthTemplate(PROJECT_ID, params);

        // 期望：dryRun 无 id；preview 含 1 流 1 素材；图内 apiId 已非项目主键
        assertEquals("full", result.getKind());
        assertNull(result.getTestProjectTemplateId());
        verify(templateService, never()).insertTemplatePack(any());
        Map<String, Object> preview = result.getPreview();
        assertNotNull(preview);
        JsonNode previewNode = objectMapper.valueToTree(preview);
        assertEquals(1, previewNode.get("templateFlows").size());
        JsonNode graphNode = previewNode.get("templateFlows").get(0).get("graphJson");
        assertTrue(previewNode.get("templateApis").size() >= 1);
        String synthLoginApiId = previewNode.get("templateApis").get(0).get("testProjectApiId").asText();
        assertNotEquals(String.valueOf(LOGIN_API_ID), synthLoginApiId);
        String remappedApiId = graphNode.get("nodes").get(0).get("data").get("testProjectApiId").asText();
        assertEquals(synthLoginApiId, remappedApiId);
        assertNotEquals(String.valueOf(LOGIN_API_ID), remappedApiId);
        assertEquals(1, previewNode.get("templateParams").size());
        assertEquals("adminAuth", previewNode.get("templateParams").get(0).get("name").asText());
        @SuppressWarnings("unchecked")
        Map<String, Object> locked = (Map<String, Object>) result.getExpandedSummary().get("locked");
        assertTrue(((List<?>) locked.get("assetKeys")).contains("adminAuth"));
    }

    @Test
    @Order(2)
    @DisplayName("extraApi 可追加；锁定接口不可因未传 extra 而丢失")
    void save_extraApi_mergesWithLocked() throws Exception {
        // 前提：流只绑登录口；extra 追加验证码
        stubProject();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of(loginApi(), captchaApi()));
        when(testFlowService.selectTestFlowById(FLOW_ID)).thenReturn(loginFlow(LOGIN_API_ID));
        when(testProjectMapper.selectAssetVariablesByTestProjectId(PROJECT_ID)).thenReturn("[]");

        PackOpResult result = service.saveAsAuthTemplate(PROJECT_ID, SaveAsAuthTemplateParams.builder()
                .templateName("带验证码")
                .profileId(PROFILE_ID)
                .flowIds(List.of(FLOW_ID))
                .extraApiIds(List.of(CAPTCHA_API_ID))
                .includeEnv(false)
                .dryRun(true)
                .build());

        // 期望：templateApis 含登录与验证码两条
        JsonNode apis = objectMapper.valueToTree(result.getPreview()).get("templateApis");
        assertEquals(2, apis.size());
        String joined = apis.toString();
        assertTrue(joined.contains("/login"));
        assertTrue(joined.contains("/captchaImage"));
    }

    @Test
    @Order(3)
    @DisplayName("无流且无可用接口时失败")
    void save_noApis_fails() {
        // 前提：空 Profile apis、无流、项目无接口
        when(testProjectService.selectTestProjectById(PROJECT_ID)).thenReturn(TestProject.builder()
                .testProjectId(PROJECT_ID)
                .authConfig("{\"authProfiles\":[{\"id\":\"" + PROFILE_ID + "\",\"name\":\"管理端\",\"apis\":[]}]}")
                .delStatus(0)
                .build());
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());

        assertThrows(ServiceException.class, () -> service.saveAsAuthTemplate(PROJECT_ID,
                SaveAsAuthTemplateParams.builder()
                        .templateName("空")
                        .profileId(PROFILE_ID)
                        .dryRun(true)
                        .build()));
    }

    @Test
    @Order(4)
    @DisplayName("非 dryRun 会 insert 自定义模板")
    void save_write_insertsTemplate() {
        // 前提：dryRun=false；同名不存在；走可写流的 insertTemplatePack
        stubProject();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of(loginApi()));
        when(testFlowService.selectTestFlowById(FLOW_ID)).thenReturn(loginFlow(LOGIN_API_ID));
        when(testProjectMapper.selectAssetVariablesByTestProjectId(PROJECT_ID)).thenReturn(
                "[{\"id\":1,\"key\":\"adminAuth\",\"assets\":{\"username\":\"a\"}}]");
        when(templateMapper.selectTestProjectTemplateList(any())).thenReturn(List.of());
        when(templateService.insertTemplatePack(any())).thenAnswer(inv -> {
            TestProjectTemplate entity = inv.getArgument(0);
            entity.setTestProjectTemplateId(555L);
            return 1;
        });

        PackOpResult result = service.saveAsAuthTemplate(PROJECT_ID, SaveAsAuthTemplateParams.builder()
                .templateName("写入模板")
                .profileId(PROFILE_ID)
                .flowIds(List.of(FLOW_ID))
                .includeEnv(false)
                .dryRun(false)
                .build());

        // 期望：返回新模板 id；落库实体带登录流（另存允许写 flows）
        assertEquals(555L, result.getTestProjectTemplateId());
        ArgumentCaptor<TestProjectTemplate> captor = ArgumentCaptor.forClass(TestProjectTemplate.class);
        verify(templateService).insertTemplatePack(captor.capture());
        assertEquals("写入模板", captor.getValue().getTemplateName());
        assertEquals(Integer.valueOf(0), captor.getValue().getBuiltinStatus());
        assertTrue(captor.getValue().getTemplateFlows().contains("登录"));
    }

    private void stubProject() {
        String auth = """
                {"authProfiles":[{
                  "id":"%s",
                  "name":"管理端 Bearer",
                  "match":{"pathPrefix":["/system/"]},
                  "headerValueTemplate":"Bearer {{asset.adminAuth.token}}",
                  "apis":[{"apiName":"登录","apiPath":"/login","requestConfig":{"method":"POST"}}]
                }]}
                """.formatted(PROFILE_ID);
        when(testProjectService.selectTestProjectById(PROJECT_ID)).thenReturn(TestProject.builder()
                .testProjectId(PROJECT_ID)
                .authConfig(auth)
                .delStatus(0)
                .build());
    }

    private static TestProjectApi loginApi() {
        return TestProjectApi.builder()
                .testProjectApiId(LOGIN_API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("登录")
                .apiPath("/login")
                .requestConfig("{\"method\":\"POST\",\"body\":{\"mode\":\"json\",\"json\":{\"example\":{\"username\":\"{{asset.adminAuth.username}}\"}}}}")
                .authConfig("{\"mode\":\"none\"}")
                .responseConfig("{}")
                .headers("{}")
                .cookies("{}")
                .syncProtected(1)
                .delStatus(0)
                .build();
    }

    private static TestProjectApi captchaApi() {
        return TestProjectApi.builder()
                .testProjectApiId(CAPTCHA_API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("验证码")
                .apiPath("/captchaImage")
                .requestConfig("{\"method\":\"GET\"}")
                .authConfig("{\"mode\":\"none\"}")
                .responseConfig("{}")
                .headers("{}")
                .cookies("{}")
                .delStatus(0)
                .build();
    }

    private static TestFlow loginFlow(Long apiId) {
        String graph = """
                {"nodes":[{"id":"h1","type":"http","data":{
                  "callMode":"project",
                  "testProjectApiId":"%d",
                  "httpMethod":"POST",
                  "apiPath":"/login",
                  "extracts":[{"scope":"asset","entryKey":"adminAuth","field":"token","expr":"$.token"}]
                }}],"edges":[]}
                """.formatted(apiId);
        return TestFlow.builder()
                .testFlowId(FLOW_ID)
                .testProjectId(PROJECT_ID)
                .flowName("登录")
                .graphJson(graph)
                .delStatus(0)
                .build();
    }
}
