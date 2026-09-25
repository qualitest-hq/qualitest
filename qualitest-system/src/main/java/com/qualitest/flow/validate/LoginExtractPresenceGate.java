package com.qualitest.flow.validate;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.project.domain.TestProjectApi;

import java.util.List;
import java.util.function.Function;

/**
 * 设计期门禁入口：登录相关 extracts 一致性校验。
 * <p>
 * 不再按声明登录口硬拦缺 extract（缺了由人/AI 后补）；
 * 仅保留「已有 extracts 写出托管头目标」时的键碰撞等校验。
 */
public final class LoginExtractPresenceGate {

    private LoginExtractPresenceGate() {}

    /**
     * @param graph           待检查画布
     * @param projectAuthJson 项目鉴权配置，可空
     * @param apiResolver     按 testProjectApiId 取接口；返回 null 则跳过该节点
     * @return 错误文案列表；空表示通过
     */
    public static List<String> validate(
            GraphJson graph,
            String projectAuthJson,
            Function<Long, TestProjectApi> apiResolver) {
        return LoginFlowKeyCollisionGate.validate(graph, projectAuthJson, apiResolver);
    }
}
