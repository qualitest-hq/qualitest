package com.qualitest.project.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 接口详情附带的托管鉴权头。
 * 调试台据此展示只读的 Authorization 行；发送前再把值模板换成素材库里的实际值。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedAuthHeader implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 头名，如 Authorization */
    private String name;

    /** 值模板，如 Bearer {{asset.clientAuth.token}} */
    private String valueTemplate;

    /** 命中的项目鉴权配置 id；接口自定义头时为空 */
    private String authProfileId;

    /** 鉴权配置显示名，如「客户端 Bearer」 */
    private String profileName;

    /**
     * 来源。
     * profile：继承项目鉴权配置；override：接口自定义头。
     */
    private String source;
}
