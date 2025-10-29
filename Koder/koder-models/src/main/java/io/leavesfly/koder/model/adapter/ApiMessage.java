package io.leavesfly.koder.model.adapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * API消息格式
 * 用于与AI模型API交互的标准消息格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiMessage {
    
    /**
     * 消息角色
     */
    private String role;
    
    /**
     * 消息内容（文本或复杂内容）
     */
    private Object content;
    
    /**
     * 工具调用列表（仅assistant消息）
     */
    private List<ApiToolCall> toolCalls;
    
    /**
     * 工具调用ID（仅tool消息）
     */
    private String toolCallId;
    
    /**
     * 消息名称（可选）
     */
    private String name;
}

/**
 * API工具调用格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ApiToolCall {
    
    /**
     * 工具调用ID
     */
    private String id;
    
    /**
     * 工具类型
     */
    @Builder.Default
    private String type = "function";
    
    /**
     * 函数调用信息
     */
    private ApiFunctionCall function;
}

/**
 * API函数调用格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ApiFunctionCall {
    
    /**
     * 函数名称
     */
    private String name;
    
    /**
     * 函数参数（JSON字符串）
     */
    private String arguments;
}
