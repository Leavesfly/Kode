package io.leavesfly.koder.cli.service;

import io.leavesfly.koder.cli.repl.REPLSession;
import io.leavesfly.koder.core.message.AssistantMessage;
import io.leavesfly.koder.core.message.Message;
import io.leavesfly.koder.core.message.UserMessage;
import io.leavesfly.koder.model.adapter.MessageChunk;
import io.leavesfly.koder.model.adapter.ModelAdapter;
import io.leavesfly.koder.model.manager.ModelManager;
import io.leavesfly.koder.tool.Tool;
import io.leavesfly.koder.tool.executor.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI查询服务
 * 负责调用AI模型并处理响应
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIQueryService {

    private final ModelManager modelManager;
    private final ToolExecutor toolExecutor;

    /**
     * 查询AI模型
     *
     * @param userInput   用户输入
     * @param session     REPL会话
     * @param systemPrompt 系统提示词
     * @return 流式响应
     */
    public Flux<AIResponse> query(String userInput, REPLSession session, String systemPrompt) {
        return Flux.create(sink -> {
            try {
                // 创建用户消息
                UserMessage userMessage = UserMessage.builder()
                        .content(userInput)
                        .build();

                // 添加到会话
                session.addMessage(userMessage);

                // 获取主模型适配器
                ModelAdapter adapter = modelManager.getModelAdapterByPointer("main")
                        .orElseThrow(() -> new RuntimeException("未找到主模型配置"));

                // 准备消息历史
                List<Message> messages = new ArrayList<>(session.getMessages());

                // 准备工具列表
                List<Object> tools = prepareTools();

                // 准备选项
                Map<String, Object> options = new HashMap<>();
                options.put("verbose", session.isVerbose());
                options.put("safeMode", session.isSafeMode());

                // 调用模型
                adapter.query(messages, systemPrompt, tools, options)
                        .subscribe(
                                chunk -> {
                                    // 处理每个消息块
                                    AIResponse response = processChunk(chunk, session);
                                    if (response != null) {
                                        sink.next(response);
                                    }
                                },
                                error -> {
                                    log.error("AI查询失败", error);
                                    sink.error(error);
                                },
                                () -> {
                                    log.debug("AI查询完成");
                                    sink.complete();
                                }
                        );

            } catch (Exception e) {
                log.error("启动AI查询失败", e);
                sink.error(e);
            }
        });
    }

    /**
     * 处理消息块
     */
    private AIResponse processChunk(MessageChunk chunk, REPLSession session) {
        return switch (chunk.getType()) {
            case TEXT -> {
                // 文本块
                yield AIResponse.builder()
                        .type(AIResponseType.TEXT)
                        .content(chunk.getContent())
                        .build();
            }
            case TOOL_USE -> {
                // 工具调用
                yield AIResponse.builder()
                        .type(AIResponseType.TOOL_USE)
                        .toolName(chunk.getToolName())
                        .toolInput((Map<String, Object>) chunk.getToolInput())
                        .build();
            }
            case THINKING -> {
                // 思考过程
                yield AIResponse.builder()
                        .type(AIResponseType.THINKING)
                        .content(chunk.getContent())
                        .build();
            }
            case COMPLETE -> {
                // 完成标记
                if (chunk.getMessage() != null) {
                    session.addMessage(chunk.getMessage());
                }
                // 记录Token使用
                if (chunk.getTokenUsage() != null && chunk.getModelName() != null) {
                    session.getCostTracker().recordUsage(chunk.getModelName(), chunk.getTokenUsage());
                }
                yield AIResponse.builder()
                        .type(AIResponseType.COMPLETE)
                        .message(chunk.getMessage())
                        .build();
            }
            default -> null;
        };
    }

    /**
     * 准备工具列表
     */
    private List<Object> prepareTools() {
        List<Object> tools = new ArrayList<>();

        // 获取所有工具的Schema
        toolExecutor.getAllTools().forEach(tool -> {
            Map<String, Object> toolSchema = new HashMap<>();
            toolSchema.put("name", tool.getName());
            toolSchema.put("description", tool.getDescription());
            toolSchema.put("input_schema", tool.getInputSchema());
            tools.add(toolSchema);
        });

        return tools;
    }

    /**
     * AI响应类型
     */
    public enum AIResponseType {
        TEXT,       // 文本响应
        TOOL_USE,   // 工具调用
        THINKING,   // 思考过程
        COMPLETE    // 完成
    }

    /**
     * AI响应
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AIResponse {
        private AIResponseType type;
        private String content;
        private String toolName;
        private Map<String, Object> toolInput;
        private Message message;
    }
}
