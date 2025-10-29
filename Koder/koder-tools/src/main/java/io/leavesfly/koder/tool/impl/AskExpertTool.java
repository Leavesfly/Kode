package io.leavesfly.koder.tool.impl;

import io.leavesfly.koder.tool.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 专家咨询工具
 * 允许主模型向专门的专家模型请求帮助
 * （实际应用中需要集成模型管理器来调用不同的模型）
 */
@Slf4j
@Component
public class AskExpertTool extends AbstractTool<AskExpertTool.Input, AskExpertTool.Output> {

    @Override
    public String getName() {
        return "AskExpert";
    }

    @Override
    public String getDescription() {
        return "向专家模型请求帮助。可以用于获取专业领域的建议或分析。";
    }

    @Override
    public String getPrompt(boolean safeMode) {
        return """
                向专家模型咨询：
                - question: 要咨询的问题（必需）
                - expert_type: 专家类型（可选：code_review, architecture, debugging, performance）
                - context: 额外的上下文信息（可选）
                
                专家将提供专业的分析和建议。
                """;
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of("type", "string", "description", "要咨询的问题"),
                        "expert_type", Map.of(
                                "type", "string",
                                "description", "专家类型",
                                "enum", java.util.List.of("code_review", "architecture", "debugging", "performance", "general")
                        ),
                        "context", Map.of("type", "string", "description", "额外的上下文信息")
                ),
                "required", java.util.List.of("question")
        );
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isConcurrencySafe() {
        return true;
    }

    @Override
    public boolean needsPermissions(Input input) {
        return false;
    }

    @Override
    public String renderToolUseMessage(Input input, boolean verbose) {
        return String.format("咨询专家: %s - %s",
                input.expertType != null ? input.expertType : "general",
                input.question);
    }

    @Override
    public String renderToolResultMessage(Output output) {
        return String.format("专家回复 (%s):\n%s",
                output.expertType, output.answer);
    }

    @Override
    public Flux<ToolResponse<Output>> call(Input input, ToolUseContext context) {
        return Flux.create(sink -> {
            try {
                // TODO: 集成实际的模型管理器来调用专家模型
                // 目前使用模拟响应
                String expertType = input.expertType != null ? input.expertType : "general";
                String answer = generateMockExpertAnswer(input.question, expertType, input.context);

                Output output = Output.builder()
                        .question(input.question)
                        .expertType(expertType)
                        .answer(answer)
                        .timestamp(System.currentTimeMillis())
                        .build();

                sink.next(ToolResponse.result(output));
                sink.complete();

                log.info("专家咨询完成: type={}", expertType);

            } catch (Exception e) {
                log.error("专家咨询失败", e);
                sink.error(new RuntimeException("专家咨询失败: " + e.getMessage(), e));
            }
        });
    }

    /**
     * 生成模拟专家回答
     * TODO: 替换为实际的模型调用
     */
    private String generateMockExpertAnswer(String question, String expertType, String context) {
        return String.format("""
                [模拟专家回复 - %s]
                
                问题: %s
                
                分析:
                这是一个关于 %s 的问题。根据提供的上下文，我建议：
                
                1. 仔细审查相关代码和文档
                2. 考虑最佳实践和设计模式
                3. 权衡性能、可维护性和可扩展性
                
                %s
                
                注意: 这是一个模拟回复。实际应用中需要集成真实的专家模型。
                """,
                expertType,
                question,
                expertType,
                context != null ? "上下文: " + context : ""
        );
    }

    /**
     * 输入参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Input {
        /**
         * 问题
         */
        private String question;

        /**
         * 专家类型
         */
        private String expertType;

        /**
         * 上下文
         */
        private String context;
    }

    /**
     * 输出结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output {
        /**
         * 问题
         */
        private String question;

        /**
         * 专家类型
         */
        private String expertType;

        /**
         * 回答
         */
        private String answer;

        /**
         * 时间戳
         */
        private long timestamp;
    }
}
