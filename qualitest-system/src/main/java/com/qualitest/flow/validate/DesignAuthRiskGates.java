package com.qualitest.flow.validate;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.project.domain.TestProjectApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 鉴权与 HTTP 必填风险检查。
 * <p>
 * 依次检查：托管 Bearer 是否有来源、登录口是否抽出所需凭证、成功路径 HTTP 是否缺必填测值。
 * 返回带 CODE 前缀的错误文案列表；空列表表示通过。
 */
public final class DesignAuthRiskGates {

    private DesignAuthRiskGates() {
    }

    /**
     * 汇总鉴权与必填错误文案。
     *
     * @param graph           待检查的图；null 则返回空列表
     * @param projectAuthJson 项目鉴权配置 JSON；null 时部分检查会跳过
     * @param apiResolver     按接口 id 查项目接口；null 当作查不到
     * @param subflowResolver 按子流 id 加载子流图；null 表示不展开子流
     * @return 错误文案列表，每条通常以 AUTH_* 或业务码开头
     */
    public static List<String> collect(
            GraphJson graph,
            String projectAuthJson,
            Function<Long, TestProjectApi> apiResolver,
            Function<Long, GraphJson> subflowResolver
    ) {
        List<String> errors = new ArrayList<>();
        if (graph == null) {
            return errors;
        }
        Function<Long, TestProjectApi> apis = apiResolver != null ? apiResolver : id -> null;
        errors.addAll(AuthTokenPresenceGate.validate(graph, projectAuthJson, apis, subflowResolver));
        errors.addAll(LoginExtractPresenceGate.validate(graph, projectAuthJson, apis));
        errors.addAll(HttpRequiredParamGate.validate(graph, apis));
        return errors;
    }
}
