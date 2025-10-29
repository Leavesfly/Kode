package io.leavesfly.koder.model.adapter;

import io.leavesfly.koder.core.message.Message;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 模型适配器接口
 * 统一不同AI模型提供商的调用方式
 */
public interface ModelAdapter {
    
    /**
     * 流式查询模型
     * 
     * @param messages 消息历史
     * @param systemPrompt 系统提示词
     * @param tools 可用工具列表（可选）
     * @param options 额外选项
     * @return 流式消息响应
     */
    Flux<MessageChunk> query(
        List<Message> messages, 
        String systemPrompt,
        List<Object> tools,
        Map<String, Object> options
    );
    
    /**
     * 验证配置有效性
     */
    ValidationResult validate();
    
    /**
     * 获取模型能力描述
     */
    ModelCapabilities getCapabilities();
    
    /**
     * 将内部消息格式转换为API格式
     */
    List<ApiMessage> formatMessages(List<Message> messages);
    
    /**
     * 获取提供商名称
     */
    String getProviderName();
}
