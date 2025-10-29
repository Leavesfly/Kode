package io.leavesfly.koder.model.adapter;

import io.leavesfly.koder.core.cost.TokenUsage;
import io.leavesfly.koder.core.message.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息块
 * 流式响应的单个数据块
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageChunk {
    
    /**
     * 块类型
     */
    private ChunkType type;
    
    /**
     * 文本内容
     */
    private String content;
    
    /**
     * 工具调用ID
     */
    private String toolUseId;
    
    /**
     * 工具名称
     */
    private String toolName;
    
    /**
     * 工具输入
     */
    private Object toolInput;
    
    /**
     * 停止原因
     */
    private String stopReason;
    
    /**
     * 思考内容（仅用于支持thinking的模型）
     */
    private String thinkingContent;
    
    /**
     * 完整消息（COMPLETE类型）
     */
    private Message message;
    
    /**
     * Token使用统计（COMPLETE类型）
     */
    private TokenUsage tokenUsage;
    
    /**
     * 模型名称（COMPLETE类型）
     */
    private String modelName;
    
    public enum ChunkType {
        TEXT,           // 文本块
        TOOL_USE,       // 工具调用块
        THINKING,       // 思考过程块
        STOP,           // 停止块
        COMPLETE        // 完成块（包含完整消息）
    }
}
