package io.leavesfly.koder.model.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.koder.core.config.ModelProfile;
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
 * Google Gemini适配器
 * 实现Gemini API
 */
@Slf4j
@RequiredArgsConstructor
public class GeminiAdapter implements ModelAdapter {
    
    private final ModelProfile profile;
    private final WebClient webClient;
    
    @Override
    public Flux<MessageChunk> query(
        List<Message> messages,
        String systemPrompt,
        List<Object> tools,
        Map<String, Object> options
    ) {
        log.info("调用Gemini模型: {}", profile.getModelName());
        
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        
        // Gemini的消息格式不同，system通过systemInstruction字段
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, Object> systemInstruction = new HashMap<>();
            systemInstruction.put("parts", List.of(Map.of("text", systemPrompt)));
            requestBody.put("systemInstruction", systemInstruction);
        }
        
        // 消息列表
        requestBody.put("contents", formatMessages(messages));
        
        // 生成配置
        Map<String, Object> generationConfig = new HashMap<>();
        if (profile.getMaxTokens() > 0) {
            generationConfig.put("maxOutputTokens", profile.getMaxTokens());
        }
        if (options.containsKey("temperature")) {
            generationConfig.put("temperature", options.get("temperature"));
        }
        if (!generationConfig.isEmpty()) {
            requestBody.put("generationConfig", generationConfig);
        }
        
        // 工具
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools);
        }
        
        // 调用API
        String endpoint = String.format("/v1beta/models/%s:streamGenerateContent?key=%s",
                profile.getModelName(), profile.getApiKey());
        
        return webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamEvent)
                .doOnError(error -> log.error("Gemini API调用失败", error));
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
            .apiType("gemini")
            .build();
    }
    
    @Override
    public List<ApiMessage> formatMessages(List<Message> messages) {
        // Gemini使用contents格式，不是OpenAI的messages
        return messages.stream()
                .map(this::convertToGeminiContent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 转换为Gemini content格式
     */
    private ApiMessage convertToGeminiContent(Message message) {
        if (message instanceof UserMessage) {
            Map<String, Object> content = new HashMap<>();
            content.put("role", "user");
            content.put("parts", List.of(Map.of("text", ((UserMessage) message).getContent())));
            return ApiMessage.builder()
                    .role("user")
                    .content(content)
                    .build();
        } else if (message instanceof AssistantMessage) {
            AssistantMessage am = (AssistantMessage) message;
            Map<String, Object> content = new HashMap<>();
            content.put("role", "model"); // Gemini使用"model"而不是"assistant"
            content.put("parts", List.of(Map.of("text", am.getContent() != null ? am.getContent() : "")));
            return ApiMessage.builder()
                    .role("model")
                    .content(content)
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
            
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(jsonData);
                
                JsonNode candidates = node.path("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    JsonNode candidate = candidates.get(0);
                    JsonNode content = candidate.path("content");
                    JsonNode parts = content.path("parts");
                    
                    if (parts.isArray() && parts.size() > 0) {
                        JsonNode part = parts.get(0);
                        
                        // 文本内容
                        if (part.has("text")) {
                            String text = part.path("text").asText();
                            return Flux.just(MessageChunk.builder()
                                    .type(MessageChunk.ChunkType.TEXT)
                                    .content(text)
                                    .build());
                        }
                    }
                    
                    // 停止原因
                    String finishReason = candidate.path("finishReason").asText(null);
                    if (finishReason != null) {
                        return Flux.just(MessageChunk.builder()
                                .type(MessageChunk.ChunkType.STOP)
                                .stopReason(finishReason)
                                .build());
                    }
                }
                
            } catch (Exception e) {
                log.warn("解析Gemini SSE事件失败: {}", jsonData, e);
                return Flux.empty();
            }
        }
        return Flux.empty();
    }
    
    @Override
    public String getProviderName() {
        return "gemini";
    }
}
