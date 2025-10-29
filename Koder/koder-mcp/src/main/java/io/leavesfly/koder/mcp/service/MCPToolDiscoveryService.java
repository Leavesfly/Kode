package io.leavesfly.koder.mcp.service;

import io.leavesfly.koder.mcp.client.MCPClientManager;
import io.leavesfly.koder.mcp.wrapper.MCPToolWrapper;
import io.leavesfly.koder.tool.Tool;
import io.leavesfly.koder.tool.executor.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * MCP工具发现服务
 * 启动时自动发现并注册MCP工具
 */
@Slf4j
@Service
@Order(10) // 较低优先级，在配置初始化之后
@RequiredArgsConstructor
public class MCPToolDiscoveryService implements CommandLineRunner {

    private final MCPClientManager clientManager;
    private final ToolExecutor toolExecutor;

    @Override
    public void run(String... args) {
        log.info("开始发现MCP工具...");

        // 异步发现并注册MCP工具
        discoverAndRegisterTools()
                .subscribe(
                        count -> log.info("MCP工具发现完成，注册了 {} 个工具", count),
                        error -> log.error("MCP工具发现失败", error)
                );
    }

    /**
     * 发现并注册MCP工具
     */
    public Mono<Integer> discoverAndRegisterTools() {
        return clientManager.listAllTools()
                .map(mcpTools -> {
                    int count = 0;

                    for (var entry : groupToolsByServer(mcpTools).entrySet()) {
                        String serverName = entry.getKey();
                        List<io.leavesfly.koder.mcp.protocol.MCPTool> tools = entry.getValue();

                        for (var mcpTool : tools) {
                            // 创建包装器
                            MCPToolWrapper wrapper = new MCPToolWrapper(
                                    mcpTool,
                                    serverName,
                                    clientManager
                            );

                            // 注册到工具执行器
                            toolExecutor.registerTool(wrapper);

                            log.debug("注册MCP工具: {} (来自服务器: {})",
                                    wrapper.getName(), serverName);

                            count++;
                        }
                    }

                    return count;
                })
                .onErrorResume(error -> {
                    log.warn("发现MCP工具时出错，跳过: {}", error.getMessage());
                    return Mono.just(0);
                });
    }

    /**
     * 按服务器分组工具
     */
    private java.util.Map<String, List<io.leavesfly.koder.mcp.protocol.MCPTool>> groupToolsByServer(
            List<io.leavesfly.koder.mcp.protocol.MCPTool> tools) {
        
        // TODO: 实际应该从工具元数据中提取服务器名称
        // 目前简单返回一个默认分组
        return java.util.Map.of("default", tools);
    }
}
