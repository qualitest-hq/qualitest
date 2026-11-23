package com.qualitest.plugin.manifest;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用说明书 configSchema 校验环境绑定 config（普通 JSON，非 JSONC）。
 */
@Component
public class PluginConfigSchemaValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    /**
     * 无 schema 时跳过；有 schema 时校验失败抛 CONFIG_SCHEMA_INVALID。
     */
    public void validate(Map<String, Object> configSchema, Object config) {
        if (configSchema == null || configSchema.isEmpty()) {
            return;
        }
        try {
            JsonNode schemaNode = objectMapper.readTree(JSON.toJSONString(configSchema));
            JsonNode dataNode = objectMapper.readTree(JSON.toJSONString(config == null ? Map.of() : config));
            JsonSchema schema = factory.getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(dataNode);
            if (errors != null && !errors.isEmpty()) {
                String detail = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining("; "));
                throw new PluginException(PluginErrorCode.CONFIG_SCHEMA_INVALID, "环境配置不符合说明书: " + detail);
            }
        } catch (PluginException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginException(PluginErrorCode.CONFIG_SCHEMA_INVALID, "环境配置校验失败", e);
        }
    }
}
