package com.qualitest.project.service.impl;

import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.mapper.TestProjectTemplateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 覆盖模板写库时对 templateFlows 的两种策略：
 * - 管理端 CRUD：新增清空流、修改保留库内流
 * - 包导入/另存：允许写入或覆盖流
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProjectTemplateServiceImplTest {

    private static final Long TPL_ID = 1001L;

    @Mock
    private TestProjectTemplateMapper mapper;

    @InjectMocks
    private TestProjectTemplateServiceImpl service;

    /**
     * 前提：管理端新增时请求体里带了非空 flows。
     * 期望：真正 insert 的实体 templateFlows 为 []。
     */
    @Test
    @Order(1)
    @DisplayName("CRUD 新增：强制 templateFlows 为空数组")
    void insertCrud_forcesEmptyFlows() {
        when(mapper.countByTemplateName(any(), isNull())).thenReturn(0);
        when(mapper.insertTestProjectTemplate(any())).thenReturn(1);

        TestProjectTemplate entity = baseEntity();
        entity.setTemplateFlows("[{\"flowName\":\"登录\"}]");

        service.insertTestProjectTemplate(entity);

        ArgumentCaptor<TestProjectTemplate> captor = ArgumentCaptor.forClass(TestProjectTemplate.class);
        verify(mapper).insertTestProjectTemplate(captor.capture());
        assertEquals("[]", captor.getValue().getTemplateFlows());
    }

    /**
     * 前提：库内已有登录流；管理端修改请求试图改成空流并改名。
     * 期望：名称更新成功，flows 仍是库里的登录流。
     */
    @Test
    @Order(2)
    @DisplayName("CRUD 修改：忽略客户端 flows，保留库内原值")
    void updateCrud_preservesDbFlows() {
        TestProjectTemplate existing = baseEntity();
        existing.setTestProjectTemplateId(TPL_ID);
        existing.setTemplateFlows("[{\"flowName\":\"登录\"}]");
        existing.setBuiltinStatus(0);
        existing.setDelStatus(0);
        when(mapper.selectTestProjectTemplateById(TPL_ID)).thenReturn(existing);
        when(mapper.countByTemplateName(any(), any())).thenReturn(0);
        when(mapper.updateTestProjectTemplate(any())).thenReturn(1);

        TestProjectTemplate patch = baseEntity();
        patch.setTestProjectTemplateId(TPL_ID);
        patch.setTemplateFlows("[]");
        patch.setTemplateName("改名模板");

        service.updateTestProjectTemplate(patch);

        ArgumentCaptor<TestProjectTemplate> captor = ArgumentCaptor.forClass(TestProjectTemplate.class);
        verify(mapper).updateTestProjectTemplate(captor.capture());
        assertTrue(captor.getValue().getTemplateFlows().contains("登录"));
        assertEquals("改名模板", captor.getValue().getTemplateName());
    }

    /**
     * 前提：另存/完整包新增时实体带登录流。
     * 期望：insert 保留该流。
     */
    @Test
    @Order(3)
    @DisplayName("完整包新增：允许写入 templateFlows")
    void insertPack_keepsFlows() {
        when(mapper.countByTemplateName(any(), isNull())).thenReturn(0);
        when(mapper.insertTestProjectTemplate(any())).thenReturn(1);

        TestProjectTemplate entity = baseEntity();
        entity.setTemplateFlows("[{\"flowName\":\"登录\"}]");

        service.insertTemplatePack(entity);

        ArgumentCaptor<TestProjectTemplate> captor = ArgumentCaptor.forClass(TestProjectTemplate.class);
        verify(mapper).insertTestProjectTemplate(captor.capture());
        assertTrue(captor.getValue().getTemplateFlows().contains("登录"));
    }

    /**
     * 前提：同名覆盖导入，包里带新的登录流。
     * 期望：update 采用包里的新流。
     */
    @Test
    @Order(4)
    @DisplayName("完整包覆盖：允许更新 templateFlows")
    void updatePack_allowsFlows() {
        TestProjectTemplate existing = baseEntity();
        existing.setTestProjectTemplateId(TPL_ID);
        existing.setTemplateFlows("[]");
        existing.setBuiltinStatus(0);
        existing.setDelStatus(0);
        when(mapper.selectTestProjectTemplateById(TPL_ID)).thenReturn(existing);
        when(mapper.countByTemplateName(any(), any())).thenReturn(0);
        when(mapper.updateTestProjectTemplate(any())).thenReturn(1);

        TestProjectTemplate pack = baseEntity();
        pack.setTestProjectTemplateId(TPL_ID);
        pack.setTemplateFlows("[{\"flowName\":\"新登录\"}]");

        service.updateTemplatePack(pack);

        ArgumentCaptor<TestProjectTemplate> captor = ArgumentCaptor.forClass(TestProjectTemplate.class);
        verify(mapper).updateTestProjectTemplate(captor.capture());
        assertTrue(captor.getValue().getTemplateFlows().contains("新登录"));
    }

    /** 最小可写模板实体（含必填预制接口） */
    private static TestProjectTemplate baseEntity() {
        return TestProjectTemplate.builder()
                .templateName("单测模板")
                .templateApis("[{\"apiName\":\"登录\",\"apiPath\":\"/login\",\"requestConfig\":{\"method\":\"POST\"}}]")
                .templateParams("[]")
                .templateEnvs("[]")
                .templatePrompts("[]")
                .enableStatus(1)
                .sortNum(0)
                .delStatus(0)
                .build();
    }
}
