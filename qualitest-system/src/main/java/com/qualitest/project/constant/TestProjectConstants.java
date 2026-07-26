package com.qualitest.project.constant;

/**
 * 测试项目相关常量
 *
 * @author qualitest
 */
public final class TestProjectConstants {

    /**
     * 默认环境名称
     */
    public static final String DEFAULT_ENV_NAME = "默认环境";

    /**
     * 默认环境 URL
     */
    public static final String DEFAULT_ENV_URL_PLACEHOLDER = "http://127.0.0.1";

    /**
     * 空变量条目 JSON（数组，素材库与环境变量共用）
     */
    public static final String EMPTY_VARIABLE_ENTRIES_JSON = "[]";

    /**
     * 空环境变量 JSON
     */
    public static final String EMPTY_ENV_VARIABLES_JSON = EMPTY_VARIABLE_ENTRIES_JSON;

    /**
     * 空素材变量 JSON（数组）
     */
    public static final String EMPTY_ASSET_VARIABLES_JSON = EMPTY_VARIABLE_ENTRIES_JSON;

    /**
     * 环境共享状态：共享
     */
    public static final String ENV_SHARE_STATUS_SHARE = "share";

    /**
     * 环境共享状态：私有
     */
    public static final String ENV_SHARE_STATUS_PRIVATE = "private";

    /**
     * 新建环境默认共享状态
     */
    public static final String DEFAULT_ENV_SHARE_STATUS = ENV_SHARE_STATUS_PRIVATE;

}
