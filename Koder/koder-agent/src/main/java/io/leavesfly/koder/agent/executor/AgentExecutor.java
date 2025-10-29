package io.leavesfly.koder.agent.executor;

import io.leavesfly.koder.agent.AgentConfig;
import io.leavesfly.koder.agent.AgentRegistry;
import io.leavesfly.koder.core.message.Message;
import io.leavesfly.koder.tool.Tool;
import io.leavesfly.koder.tool.executor.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 代理执行器
 * 负责代理的执行和工具权限控制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutor {

    private final AgentRegistry agentRegistry;
    private final ToolExecutor toolExecutor;

    /**
     * 执行上下文
     */
    public static class ExecutionContext {
        private final AgentConfig agent;
        private final List<Message> messages;
        private final String userInput;

        public ExecutionContext(AgentConfig agent, List<Message> messages, String userInput) {
            this.agent = agent;
            this.messages = messages;
            this.userInput = userInput;
        }

        public AgentConfig getAgent() {
            return agent;
        }

        public List<Message> getMessages() {
            return messages;
        }

        public String getUserInput() {
            return userInput;
        }
    }

    /**
     * 准备代理执行上下文
     */
    public Optional<ExecutionContext> prepareExecution(
            String agentType,
            List<Message> messages,
            String userInput) {

        // 获取代理配置
        Optional<AgentConfig> agentOpt = agentRegistry.getAgentByType(agentType);
        if (agentOpt.isEmpty()) {
            log.error("未找到代理: {}", agentType);
            return Optional.empty();
        }

        AgentConfig agent = agentOpt.get();
        log.info("准备执行代理: {} ({})", agent.getAgentType(), agent.getLocation());

        return Optional.of(new ExecutionContext(agent, messages, userInput));
    }

    /**
     * 获取代理允许使用的工具列表
     */
    public List<Tool<?, ?>> getAvailableTools(AgentConfig agent) {
        List<Tool<?, ?>> allTools = toolExecutor.getAllTools();

        // 如果代理允许所有工具
        if (agent.allowsAllTools()) {
            log.debug("代理 {} 允许使用所有 {} 个工具", agent.getAgentType(), allTools.size());
            return allTools;
        }

        // 根据代理的工具权限过滤
        List<Tool<?, ?>> allowedTools = allTools.stream()
                .filter(tool -> agent.allowsTool(tool.getName()))
                .collect(Collectors.toList());

        log.debug("代理 {} 允许使用 {} 个工具（共 {} 个）",
                agent.getAgentType(), allowedTools.size(), allTools.size());

        return allowedTools;
    }

    /**
     * 检查代理是否可以使用指定工具
     */
    public boolean canUseTool(AgentConfig agent, String toolName) {
        return agent.allowsTool(toolName);
    }

    /**
     * 构建代理的系统提示词
     */
    public String buildSystemPrompt(AgentConfig agent) {
        StringBuilder prompt = new StringBuilder();

        // 基础系统提示词
        prompt.append(agent.getSystemPrompt());

        // 添加工具权限说明
        if (!agent.allowsAllTools()) {
            prompt.append("\n\n## 可用工具\n\n");
            prompt.append("你只能使用以下工具：\n");
            agent.getTools().forEach(tool -> prompt.append("- ").append(tool).append("\n"));
        }

        return prompt.toString();
    }

    /**
     * 获取代理的模型名称
     */
    public Optional<String> getModelName(AgentConfig agent) {
        return Optional.ofNullable(agent.getModelName());
    }

    /**
     * 列出所有可用代理
     */
    public List<AgentConfig> listAvailableAgents() {
        return agentRegistry.getAllAgents();
    }

    /**
     * 获取代理描述（用于AI选择代理）
     */
    public String getAgentDescriptions() {
        List<AgentConfig> agents = agentRegistry.getAllAgents();

        StringBuilder descriptions = new StringBuilder();
        descriptions.append("可用的代理：\n\n");

        for (AgentConfig agent : agents) {
            descriptions.append("### ").append(agent.getAgentType()).append("\n");
            descriptions.append("**何时使用**: ").append(agent.getWhenToUse()).append("\n");
            descriptions.append("**工具权限**: ");
            if (agent.allowsAllTools()) {
                descriptions.append("所有工具\n");
            } else {
                descriptions.append(String.join(", ", agent.getTools())).append("\n");
            }
            descriptions.append("**位置**: ").append(agent.getLocation().getValue()).append("\n");
            descriptions.append("\n");
        }

        return descriptions.toString();
    }
}
