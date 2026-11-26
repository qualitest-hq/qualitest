package com.qualitest.flow.openhttp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：开放 HTTP 路由冲突判定的调用边界。
 * 边界：生产 main 源码里 OpenHttpRouteCollisionGate.validate 只允许出现在 Registry；其它类须走 Registry。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=OpenHttpRouteRegistryBoundaryTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenHttpRouteRegistryBoundaryTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");

    /**
     * 前提：扫描 qualitest-system 主源码。
     * 期望：OpenHttpRouteCollisionGate.validate( 仅出现在 OpenHttpRouteRegistry（及 CollisionGate 自身定义）。
     */
    @Test
    @Order(1)
    @DisplayName("CollisionGate.validate 生产调用只在 Registry")
    void collisionGateValidateOnlyInRegistry() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(MAIN_JAVA)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        String rel = MAIN_JAVA.relativize(path).toString().replace('\\', '/');
                        if (rel.endsWith("OpenHttpRouteCollisionGate.java")
                                || rel.endsWith("OpenHttpRouteRegistry.java")) {
                            return;
                        }
                        String text;
                        try {
                            text = Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        if (text.contains("OpenHttpRouteCollisionGate.validate")) {
                            offenders.add(rel);
                        }
                    });
        }
        assertTrue(offenders.isEmpty(),
                () -> "生产代码不得直接调用 OpenHttpRouteCollisionGate.validate，违规：" + offenders);
    }
}
