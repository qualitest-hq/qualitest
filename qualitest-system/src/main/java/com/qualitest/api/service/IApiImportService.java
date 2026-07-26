package com.qualitest.api.service;

import com.qualitest.api.params.ApiImportParams;
import com.qualitest.api.result.ApiImportResult;

/**
 * API导入服务接口
 * 用于处理IDEA插件或其他工具的接口信息上传
 *
 * @author qualitest
 */
public interface IApiImportService {

    /**
     * 导入/同步接口信息
     * 默认策略：存在则更新，不存在则新增
     *
     * @param projectId 项目ID
     * @param userId 用户ID
     * @param params 导入参数
     * @return 导入结果
     */
    ApiImportResult importApis(Long projectId, Long userId, ApiImportParams params);
}
