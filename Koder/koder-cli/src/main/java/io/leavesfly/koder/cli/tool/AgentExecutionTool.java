package io.leavesfly.koder.cli.tool;

import io.leavesfly.koder.agent.AgentConfig;
import io.leavesfly.koder.agent.executor.AgentExecutor;
import io.leavesfly.koder.cli.service.AIQueryService;
import io.leavesfly.koder.core.message.AssistantMessage;
import io.leavesfly.koder.core.message.Message;
import io.leavesfly.koder.core.message.UserMessage;


import io.leavesfly.koder.tool.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent执行工具 - SubAgent调用
 * 对标TypeScript版本的TaskTool，用于创建子代理执行任务
 * 
 * 功能：
 * 1. 调用指定类型的Agent执行任务
 * 2. 隔离执行上下文
 * 3. 应用Agent的工具权限和模型配置
 * 4. 返回子代理的执行结果
 * 
 * 使用场景：
 * - 主模型需要委托专门的Agent处理特定任务
 * - 创建并行子任务
 * - 使用不同模型处理不同类型的工作
 */
@Slf4j
@Component
public class AgentExecutionTool extends AbstractTool<AgentExecutionTool.Input, AgentExecutionTool.Output> {

    @Lazy
    @Autowired
    private AgentExecutor agentExecutor;
    
    @Lazy
    @Autowired
    private AIQueryService aiQueryService;

    @Override
    public String getName() {
        return "RunAgent";
    }

    @Override
    public String getDescription() {
        return "启动一个子代理（SubAgent）来处理专门的任务。子代理有自己的工具权限、模型和系统提示词。";
    }

    @Override
    public String getPrompt(boolean safeMode) {
        // 获取可用代理的描述
        String agentDescriptions = agentExecutor.getAgentDescriptions();
        
        return String.format("""
                SubAgent执行工具 - 启动专门的代理处理任务
                
                %s
                
                使用指南：
                - description: 任务的简短描述（3-5个词）
                - prompt: 发送给子代理的完整任务提示
                - agent_type: 要使用的代理类型（从上面的列表中选择）
                - model_name: [可选] 覆盖代理默认模型
                
                何时使用：
                - 需要专门的代理处理特定类型的任务
                - 并行处理多个独立的子任务
                - 使用不同模型处理不同工作
                
                何时不使用：
                - 简单的文件读取（使用FileRead工具）
                - 直接的命令执行（使用Bash工具）
                - 不需要专门配置的通用任务
                """, agentDescriptions);
    }

    @Override
    public Map<String, Object> getInputSchema() {
        // 获取可用的agent类型
        List<String> availableAgents = agentExecutor.listAvailableAgents().stream()
                .map(AgentConfig::getAgentType)
                .toList();
        
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "description", Map.of(
                                "type", "string",
                                "description", "任务的简短描述（3-5个词）"
                        ),
                        "prompt", Map.of(
                                "type", "string",
                                "description", "发送给子代理的完整任务提示"
                        ),
                        "agent_type", Map.of(
                                "type", "string",
                                "description", "要使用的代理类型",
                                "enum", availableAgents
                        ),
                        "model_name", Map.of(
                                "type", "string",
                                "description", "可选：覆盖代理默认模型的模型名称"
                        )
                ),
                "required", List.of("description", "prompt", "agent_type")
        );
    }

    @Override
    public boolean isReadOnly() {
        // SubAgent可能执行写操作
        return false;
    }

    @Override
    public boolean needsPermissions(Input input) {
        // SubAgent工具不需要单独权限（由子代理的工具权限控制）
        return false;
    }

    @Override
    public String renderToolUseMessage(Input input, boolean verbose) {
        if (verbose) {
            return String.format("🤖 启动SubAgent [%s]: %s\n提示: %s",
                    input.agentType,
                    input.description,
                    truncate(input.prompt, 100));
        } else {
            return String.format("🤖 SubAgent [%s]: %s",
                    input.agentType,
                    input.description);
        }
    }

    @Override
    public String renderToolResultMessage(Output output) {
        if (output.success) {
            return String.format("✅ SubAgent完成\n结果: %s",
                    truncate(output.result, 200));
        } else {
            return String.format("❌ SubAgent失败: %s", output.error);
        }
    }

    @Override
    public Flux<ToolResponse<Output>> call(Input input, ToolUseContext context) {
        return Flux.create(sink -> {
            try {
                log.info("启动SubAgent: {} - {}", input.agentType, input.description);
                
                // 1. 准备执行上下文
                List<Message> subMessages = new ArrayList<>();
                subMessages.add(UserMessage.builder()
                        .content(input.prompt)
                        .build());
                
                Optional<AgentExecutor.ExecutionContext> execContext = 
                        agentExecutor.prepareExecution(
                                input.agentType,
                                subMessages,
                                input.prompt
                        );
                
                if (execContext.isEmpty()) {
                    Output output = Output.builder()
                            .success(false)
                            .error("未找到代理: " + input.agentType)
                            .build();
                    sink.next(ToolResponse.result(output));
                    sink.complete();
                    return;
                }
                
                AgentConfig agent = execContext.get().getAgent();
                
                // 2. 构建系统提示词
                String systemPrompt = agentExecutor.buildSystemPrompt(agent);
                
                // 3. 获取可用工具列表
                List<Tool<?, ?>> availableTools = agentExecutor.getAvailableTools(agent);
                log.info("SubAgent {} 可使用 {} 个工具", agent.getAgentType(), availableTools.size());
                
                // 4. 确定使用的模型
                String modelToUse = input.modelName != null ? 
                        input.modelName : 
                        agentExecutor.getModelName(agent).orElse(null);
                
                // 5. 发送进度通知
                sink.next(ToolResponse.progress(String.format(
                        "SubAgent [%s] 启动中...\n模型: %s\n工具数: %d",
                        agent.getAgentType(),
                        modelToUse != null ? modelToUse : "默认",
                        availableTools.size()
                )));
                
                // 6. 执行SubAgent查询
                StringBuilder resultBuilder = new StringBuilder();
                
                // TODO: 这里需要集成AIQueryService的执行逻辑
                // 当前简化实现：直接返回模拟结果
                // 完整实现需要：
                // - 使用指定的模型和系统提示词
                // - 应用工具权限过滤
                // - 流式返回进度
                // - 处理工具调用链
                
                // 模拟执行（实际应该调用AIQueryService）
                String simulatedResult = String.format(
                        "SubAgent [%s] 收到任务：%s\n" +
                        "系统提示词长度：%d 字符\n" +
                        "可用工具：%d 个\n" +
                        "执行模型：%s\n\n" +
                        "注意：当前为模拟执行，完整实现需要集成AIQueryService",
                        agent.getAgentType(),
                        input.description,
                        systemPrompt.length(),
                        availableTools.size(),
                        modelToUse != null ? modelToUse : "默认"
                );
                
                resultBuilder.append(simulatedResult);
                
                // 7. 返回成功结果
                Output output = Output.builder()
                        .success(true)
                        .agentType(agent.getAgentType())
                        .description(input.description)
                        .result(resultBuilder.toString())
                        .toolCount(availableTools.size())
                        .model(modelToUse)
                        .build();
                
                sink.next(ToolResponse.result(output));
                sink.complete();
                
            } catch (Exception e) {
                log.error("SubAgent执行失败", e);
                Output output = Output.builder()
                        .success(false)
                        .error("执行失败: " + e.getMessage())
                        .build();
                sink.next(ToolResponse.result(output));
                sink.complete();
            }
        });
    }
    
    /**
     * 执行SubAgent的完整实现（待集成）
     */
    private Flux<String> executeSubAgent(
            AgentConfig agent,
            String prompt,
            String systemPrompt,
            List<Tool<?, ?>> tools,
            String modelName) {
        
        // TODO: 完整实现
        // 1. 创建子会话
        // 2. 应用工具权限
        // 3. 使用指定模型
        // 4. 流式返回结果
        // 5. 处理工具调用链
        
        return Flux.just("模拟执行结果");
    }

    /**
     * 文本截断
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 输入参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Input {
        /** 任务描述（3-5个词） */
        private String description;
        
        /** 发送给子代理的提示 */
        private String prompt;
        
        /** 代理类型 */
        private String agentType;
        
        /** 可选：覆盖代理默认模型 */
        private String modelName;
    }

    /**
     * 输出结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output {
        /** 是否成功 */
        private boolean success;
        
        /** 代理类型 */
        private String agentType;
        
        /** 任务描述 */
        private String description;
        
        /** 执行结果 */
        private String result;
        
        /** 错误信息 */
        private String error;
        
        /** 使用的工具数量 */
        private Integer toolCount;
        
        /** 使用的模型 */
        private String model;
    }
}
