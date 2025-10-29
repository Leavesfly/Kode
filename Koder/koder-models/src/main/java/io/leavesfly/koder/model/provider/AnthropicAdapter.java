package io.leavesfly.koder.model.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.koder.core.config.ModelProfile;
import io.leavesfly.koder.core.cost.TokenUsage;
import io.leavesfly.koder.core.message.*;
import io.leavesfly.koder.model.adapter.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Anthropic Claude适配器
 * 实现Anthropic Messages API
 */
@Slf4j
@RequiredArgsConstructor
public class AnthropicAdapter implements ModelAdapter {
    
    private final ModelProfile profile;
    private final WebClient webClient;
    
    // 用于累积响应内容
    private final StringBuilder contentBuilder = new StringBuilder();
    private String currentStopReason = null;
    private io.leavesfly.koder.core.cost.TokenUsage currentTokenUsage = null;
    
    @Override
    public Flux<MessageChunk> query(
        List<Message> messages,
        String systemPrompt,
        List<Object> tools,
        Map<String, Object> options
    ) {
        log.info("调用Anthropic模型: {}", profile.getModelName());
        
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", profile.getModelName());
        requestBody.put("max_tokens", profile.getMaxTokens() > 0 ? profile.getMaxTokens() : 4096);
        requestBody.put("stream", true);
        
        // 系统提示词
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            requestBody.put("system", systemPrompt);
        }
        
        // 消息列表
        requestBody.put("messages", formatMessages(messages));
        
        // 工具
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools);
        }
        
        // 温度
        if (options.containsKey("temperature")) {
            requestBody.put("temperature", options.get("temperature"));
        }
        
        // 调用API
        return webClient.post()
                .uri("/v1/messages")
                .header("anthropic-version", "2023-06-01")
                .header("x-api-key", profile.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamEvent)
                .doOnError(error -> log.error("Anthropic API调用失败", error));
    }
    
    @Override
    public ValidationResult validate() {
        if (profile.getApiKey() == null || profile.getApiKey().isEmpty()) {
            return ValidationResult.builder()
                    .valid(false)
                    .errorMessage("API密钥未配置")
                    .build();
        }
        
        if (profile.getModelName() == null || profile.getModelName().isEmpty()) {
            return ValidationResult.builder()
                    .valid(false)
                    .errorMessage("模型名称未配置")
                    .build();
        }
        
        return ValidationResult.builder()
                .valid(true)
                .errorMessage("配置有效")
                .build();
    }
    
    @Override
    public ModelCapabilities getCapabilities() {
        return ModelCapabilities.builder()
            .supportsStreaming(true)
            .supportsTools(true)
            .supportsVision(true)
            .supportsThinking(false)
            .maxContextLength(profile.getContextLength())
            .maxOutputTokens(profile.getMaxTokens())
            .apiType("messages")
            .build();
    }
    
    @Override
    public List<ApiMessage> formatMessages(List<Message> messages) {
        return messages.stream()
                .map(this::convertMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 转换单个消息
     */
    private ApiMessage convertMessage(Message message) {
        if (message instanceof UserMessage) {
            return ApiMessage.builder()
                    .role("user")
                    .content(((UserMessage) message).getContent())
                    .build();
        } else if (message instanceof AssistantMessage) {
            AssistantMessage am = (AssistantMessage) message;
            return ApiMessage.builder()
                    .role("assistant")
                    .content(am.getContent() != null ? am.getContent() : "")
                    .build();
        } else if (message instanceof ToolResultMessage) {
            ToolResultMessage tm = (ToolResultMessage) message;
            return ApiMessage.builder()
                    .role("user")
                    .content(formatToolResult(tm))
                    .build();
        }
        return null;
    }
    
    /**
     * 格式化工具结果
     */
    private Object formatToolResult(ToolResultMessage message) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "tool_result");
        result.put("tool_use_id", message.getToolUseId());
        result.put("content", message.getContent());
        return List.of(result);
    }
    
    /**
     * 解析流式事件
     */
    private Flux<MessageChunk> parseStreamEvent(String event) {
        if (event.startsWith("data: ")) {
            String jsonData = event.substring(6).trim();
            
            if ("[DONE]".equals(jsonData)) {
                return Flux.just(MessageChunk.builder()
                        .type(MessageChunk.ChunkType.STOP)
                        .stopReason("end_turn")
                        .build());
            }
            
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(jsonData);
                
                String type = node.path("type").asText();
                
                return switch (type) {
                    case "content_block_delta" -> parseContentDelta(node);
                    case "message_delta" -> parseMessageDelta(node);
                    case "message_stop" -> parseMessageStop(node);
                    default -> Flux.empty();
                };
                
            } catch (Exception e) {
                log.warn("解析SSE事件失败: {}", jsonData, e);
                return Flux.empty();
            }
        }
        return Flux.empty();
    }
    
    /**
     * 解析内容增量
     */
    private Flux<MessageChunk> parseContentDelta(JsonNode node) {
        JsonNode delta = node.path("delta");
        String deltaType = delta.path("type").asText();
        
        if ("text_delta".equals(deltaType)) {
            String text = delta.path("text").asText();
            contentBuilder.append(text);
            return Flux.just(MessageChunk.builder()
                    .type(MessageChunk.ChunkType.TEXT)
                    .content(text)
                    .build());
        }
        
        return Flux.empty();
    }
    
    /**
     * 解析消息增量
     */
    private Flux<MessageChunk> parseMessageDelta(JsonNode node) {
        JsonNode delta = node.path("delta");
        String stopReason = delta.path("stop_reason").asText(null);
        
        if (stopReason != null) {
            currentStopReason = stopReason;
            return Flux.just(MessageChunk.builder()
                    .type(MessageChunk.ChunkType.STOP)
                    .stopReason(stopReason)
                    .build());
        }
        
        // 解析usage
        JsonNode usage = node.path("usage");
        if (usage != null && !usage.isMissingNode()) {
            currentTokenUsage = TokenUsage.builder()
                    .inputTokens(usage.path("input_tokens").asInt(0))
                    .outputTokens(usage.path("output_tokens").asInt(0))
                    .build();
        }
        
        return Flux.empty();
    }
    
    /**
     * 解析消息停止
     */
    private Flux<MessageChunk> parseMessageStop(JsonNode node) {
        // 构建完整消息
        AssistantMessage message = AssistantMessage.builder()
                .content(contentBuilder.toString())
                .stopReason(currentStopReason)
                .build();
        
        MessageChunk chunk = MessageChunk.builder()
                .type(MessageChunk.ChunkType.COMPLETE)
                .message(message)
                .tokenUsage(currentTokenUsage)
                .modelName(profile.getModelName())
                .build();
        
        // 重置状态
        contentBuilder.setLength(0);
        currentStopReason = null;
        currentTokenUsage = null;
        
        return Flux.just(chunk);
    }
    
    @Override
    public String getProviderName() {
        return "anthropic";
    }
}
