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
 * OpenAI适配器
 * 实现OpenAI Chat Completions API
 * 同时兼容OpenAI兼容的国产模型
 */
@Slf4j
@RequiredArgsConstructor
public class OpenAIAdapter implements ModelAdapter {
    
    private final ModelProfile profile;
    private final WebClient webClient;
    
    // 用于累积响应内容
    private final StringBuilder contentBuilder = new StringBuilder();
    private String currentStopReason = null;
    private TokenUsage currentTokenUsage = null;
    
    @Override
    public Flux<MessageChunk> query(
        List<Message> messages,
        String systemPrompt,
        List<Object> tools,
        Map<String, Object> options
    ) {
        log.info("调用OpenAI模型: {}", profile.getModelName());
        
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", profile.getModelName());
        requestBody.put("stream", true);
        
        // 消息列表（包括系统消息）
        List<ApiMessage> apiMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            apiMessages.add(ApiMessage.builder()
                    .role("system")
                    .content(systemPrompt)
                    .build());
        }
        apiMessages.addAll(formatMessages(messages));
        requestBody.put("messages", apiMessages);
        
        // 最大token数
        if (profile.getMaxTokens() > 0) {
            requestBody.put("max_tokens", profile.getMaxTokens());
        }
        
        // 工具
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools);
            requestBody.put("tool_choice", "auto");
        }
        
        // 温度
        if (options.containsKey("temperature")) {
            requestBody.put("temperature", options.get("temperature"));
        }
        
        // 调用API
        return webClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + profile.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamEvent)
                .doOnError(error -> log.error("OpenAI API调用失败", error));
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
        // 检测是否为GPT-5类型模型
        boolean isGPT5 = profile.getIsGPT5() != null && profile.getIsGPT5();
        
        return ModelCapabilities.builder()
            .supportsStreaming(true)
            .supportsTools(true)
            .supportsVision(profile.getModelName().contains("vision") || profile.getModelName().contains("gpt-4"))
            .supportsThinking(isGPT5)
            .maxContextLength(profile.getContextLength())
            .maxOutputTokens(profile.getMaxTokens())
            .apiType(isGPT5 ? "responses" : "chat-completions")
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
                    .role("tool")
                    .toolCallId(tm.getToolUseId())
                    .content(String.valueOf(tm.getContent()))
                    .build();
        }
        return null;
    }
    
    /**
     * 解析流式事件
     */
    private Flux<MessageChunk> parseStreamEvent(String event) {
        if (event.startsWith("data: ")) {
            String jsonData = event.substring(6).trim();
            
            if ("[DONE]".equals(jsonData)) {
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
            
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(jsonData);
                
                JsonNode choices = node.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode choice = choices.get(0);
                    JsonNode delta = choice.path("delta");
                    
                    // 文本内容
                    if (delta.has("content")) {
                        String content = delta.path("content").asText();
                        contentBuilder.append(content);
                        return Flux.just(MessageChunk.builder()
                                .type(MessageChunk.ChunkType.TEXT)
                                .content(content)
                                .build());
                    }
                    
                    // 工具调用
                    if (delta.has("tool_calls")) {
                        // OpenAI的工具调用格式较复杂，这里简化处理
                        JsonNode toolCalls = delta.path("tool_calls");
                        if (toolCalls.isArray() && toolCalls.size() > 0) {
                            JsonNode toolCall = toolCalls.get(0);
                            return Flux.just(MessageChunk.builder()
                                    .type(MessageChunk.ChunkType.TOOL_USE)
                                    .toolName(toolCall.path("function").path("name").asText())
                                    .build());
                        }
                    }
                    
                    // 停止原因
                    String finishReason = choice.path("finish_reason").asText(null);
                    if (finishReason != null && !"null".equals(finishReason)) {
                        currentStopReason = finishReason;
                        return Flux.just(MessageChunk.builder()
                                .type(MessageChunk.ChunkType.STOP)
                                .stopReason(finishReason)
                                .build());
                    }
                }
                
                // 解析usage
                JsonNode usage = node.path("usage");
                if (usage != null && !usage.isMissingNode()) {
                    currentTokenUsage = TokenUsage.builder()
                            .inputTokens(usage.path("prompt_tokens").asInt(0))
                            .outputTokens(usage.path("completion_tokens").asInt(0))
                            .build();
                }
                
            } catch (Exception e) {
                log.warn("解析SSE事件失败: {}", jsonData, e);
                return Flux.empty();
            }
        }
        return Flux.empty();
    }
    
    @Override
    public String getProviderName() {
        return "openai";
    }
}
