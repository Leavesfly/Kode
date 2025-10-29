package io.leavesfly.koder.model.provider;

import io.leavesfly.koder.core.config.ModelProfile;
import io.leavesfly.koder.core.config.ProviderType;
import io.leavesfly.koder.model.adapter.ModelAdapter;
import io.leavesfly.koder.model.adapter.impl.DemoModelAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 模型适配器工厂
 * 根据模型配置创建相应的适配器实例
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelAdapterFactory {
    
    private final WebClient.Builder webClientBuilder;
    
    /**
     * 创建模型适配器
     */
    public ModelAdapter createAdapter(ModelProfile profile) {
        log.info("创建模型适配器: provider={}, model={}", profile.getProvider(), profile.getModelName());
        
        // 如果是演示提供商，返回演示适配器
        if (profile.getProvider() == ProviderType.DEMO || 
            profile.getApiKey() == null || 
            profile.getApiKey().equals("demo")) {
            log.info("使用演示模型适配器");
            return new DemoModelAdapter(profile);
        }
        
        return switch (profile.getProvider()) {
            case ANTHROPIC -> createAnthropicAdapter(profile);
            case OPENAI, CUSTOM_OPENAI -> createOpenAIAdapter(profile);
            case GEMINI -> createGeminiAdapter(profile);
            case QWEN -> createQwenAdapter(profile);
            case DEEPSEEK -> createDeepSeekAdapter(profile);
            case KIMI, GLM, MINIMAX -> createOpenAIAdapter(profile); // OpenAI兼容接口
            default -> throw new UnsupportedOperationException(
                "不支持的提供商: " + profile.getProvider()
            );
        };
    }
    
    /**
     * 创建Anthropic适配器
     */
    private ModelAdapter createAnthropicAdapter(ModelProfile profile) {
        WebClient webClient = createWebClient(
            profile.getBaseURL() != null ? profile.getBaseURL() : "https://api.anthropic.com",
            profile.getApiKey()
        );
        return new AnthropicAdapter(profile, webClient);
    }
    
    /**
     * 创建OpenAI适配器
     */
    private ModelAdapter createOpenAIAdapter(ModelProfile profile) {
        WebClient webClient = createWebClient(
            profile.getBaseURL() != null ? profile.getBaseURL() : "https://api.openai.com",
            profile.getApiKey()
        );
        return new OpenAIAdapter(profile, webClient);
    }
    
    /**
     * 创建Gemini适配器
     */
    private ModelAdapter createGeminiAdapter(ModelProfile profile) {
        WebClient webClient = createWebClient(
            profile.getBaseURL() != null ? profile.getBaseURL() : "https://generativelanguage.googleapis.com",
            profile.getApiKey()
        );
        return new GeminiAdapter(profile, webClient);
    }
    
    /**
     * 创建 Qwen适配器
     */
    private ModelAdapter createQwenAdapter(ModelProfile profile) {
        WebClient webClient = createWebClient(
            profile.getBaseURL() != null ? profile.getBaseURL() : "https://dashscope.aliyuncs.com",
            profile.getApiKey()
        );
        return new QwenAdapter(profile, webClient);
    }
    
    /**
     * 创建DeepSeek适配器
     */
    private ModelAdapter createDeepSeekAdapter(ModelProfile profile) {
        WebClient webClient = createWebClient(
            profile.getBaseURL() != null ? profile.getBaseURL() : "https://api.deepseek.com",
            profile.getApiKey()
        );
        return new DeepSeekAdapter(profile, webClient);
    }
    
    /**
     * 创建配置好的WebClient
     */
    private WebClient createWebClient(String baseUrl, String apiKey) {
        return webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
