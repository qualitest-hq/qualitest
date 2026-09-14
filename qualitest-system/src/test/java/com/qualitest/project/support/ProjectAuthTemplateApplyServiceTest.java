package com.qualitest.project.support;

import com.qualitest.ai.domain.AiPromptTemplate;
import com.qualitest.ai.service.IAiPromptTemplateService;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.domain.TestProjectApiGroup;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectApiGroupService;
import com.qualitest.project.service.ITestProjectApiService;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.service.ITestProjectService;
import com.qualitest.project.service.ITestProjectTemplateService;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProjectAuthTemplateApplyService：勾选模板写入项目鉴权 Profile，并种子接口 / 测值 / 测试流 / 环境。
 * 覆盖：同名 Profile 跳过、method+path 去重、库内已有不覆盖、字段拷贝、瘦 JSON 补缺省；
 * 预制环境占位 URL→8801、已定制不覆盖、无环境行跳过（建项须先建默认环境再 Apply）。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ProjectAuthTemplateApplyServiceTest}
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectAuthTemplateApplyServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long TPL_ADMIN = 2100000000000000004L;
    private static final Long TPL_DEFAULT = 2100000000000000001L;

    @Mock
    private ITestProjectTemplateService templateService;
    @Mock
    private TestProjectMapper testProjectMapper;
    @Mock
    private ITestProjectApiService testProjectApiService;
    @Mock
    private ITestProjectApiGroupService testProjectApiGroupService;
    @Mock
    private ITestProjectService testProjectService;
    @Mock
    private ITestFlowService testFlowService;
    @Mock
    private ITestProjectEnvService testProjectEnvService;
    @Mock
    private IAiPromptTemplateService aiPromptTemplateService;

    private ProjectAuthTemplateApplyService service;

    @BeforeEach
    void setUp() {
        service = new ProjectAuthTemplateApplyService(
                templateService,
                testProjectMapper,
                testProjectApiService,
                testProjectApiGroupService,
                testProjectService,
                testFlowService,
                testProjectEnvService,
                aiPromptTemplateService);
    }

    /**
     * 前提：项目已有同名 Profile。
     * 期望：整份跳过；不种子接口。
     */
    @Test
    @Order(1)
    @DisplayName("同名 Profile 整份跳过")
    void apply_skipsSameNameProfile() {
        TestProject project = new TestProject();
        project.setTestProjectId(PROJECT_ID);
        project.setAuthConfig(ProjectAuthConfigSupport.toJson(
                ProjectAuthConfigSupport.ruoyiBearerTemplate()));
        when(testProjectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(project);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", loginAndCaptchaApis()));

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProject> update = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateTestProject(update.capture());
        ProjectAuthConfig stored = ProjectAuthConfigSupport.parse(update.getValue().getAuthConfig());
        assertEquals(1, stored.getAuthProfiles().size());
        assertEquals("RuoYi Bearer", stored.getAuthProfiles().get(0).getName());
        verify(testProjectApiService, never()).batchInsertTestProjectApi(any());
    }

    /**
     * 前提：先勾管理端（POST /login），再勾 RuoYi Bearer（同 path 的登录 + captcha）。
     * 期望：两条 Profile；后勾登录口跳过；只种子 login + captcha。
     */
    @Test
    @Order(2)
    @DisplayName("method+path 跨条去重")
    void apply_dedupsMethodAndPathAcrossProfiles() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(templateService.selectTestProjectTemplateById(TPL_ADMIN)).thenReturn(
                template(TPL_ADMIN, "管理端 Bearer", loginOnlyApis()));
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", loginAndCaptchaApis()));

        service.apply(PROJECT_ID, List.of(TPL_ADMIN, TPL_DEFAULT));

        ArgumentCaptor<TestProject> update = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateTestProject(update.capture());
        ProjectAuthConfig stored = ProjectAuthConfigSupport.parse(update.getValue().getAuthConfig());
        assertEquals(2, stored.getAuthProfiles().size());
        assertEquals("管理端 Bearer", stored.getAuthProfiles().get(0).getName());
        assertEquals(1, stored.getAuthProfiles().get(0).getApis().size());
        assertEquals("/login", stored.getAuthProfiles().get(0).getApis().get(0).getApiPath());
        assertEquals(1, stored.getAuthProfiles().get(1).getApis().size());
        assertEquals("/captchaImage", stored.getAuthProfiles().get(1).getApis().get(0).getApiPath());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TestProjectApi>> inserts = ArgumentCaptor.forClass(List.class);
        verify(testProjectApiService).batchInsertTestProjectApi(inserts.capture());
        Set<String> paths = inserts.getValue().stream()
                .map(TestProjectApi::getApiPath)
                .collect(Collectors.toSet());
        assertEquals(Set.of("/login", "/captchaImage"), paths);
    }

    /**
     * 前提：库内已有 POST /login。
     * 期望：Profile 仍写入；不 insert 已有接口。
     */
    @Test
    @Order(3)
    @DisplayName("库内已有 method+path 不覆盖")
    void apply_skipsExistingDbApi() {
        stubEmptyProject();
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", loginOnlyApis()));
        TestProjectApi existing = TestProjectApi.builder()
                .apiPath("/login")
                .requestConfig("{\"method\":\"POST\"}")
                .delStatus(0)
                .build();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of(existing));

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProject> update = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateTestProject(update.capture());
        assertEquals(1, ProjectAuthConfigSupport.parse(update.getValue().getAuthConfig())
                .getAuthProfiles().size());
        verify(testProjectApiService, never()).batchInsertTestProjectApi(any());
    }

    /**
     * 前提：模板预制口带描述、头、响应、测值、脚本等同名字段。
     * 期望：insert 行这些列与模板一致，而不是写死空 JSON。
     */
    @Test
    @Order(4)
    @DisplayName("全字段预制口按同名字段 insert")
    void apply_copiesFullPrefabFields() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", fullLoginPrefabApis()));

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TestProjectApi>> inserts = ArgumentCaptor.forClass(List.class);
        verify(testProjectApiService).batchInsertTestProjectApi(inserts.capture());
        assertEquals(1, inserts.getValue().size());
        TestProjectApi row = inserts.getValue().get(0);
        assertEquals("登录", row.getApiName());
        assertEquals("/login", row.getApiPath());
        assertEquals("管理端登录", row.getApiDescription());
        assertTrue(row.getHeaders().contains("X-Client"));
        assertTrue(row.getCookies().contains("sid"));
        assertTrue(row.getResponseConfig().contains("\"ok\""));
        assertTrue(row.getTestValueConfig().contains("admin"));
        assertTrue(row.getBizCodeConfig().contains("200"));
        assertEquals("pre()", row.getPreRequestScript());
        assertEquals("post()", row.getPostRequestScript());
        assertTrue(row.getDesignHints().contains("$.token"));
        assertTrue(row.getAuthConfig().contains("\"none\""));
        assertFalse(row.getAuthConfig().contains("loginHint"));
    }

    /**
     * 前提：内置瘦 JSON（无 headers/response/描述/脚本）。
     * 期望：headers/cookies 为 {}，responseConfig 为空 responses，可选列为空。
     */
    @Test
    @Order(5)
    @DisplayName("瘦 JSON 种子填缺省列")
    void apply_fillsDefaultJsonColumnsForSlimPrefab() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis()));

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TestProjectApi>> inserts = ArgumentCaptor.forClass(List.class);
        verify(testProjectApiService).batchInsertTestProjectApi(inserts.capture());
        TestProjectApi row = inserts.getValue().get(0);
        assertEquals("{}", row.getHeaders());
        assertEquals("{}", row.getCookies());
        assertEquals("{\"configVersion\":1,\"responses\":[]}", row.getResponseConfig());
        assertNull(row.getApiDescription());
        assertNull(row.getTestValueConfig());
        assertNull(row.getBizCodeConfig());
        assertNull(row.getPreRequestScript());
        assertNull(row.getPostRequestScript());
    }

    /**
     * 模板登录口无 flows 时写弱默认 asset 头。
     * 期望：Profile 有 Bearer {{asset.adminAuth.token}}；接口行只写 mode=none；测值在 testValueConfig。
     */
    @Test
    @Order(6)
    @DisplayName("种子：无 flows 时弱默认 asset 头，接口行只有 none")
    void apply_seedsLoginModeNoneDefaultAssetHeader() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", loginOnlyApis()));

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProject> update = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateTestProject(update.capture());
        String authConfigJson = update.getValue().getAuthConfig();
        assertFalse(authConfigJson.contains("loginHint"));
        ProjectAuthConfig stored = ProjectAuthConfigSupport.parse(authConfigJson);
        assertEquals("Bearer {{asset.adminAuth.token}}",
                stored.getAuthProfiles().get(0).getHeaderValueTemplate());
        assertNull(stored.getAuthProfiles().get(0).getCredentialApi());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TestProjectApi>> inserts = ArgumentCaptor.forClass(List.class);
        verify(testProjectApiService).batchInsertTestProjectApi(inserts.capture());
        String authJson = inserts.getValue().get(0).getAuthConfig();
        assertTrue(authJson.contains("\"none\""));
        assertFalse(authJson.contains("loginHint"));
        assertTrue(inserts.getValue().get(0).getTestValueConfig().contains("admin"));
        assertTrue(inserts.getValue().get(0).getTestValueConfig().contains("token"));
        assertFalse(inserts.getValue().get(0).getResponseConfig().contains("\"example\""));
    }

    /**
     * 前提：模板带 flows 登录流。
     * 期望：从 extracts 派生 asset 头；插入同名测试流一次。
     */
    @Test
    @Order(7)
    @DisplayName("种子：flows 派生 asset 头并插入登录流")
    void apply_seedsFlowAndDerivesAssetHeader() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(testFlowService.selectTestFlowList(any())).thenReturn(List.of());
        when(testFlowService.insertTestFlow(any())).thenReturn(1);
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "RuoYi Bearer 登录", "POST", "/login", "adminAuth", "token", "body", "$.token");
        TestProjectTemplate tpl = template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis());
        tpl.setTemplateFlows(flows);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(tpl);

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProject> update = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateTestProject(update.capture());
        String authConfigJson = update.getValue().getAuthConfig();
        assertFalse(authConfigJson.contains("loginHint"));
        ProjectAuthConfig stored = ProjectAuthConfigSupport.parse(authConfigJson);
        assertEquals("Bearer {{asset.adminAuth.token}}",
                stored.getAuthProfiles().get(0).getHeaderValueTemplate());
        assertEquals("/login", stored.getAuthProfiles().get(0).getCredentialApi().getPath());

        ArgumentCaptor<com.qualitest.project.domain.TestFlow> flowCap =
                ArgumentCaptor.forClass(com.qualitest.project.domain.TestFlow.class);
        verify(testFlowService).insertTestFlow(flowCap.capture());
        assertEquals("RuoYi Bearer 登录", flowCap.getValue().getFlowName());
        assertTrue(flowCap.getValue().getGraphJson().contains("$.token"));
        assertTrue(flowCap.getValue().getGraphJson().contains("adminAuth"));
        assertTrue(flowCap.getValue().getGraphJson().contains("probe_http")
                || flowCap.getValue().getGraphJson().contains("statusCheck"));
    }

    /**
     * 前提：项目 auth_config 为空（新建勾选模板后先插入再 apply）。
     * 期望：能写入 Profile，不因不可变空列表抛 UnsupportedOperationException。
     */
    @Test
    @Order(8)
    @DisplayName("空 auth_config 可追加 Profile")
    void apply_blankAuthConfig_addsProfile() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis()));

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProject> update = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateTestProject(update.capture());
        ProjectAuthConfig stored = ProjectAuthConfigSupport.parse(update.getValue().getAuthConfig());
        assertEquals(1, stored.getAuthProfiles().size());
        assertEquals("RuoYi Bearer", stored.getAuthProfiles().get(0).getName());
    }

    /**
     * 前提：项目已有同名 Profile；模板带两条预制提示词，库中尚无项目芯片。
     * 期望：Profile 跳过仍种子提示词；同 title 再 Apply 不重复插入。
     */
    @Test
    @Order(9)
    @DisplayName("Profile 同名跳过仍种子提示词且按 title 去重")
    void apply_seedsPromptsEvenWhenProfileSkipped() {
        TestProject project = new TestProject();
        project.setTestProjectId(PROJECT_ID);
        project.setAuthConfig(ProjectAuthConfigSupport.toJson(
                ProjectAuthConfigSupport.ruoyiBearerTemplate()));
        when(testProjectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(project);
        when(aiPromptTemplateService.selectAiPromptTemplateResultList(any())).thenReturn(List.of());
        when(aiPromptTemplateService.insertAiPromptTemplate(any())).thenReturn(1);
        TestProjectTemplate tpl = template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis());
        tpl.setTemplatePrompts("""
                [{"title":"S01 购物车","content":"已挂子流，勿再登录。查车。","sessionScene":"test_flow_design","sortNum":1},
                 {"title":"S01 购物车","content":"重复标题应跳过","sessionScene":"test_flow_design","sortNum":2}]
                """);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(tpl);

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<AiPromptTemplate> insert = ArgumentCaptor.forClass(AiPromptTemplate.class);
        verify(aiPromptTemplateService, times(1)).insertAiPromptTemplate(insert.capture());
        AiPromptTemplate row = insert.getValue();
        assertEquals("project", row.getTemplateScope());
        assertEquals(PROJECT_ID, row.getTestProjectId());
        assertEquals("S01 购物车", row.getTemplateTitle());
        assertEquals("test_flow_design", row.getSessionScene());
        assertTrue(row.getTemplateContent().contains("勿再登录"));
        verify(testProjectApiService, never()).batchInsertTestProjectApi(any());
    }

    /**
     * 前提：空项目；模板带 adminAuth 口令。
     * 期望：素材库写入 adminAuth。
     */
    @Test
    @Order(10)
    @DisplayName("种子：templateParams asset 写入素材库")
    void apply_seedsAssetParams() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(testProjectMapper.selectAssetVariablesByTestProjectId(PROJECT_ID)).thenReturn("[]");
        TestProjectTemplate tpl = template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis());
        tpl.setTemplateParams(
                "[{\"kind\":\"asset\",\"name\":\"adminAuth\",\"value\":{\"username\":\"admin\",\"password\":\"admin123\"}}]");
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(tpl);

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProject> patch = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateAssetVariables(patch.capture());
        assertTrue(patch.getValue().getAssetVariables().contains("adminAuth"));
        assertTrue(patch.getValue().getAssetVariables().contains("admin123"));
    }

    /**
     * 前提：项目已有同名 Profile；模板带 asset 口令，素材库为空。
     * 期望：不插接口；仍把 adminAuth 写入素材库。
     */
    @Test
    @Order(11)
    @DisplayName("Profile 同名跳过仍种子 asset 口令")
    void apply_seedsAssetParamsEvenWhenProfileSkipped() {
        TestProject project = new TestProject();
        project.setTestProjectId(PROJECT_ID);
        project.setAuthConfig(ProjectAuthConfigSupport.toJson(
                ProjectAuthConfigSupport.ruoyiBearerTemplate()));
        when(testProjectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(project);
        when(testProjectMapper.selectAssetVariablesByTestProjectId(PROJECT_ID)).thenReturn("[]");
        TestProjectTemplate tpl = template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis());
        tpl.setTemplateParams(
                "[{\"kind\":\"asset\",\"name\":\"adminAuth\",\"value\":{\"username\":\"admin\",\"password\":\"admin123\"}}]");
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(tpl);

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProject> patch = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateAssetVariables(patch.capture());
        assertTrue(patch.getValue().getAssetVariables().contains("adminAuth"));
        assertTrue(patch.getValue().getAssetVariables().contains("admin123"));
        verify(testProjectApiService, never()).batchInsertTestProjectApi(any());
    }

    /**
     * 前提：建项占位 URL；模板预制环境 8801。
     * 期望：第一条环境 envUrl 写成 localhost:8801。
     */
    @Test
    @Order(12)
    @DisplayName("预制环境：占位 URL 写成 8801")
    void apply_seedsEnvUrlWhenPlaceholder() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(testProjectEnvService.selectTestProjectEnvList(any())).thenReturn(List.of(placeholderEnv()));
        TestProjectTemplate tpl = template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis());
        tpl.setTemplateEnvs(demoEnvsJson());
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(tpl);

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProjectEnv> patch = ArgumentCaptor.forClass(TestProjectEnv.class);
        verify(testProjectEnvService).updateTestProjectEnv(patch.capture());
        assertEquals("http://localhost:8801", patch.getValue().getEnvUrl());
        assertNull(patch.getValue().getAllowDestructiveReset());
    }

    /**
     * 前提：环境 URL 已定制；模板再给 8801 与同 key 变量。
     * 期望：URL 不改；timeout 不覆盖；补新 key。
     */
    @Test
    @Order(13)
    @DisplayName("预制环境：已定制 URL 不覆盖，变量同 key 不覆盖")
    void apply_keepsCustomEnvUrlAndMergesVars() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        TestProjectEnv env = TestProjectEnv.builder()
                .testProjectEnvId(10L)
                .envName("默认环境")
                .envUrl("http://custom:9")
                .envVariables("[{\"key\":\"timeout\",\"assets\":{\"timeout\":1000}}]")
                .build();
        when(testProjectEnvService.selectTestProjectEnvList(any())).thenReturn(List.of(env));
        TestProjectTemplate tpl = template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis());
        tpl.setTemplateEnvs(
                "[{\"envName\":\"默认环境\",\"envUrl\":\"http://localhost:8801\","
                        + "\"envVariables\":[{\"key\":\"timeout\",\"assets\":{\"timeout\":9999}},"
                        + "{\"key\":\"region\",\"assets\":{\"region\":\"cn\"}}]}]");
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(tpl);

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProjectEnv> patch = ArgumentCaptor.forClass(TestProjectEnv.class);
        verify(testProjectEnvService).updateTestProjectEnv(patch.capture());
        assertNull(patch.getValue().getEnvUrl());
        assertTrue(patch.getValue().getEnvVariables().contains("timeout"));
        assertTrue(patch.getValue().getEnvVariables().contains("1000"));
        assertTrue(patch.getValue().getEnvVariables().contains("region"));
        assertFalse(patch.getValue().getEnvVariables().contains("9999"));
    }

    /**
     * 前提：两套模板都带 8801；第二套多一个变量。
     * 期望：URL 只在占位时写一次；变量按 key 去重合并。
     */
    @Test
    @Order(14)
    @DisplayName("双模板共用第一条环境：URL 一次、变量去重")
    void apply_twoTemplatesShareFirstEnv() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        TestProjectEnv afterFirst = TestProjectEnv.builder()
                .testProjectEnvId(10L)
                .envName("默认环境")
                .envUrl("http://localhost:8801")
                .envVariables("[{\"key\":\"timeout\",\"assets\":{\"timeout\":\"5000\"}}]")
                .build();
        when(testProjectEnvService.selectTestProjectEnvList(any()))
                .thenReturn(List.of(placeholderEnv()))
                .thenReturn(List.of(afterFirst));
        TestProjectTemplate admin = template(TPL_ADMIN, "管理端 Bearer", slimLoginApis());
        admin.setTemplateEnvs(
                "[{\"envName\":\"默认环境\",\"envUrl\":\"http://localhost:8801\","
                        + "\"envVariables\":[{\"key\":\"timeout\",\"assets\":{\"timeout\":\"5000\"}}]}]");
        TestProjectTemplate ruoyi = template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis());
        ruoyi.setTemplateEnvs(
                "[{\"envName\":\"默认环境\",\"envUrl\":\"http://localhost:8801\","
                        + "\"envVariables\":[{\"key\":\"timeout\",\"assets\":{\"timeout\":\"9999\"}},"
                        + "{\"key\":\"region\",\"assets\":{\"region\":\"cn\"}}]}]");
        when(templateService.selectTestProjectTemplateById(TPL_ADMIN)).thenReturn(admin);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(ruoyi);

        service.apply(PROJECT_ID, List.of(TPL_ADMIN, TPL_DEFAULT));

        ArgumentCaptor<TestProjectEnv> patch = ArgumentCaptor.forClass(TestProjectEnv.class);
        verify(testProjectEnvService, times(2)).updateTestProjectEnv(patch.capture());
        List<TestProjectEnv> patches = patch.getAllValues();
        assertEquals("http://localhost:8801", patches.get(0).getEnvUrl());
        assertNull(patches.get(1).getEnvUrl());
        assertTrue(patches.get(1).getEnvVariables().contains("region"));
        assertFalse(patches.get(1).getEnvVariables().contains("9999"));
    }

    /**
     * 前提：项目已有同名 Profile；环境仍是占位。
     * 期望：不插接口；仍把 URL 写成 8801。
     */
    @Test
    @Order(15)
    @DisplayName("Profile 同名跳过仍补环境 URL")
    void apply_seedsEnvEvenWhenProfileSkipped() {
        TestProject project = new TestProject();
        project.setTestProjectId(PROJECT_ID);
        project.setAuthConfig(ProjectAuthConfigSupport.toJson(
                ProjectAuthConfigSupport.ruoyiBearerTemplate()));
        when(testProjectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(project);
        when(testProjectEnvService.selectTestProjectEnvList(any())).thenReturn(List.of(placeholderEnv()));
        TestProjectTemplate tpl = template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis());
        tpl.setTemplateEnvs(demoEnvsJson());
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(tpl);

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProjectEnv> patch = ArgumentCaptor.forClass(TestProjectEnv.class);
        verify(testProjectEnvService).updateTestProjectEnv(patch.capture());
        assertEquals("http://localhost:8801", patch.getValue().getEnvUrl());
        verify(testProjectApiService, never()).batchInsertTestProjectApi(any());
    }

    /**
     * 前提：环境 URL 已定制；templateEnvs 再给 URL。
     * 期望：不改 URL，且名称/变量无变化时不写库。
     */
    @Test
    @Order(16)
    @DisplayName("预制环境：已定制 URL 不覆盖")
    void apply_prefabEnvSkipsCustomUrl() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        TestProjectEnv env = TestProjectEnv.builder()
                .testProjectEnvId(10L)
                .envName("默认环境")
                .envUrl("http://custom:9")
                .envVariables("[]")
                .build();
        when(testProjectEnvService.selectTestProjectEnvList(any())).thenReturn(List.of(env));
        TestProjectTemplate tpl = template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis());
        tpl.setTemplateEnvs(
                "[{\"envName\":\"默认环境\",\"envUrl\":\"http://localhost:8801\",\"envVariables\":[]}]");
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(tpl);

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        verify(testProjectEnvService, never()).updateTestProjectEnv(any());
    }

    /**
     * 前提：项目尚无环境行（建项若先 Apply 再加成员会出现）；模板带 8801。
     * 期望：不 update 环境（契约：须先建默认环境再 Apply，否则预制 URL 写不进）。
     */
    @Test
    @Order(17)
    @DisplayName("预制环境：无环境行则跳过（建项须先建环境再 Apply）")
    void apply_skipsEnvSeedWhenNoProjectEnv() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(testProjectEnvService.selectTestProjectEnvList(any())).thenReturn(List.of());
        TestProjectTemplate tpl = template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis());
        tpl.setTemplateEnvs(demoEnvsJson());
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(tpl);

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        verify(testProjectEnvService, never()).updateTestProjectEnv(any());
    }

    private void stubEmptyProject() {
        TestProject project = new TestProject();
        project.setTestProjectId(PROJECT_ID);
        project.setAuthConfig(null);
        when(testProjectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(project);
    }

    private void stubGroupCreate() {
        when(testProjectApiGroupService.selectTestProjectApiGroupList(any())).thenReturn(List.of());
        when(testProjectApiGroupService.insertTestProjectApiGroup(any())).thenAnswer(invocation -> {
            TestProjectApiGroup group = invocation.getArgument(0);
            group.setApiGroupId(100L);
            return 1;
        });
    }

    private static String demoEnvsJson() {
        return "[{\"envName\":\"默认环境\",\"envUrl\":\"http://localhost:8801\",\"envVariables\":[]}]";
    }

    private static TestProjectEnv placeholderEnv() {
        return TestProjectEnv.builder()
                .testProjectEnvId(10L)
                .testProjectId(PROJECT_ID)
                .envName("默认环境")
                .envUrl("http://127.0.0.1")
                .envVariables("[]")
                .build();
    }

    private static TestProjectTemplate template(Long id, String name, String apis) {
        return TestProjectTemplate.builder()
                .testProjectTemplateId(id)
                .templateName(name)
                .templateApis(apis)
                .enableStatus(1)
                .delStatus(0)
                .build();
    }

    private static String slimLoginApis() {
        return "[{\"apiName\":\"登录\",\"apiPath\":\"/login\",\"apiGroup\":\"登录\","
                + "\"protocolType\":\"http\",\"apiStatus\":\"normal\","
                + "\"requestConfig\":{\"configVersion\":1,\"method\":\"POST\"},"
                + "\"authConfig\":{\"mode\":\"none\"}}]";
    }

    private static String loginOnlyApis() {
        return "[{\"apiName\":\"登录\",\"apiPath\":\"/login\",\"apiGroup\":\"登录\","
                + "\"protocolType\":\"http\",\"apiStatus\":\"normal\","
                + "\"requestConfig\":{\"configVersion\":1,\"method\":\"POST\"},"
                + "\"testValueConfig\":{\"request\":{\"bodyExample\":{\"username\":\"admin\",\"password\":\"admin123\"}},"
                + "\"response\":{\"examplesById\":{\"ok\":{\"code\":200,\"token\":\"...\"}}}},"
                + "\"responseConfig\":{\"configVersion\":1,\"responses\":[{\"id\":\"ok\",\"httpStatus\":200}]},"
                + "\"authConfig\":{\"mode\":\"none\"}}]";
    }

    private static String loginAndCaptchaApis() {
        return "[{\"apiName\":\"登录\",\"apiPath\":\"/login\",\"apiGroup\":\"登录\","
                + "\"protocolType\":\"http\",\"apiStatus\":\"normal\","
                + "\"requestConfig\":{\"configVersion\":1,\"method\":\"POST\"},"
                + "\"authConfig\":{\"mode\":\"none\"}},"
                + "{\"apiName\":\"验证码\",\"apiPath\":\"/captchaImage\",\"apiGroup\":\"登录\","
                + "\"protocolType\":\"http\",\"apiStatus\":\"normal\","
                + "\"requestConfig\":{\"configVersion\":1,\"method\":\"GET\"},"
                + "\"authConfig\":{\"mode\":\"none\"}}]";
    }

    private static String fullLoginPrefabApis() {
        return """
                [{"apiName":"登录","apiPath":"/login","apiGroup":"管理端.系统.登录",
                  "protocolType":"http","apiStatus":"normal",
                  "apiDescription":"管理端登录",
                  "requestConfig":{"configVersion":1,"method":"POST"},
                  "headers":{"X-Client":"qualitest"},
                  "cookies":{"sid":"1"},
                  "responseConfig":{"configVersion":1,"responses":[{"id":"ok"}]},
                  "testValueConfig":{"bodyExample":{"username":"admin"}},
                  "bizCodeConfig":{"successValues":[200]},
                  "authConfig":{"mode":"none"},
                  "designHints":{"hints":["token 在 $.token"]},
                  "preRequestScript":"pre()","postRequestScript":"post()"}]
                """;
    }
}
