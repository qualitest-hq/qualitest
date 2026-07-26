package com.qualitest.flow.validate;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 开始节点唯一性校验结果。
 * <p>
 * 开始节点定义为无入边的节点；流程要求恰好一个。
 */
@Getter
public class StartNodesValidation {

    private final boolean ok;

    /** 当前无入边节点 id 列表 */
    private final List<String> ids;

    /** ok 为 false 时的错误说明；ok 时为 empty */
    private final String message;

    public StartNodesValidation(boolean ok, List<String> ids, String message) {
        this.ok = ok;
        this.ids = Collections.unmodifiableList(ids);
        this.message = message == null ? "" : message;
    }
}
