package io.leavesfly.koder.cli.command.builtin;

import io.leavesfly.koder.agent.AgentConfig;
import io.leavesfly.koder.agent.AgentRegistry;
import io.leavesfly.koder.cli.command.Command;
import io.leavesfly.koder.cli.command.CommandContext;
import io.leavesfly.koder.cli.command.CommandResult;
import io.leavesfly.koder.cli.service.AIQueryService;
import io.leavesfly.koder.tool.executor.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * agents命令 - 全功能代理管理
 * 功能：
 * 1. 列出所有代理
 * 2. 查看代理详情
 * 3. 创建新代理 (AI生成或手动)
 * 4. 编辑代理配置
 * 5. 删除代理
 * 6. 验证代理配置
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentsCommand implements Command {

    private final AgentRegistry agentRegistry;
    private final AIQueryService aiQueryService;
    private final ToolExecutor toolExecutor;
    
    private static final List<String> RESERVED_NAMES = List.of(
            "help", "exit", "quit", "agents", "task", "model", "config"
    );

    @Override
    public String getName() {
        return "agents";
    }

    @Override
    public String getDescription() {
        return "显示所有可用的代理";
    }

    @Override
    public String getUsage() {
        return "/agents [list|create|edit|delete|view] [代理名称] [--ai]";
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String[] args = context.getArgs().toArray(new String[0]);

        // 无参数：列出所有代理
        if (args.length == 0) {
            return listAllAgents();
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "list", "ls" -> listAllAgents();
            // case "create", "new" -> createAgent(context, args); // TODO
            // case "edit", "update" -> editAgent(context, args); // TODO
            // case "delete", "remove", "rm" -> deleteAgent(context, args); // TODO
            // case "view", "show", "info" -> viewAgent(context, args); // TODO: u5b9eu73b0viewAgentu65b9u6cd5
            // case "validate" -> validateAgent(context, args); // TODO
            default -> {
                // 当做代理名称处理
                yield showAgentDetails(subCommand);
            }
        };
    }

    /**
     * 列出所有代理
     */
    private CommandResult listAllAgents() {
        List<AgentConfig> agents = agentRegistry.getAllAgents();

        if (agents.isEmpty()) {
            return CommandResult.success("未找到任何代理配置");
        }

        StringBuilder output = new StringBuilder();
        output.append("\n=== 可用代理 ===\n\n");

        for (AgentConfig agent : agents) {
            output.append(String.format("📦 %s [%s]\n",
                    agent.getAgentType(),
                    agent.getLocation().getValue()));
            output.append(String.format("   %s\n", agent.getWhenToUse()));

            // 工具权限
            if (agent.allowsAllTools()) {
                output.append("   工具: 所有工具\n");
            } else {
                output.append(String.format("   工具: %s\n",
                        String.join(", ", agent.getTools())));
            }

            // 模型覆盖
            if (agent.getModelName() != null) {
                output.append(String.format("   模型: %s\n", agent.getModelName()));
            }

            output.append("\n");
        }

        output.append(String.format("总计: %d 个代理\n", agents.size()));
        output.append("\n使用 /agents <代理名称> 查看详细信息\n");

        return CommandResult.success(output.toString());
    }

    /**
     * 显示代理详细信息
     */
    private CommandResult showAgentDetails(String agentType) {
        return agentRegistry.getAgentByType(agentType)
                .map(agent -> {
                    StringBuilder output = new StringBuilder();
                    output.append("\n=== 代理详情 ===\n\n");
                    output.append(String.format("名称: %s\n", agent.getAgentType()));
                    output.append(String.format("位置: %s\n", agent.getLocation().getValue()));
                    output.append(String.format("何时使用: %s\n", agent.getWhenToUse()));

                    // 工具权限
                    output.append("\n工具权限:\n");
                    if (agent.allowsAllTools()) {
                        output.append("  * 所有工具\n");
                    } else {
                        agent.getTools().forEach(tool ->
                                output.append(String.format("  - %s\n", tool))
                        );
                    }

                    // 模型覆盖
                    if (agent.getModelName() != null) {
                        output.append(String.format("\n指定模型: %s\n", agent.getModelName()));
                    }

                    // 颜色
                    if (agent.getColor() != null) {
                        output.append(String.format("UI颜色: %s\n", agent.getColor()));
                    }

                    // 系统提示词
                    output.append("\n系统提示词:\n");
                    output.append("─────────────────────\n");
                    output.append(agent.getSystemPrompt());
                    output.append("\n─────────────────────\n");

                    return CommandResult.success(output.toString());
                })
                .orElse(CommandResult.failure("未找到代理: " + agentType));
    }
}
