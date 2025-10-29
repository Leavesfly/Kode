package io.leavesfly.koder.model.adapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    
    /**
     * 是否有效
     */
    private boolean valid;
    
    /**
     * 错误消息
     */
    private String errorMessage;
    
    /**
     * 错误代码
     */
    private Integer errorCode;
    
    /**
     * 创建成功的验证结果
     */
    public static ValidationResult success() {
        return ValidationResult.builder()
            .valid(true)
            .build();
    }
    
    /**
     * 创建失败的验证结果
     */
    public static ValidationResult failure(String errorMessage) {
        return ValidationResult.builder()
            .valid(false)
            .errorMessage(errorMessage)
            .build();
    }
    
    /**
     * 创建失败的验证结果（带错误代码）
     */
    public static ValidationResult failure(String errorMessage, int errorCode) {
        return ValidationResult.builder()
            .valid(false)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .build();
    }
}
