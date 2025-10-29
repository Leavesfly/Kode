package io.leavesfly.koder.model.adapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型能力描述
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCapabilities {
    
    /**
     * 是否支持流式输出
     */
    @Builder.Default
    private boolean supportsStreaming = true;
    
    /**
     * 是否支持工具调用
     */
    @Builder.Default
    private boolean supportsTools = true;
    
    /**
     * 是否支持视觉输入
     */
    @Builder.Default
    private boolean supportsVision = false;
    
    /**
     * 是否支持思考过程输出
     */
    @Builder.Default
    private boolean supportsThinking = false;
    
    /**
     * 最大上下文窗口
     */
    private int maxContextLength;
    
    /**
     * 最大输出Token数
     */
    private int maxOutputTokens;
    
    /**
     * API类型（chat-completions 或 responses）
     */
    private String apiType;
}
