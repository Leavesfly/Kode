package io.leavesfly.koder.model.adapter.impl;

import io.leavesfly.koder.core.config.ModelProfile;
import io.leavesfly.koder.core.message.AssistantMessage;
import io.leavesfly.koder.core.message.Message;
import io.leavesfly.koder.core.message.UserMessage;
import io.leavesfly.koder.model.adapter.ApiMessage;
import io.leavesfly.koder.model.adapter.MessageChunk;
import io.leavesfly.koder.model.adapter.ModelAdapter;
import io.leavesfly.koder.model.adapter.ModelCapabilities;
import io.leavesfly.koder.model.adapter.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 演示模型适配器
 * 用于演示和测试，不调用真实API
 */
@Slf4j
public class DemoModelAdapter implements ModelAdapter {

    private final ModelProfile profile;

    public DemoModelAdapter(ModelProfile profile) {
        this.profile = profile;
    }

    @Override
    public Flux<MessageChunk> query(
            List<Message> messages,
            String systemPrompt,
            List<Object> tools,
            Map<String, Object> options) {

        log.info("演示模型查询: {} 条消息", messages.size());

        return Flux.create(sink -> {
            try {
                // 获取最后一条用户消息
                String userInput = messages.stream()
                        .filter(m -> m instanceof UserMessage)
                        .map(m -> ((UserMessage) m).getContent())
                        .reduce((first, second) -> second)
                        .orElse("你好");

                // 模拟思考过程
                sink.next(MessageChunk.builder()
                        .type(MessageChunk.ChunkType.THINKING)
                        .content("正在分析你的问题...")
                        .build());

                // 模拟延迟
                Thread.sleep(500);

                // 生成演示响应
                String response = generateDemoResponse(userInput, tools);

                // 分块发送响应
                String[] chunks = response.split("(?<=\\. )");
                for (String chunk : chunks) {
                    sink.next(MessageChunk.builder()
                            .type(MessageChunk.ChunkType.TEXT)
                            .content(chunk)
                            .build());

                    Thread.sleep(100); // 模拟流式输出
                }

                // 创建完整的助手消息
                AssistantMessage assistantMessage = AssistantMessage.builder()
                        .content(response)
                        .build();

                // 发送完成标记
                sink.next(MessageChunk.builder()
                        .type(MessageChunk.ChunkType.COMPLETE)
                        .message(assistantMessage)
                        .build());

                sink.complete();

            } catch (Exception e) {
                log.error("演示模型查询失败", e);
                sink.error(e);
            }
        });
    }

    /**
     * 生成演示响应
     */
    private String generateDemoResponse(String userInput, List<Object> tools) {
        StringBuilder response = new StringBuilder();

        response.append("你好！我是Koder AI助手（演示模式）。\n\n");

        // 根据输入生成不同响应
        if (userInput.contains("帮助") || userInput.contains("help")) {
            response.append("我可以帮你：\n");
            response.append("1. 编写和分析代码\n");
            response.append("2. 执行Shell命令\n");
            response.append("3. 搜索和读取文件\n");
            response.append("4. 提供编程建议\n\n");
            response.append("可用工具数量: ").append(tools != null ? tools.size() : 0);
        } else if (userInput.contains("工具") || userInput.contains("tool")) {
            response.append("当前可用的工具包括：\n");
            if (tools != null && !tools.isEmpty()) {
                tools.stream().limit(5).forEach(tool -> {
                    if (tool instanceof Map) {
                        Map<?, ?> toolMap = (Map<?, ?>) tool;
                        response.append("- ").append(toolMap.get("name"))
                                .append(": ").append(toolMap.get("description"))
                                .append("\n");
                    }
                });
                if (tools.size() > 5) {
                    response.append("... 还有 ").append(tools.size() - 5).append(" 个工具\n");
                }
            }
        } else if (userInput.contains("代码") || userInput.contains("code")) {
            response.append("关于代码编写，我建议：\n");
            response.append("1. 保持代码简洁清晰\n");
            response.append("2. 添加适当的注释\n");
            response.append("3. 遵循编码规范\n");
            response.append("4. 编写单元测试\n");
        } else {
            response.append("你说: \"").append(userInput).append("\"\n\n");
            response.append("这是一个演示响应。在实际使用中，这里会调用真实的AI模型API。\n");
            response.append("目前处于演示模式，用于测试CLI功能。");
        }

        return response.toString();
    }

    @Override
    public ValidationResult validate() {
        return ValidationResult.builder()
                .valid(true)
                .errorMessage("演示模型配置有效")
                .build();
    }

    @Override
    public ModelCapabilities getCapabilities() {
        return ModelCapabilities.builder()
                .supportsStreaming(true)
                .supportsTools(true)
                .supportsVision(false)
                .maxOutputTokens(4096)
                .maxContextLength(8192)
                .build();
    }

    @Override
    public List<ApiMessage> formatMessages(List<Message> messages) {
        List<ApiMessage> apiMessages = new ArrayList<>();

        for (Message msg : messages) {
            if (msg instanceof UserMessage) {
                UserMessage um = (UserMessage) msg;
                apiMessages.add(ApiMessage.builder()
                        .role("user")
                        .content(um.getContent())
                        .build());
            } else if (msg instanceof AssistantMessage) {
                AssistantMessage am = (AssistantMessage) msg;
                apiMessages.add(ApiMessage.builder()
                        .role("assistant")
                        .content(am.getContent())
                        .build());
            }
        }

        return apiMessages;
    }

    @Override
    public String getProviderName() {
        return "Demo";
    }
}
