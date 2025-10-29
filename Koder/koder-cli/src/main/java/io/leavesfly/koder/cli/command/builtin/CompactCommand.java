package io.leavesfly.koder.cli.command.builtin;

import io.leavesfly.koder.cli.command.Command;
import io.leavesfly.koder.cli.command.CommandContext;
import io.leavesfly.koder.cli.command.CommandResult;
import io.leavesfly.koder.cli.repl.REPLSession;
import io.leavesfly.koder.cli.service.AIQueryService;
import io.leavesfly.koder.core.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * compact命令 - 压缩对话历史但保留摘要
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompactCommand implements Command {

    private final REPLSession session;
    private final AIQueryService aiQueryService;

    private static final String COMPRESSION_PROMPT = """
            请提供我们对话的全面总结，结构如下：
            
            ## 技术上下文
            开发环境、工具、框架和配置。编程语言、库和技术约束。文件结构、目录组织和项目架构。
            
            ## 项目概述
            主要项目目标、功能和范围。关键组件、模块及其关系。数据模型、API和集成模式。
            
            ## 代码变更
            在对话中创建、修改或分析的文件。添加的具体代码实现、函数和算法。配置更改和结构修改。
            
            ## 调试和问题
            遇到的问题及其根本原因。实施的解决方案及其有效性。错误消息、日志和诊断信息。
            
            ## 当前状态
            刚刚成功完成的工作。代码库的当前状态和任何正在进行的工作。测试结果、验证步骤和执行的验证。
            
            ## 待处理任务
            即时的下一步和优先事项。计划的功能、改进和重构。已知问题、技术债务和需要关注的领域。
            
            ## 用户偏好
            编码风格、格式和组织偏好。沟通模式和反馈风格。工具选择和工作流程偏好。
            
            ## 关键决策
            做出的重要技术决策及其理由。考虑的替代方法以及被拒绝的原因。接受的权衡及其影响。
            
            专注于有效继续对话所必需的信息，包括代码、文件、错误和计划的具体细节。
            """;

    @Override
    public String getName() {
        return "compact";
    }

    @Override
    public String getDescription() {
        return "清除对话历史但保留上下文摘要";
    }

    @Override
    public String getUsage() {
        return "/compact";
    }

    @Override
    public CommandResult execute(CommandContext context) {
        try {
            log.info("开始压缩对话历史...");

            // 获取当前消息
            List<io.leavesfly.koder.core.message.Message> messages = new ArrayList<>(session.getMessages());

            if (messages.isEmpty()) {
                return CommandResult.failure("当前会话无消息历史");
            }

            // 添加摘要请求消息
            io.leavesfly.koder.core.message.UserMessage summaryRequest = 
                    io.leavesfly.koder.core.message.UserMessage.builder()
                    .content(COMPRESSION_PROMPT)
                    .build();
            messages.add(summaryRequest);

            // 创建系统提示词
            String systemPrompt = "你是一个AI助手，负责创建全面的对话摘要，保留所有继续开发工作所需的重要上下文。";

            context.getOutput().println("正在生成对话摘要...");

            // 同步调用AI生成摘要
            StringBuilder summaryContent = new StringBuilder();
            aiQueryService.query(COMPRESSION_PROMPT, session, systemPrompt)
                    .doOnNext(response -> {
                        if (response.getType() == io.leavesfly.koder.cli.service.AIQueryService.AIResponseType.TEXT) {
                            summaryContent.append(response.getContent());
                        }
                    })
                    .blockLast(); // 等待完成

            if (summaryContent.isEmpty()) {
                return CommandResult.failure("生成摘要失败");
            }

            // 清除当前会话
            session.clearMessages();

            // 添加摘要消息
            session.addMessage(io.leavesfly.koder.core.message.UserMessage.builder()
                    .content("上下文已使用结构化算法压缩。以下是对话摘要：\n\n" + summaryContent)
                    .build());

            log.info("对话历史压缩完成");

            return CommandResult.success("\n✅ 对话历史已压缩，摘要已保留\n清除了 " + (messages.size() - 1) + " 条消息，保留了关键上下文\n");

        } catch (Exception e) {
            log.error("压缩对话历史失败", e);
            return CommandResult.failure("压缩失败: " + e.getMessage());
        }
    }
}
