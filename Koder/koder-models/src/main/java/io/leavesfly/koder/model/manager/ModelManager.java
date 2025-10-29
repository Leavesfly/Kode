package io.leavesfly.koder.model.manager;

import io.leavesfly.koder.core.config.ConfigManager;
import io.leavesfly.koder.core.config.ModelPointers;
import io.leavesfly.koder.core.config.ModelProfile;
import io.leavesfly.koder.model.adapter.ModelAdapter;
import io.leavesfly.koder.model.provider.ModelAdapterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型管理器
 * 负责模型配置管理、模型切换和适配器创建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelManager {
    
    private final ConfigManager configManager;
    private final ModelAdapterFactory adapterFactory;
    
    // 模型适配器缓存
    private final Map<String, ModelAdapter> adapterCache = new ConcurrentHashMap<>();
    
    /**
     * 根据模型指针类型获取模型名称
     */
    public String getModelName(String pointerType) {
        ModelPointers pointers = configManager.getGlobalConfig().getModelPointers();
        if (pointers == null) {
            log.warn("模型指针未配置");
            return null;
        }
        
        return switch (pointerType.toLowerCase()) {
            case "main" -> pointers.getMain();
            case "task" -> pointers.getTask();
            case "reasoning" -> pointers.getReasoning();
            case "quick" -> pointers.getQuick();
            default -> {
                log.warn("未知的模型指针类型: {}", pointerType);
                yield null;
            }
        };
    }
    
    /**
     * 获取模型适配器
     */
    public Optional<ModelAdapter> getModelAdapter(String modelName) {
        // 检查缓存
        if (adapterCache.containsKey(modelName)) {
            return Optional.of(adapterCache.get(modelName));
        }
        
        // 查找模型配置
        Optional<ModelProfile> profileOpt = configManager.findModelProfile(modelName);
        if (profileOpt.isEmpty()) {
            log.error("未找到模型配置: {}", modelName);
            return Optional.empty();
        }
        
        ModelProfile profile = profileOpt.get();
        
        // 检查模型是否激活
        if (!profile.isActive()) {
            log.warn("模型未激活: {}", modelName);
            return Optional.empty();
        }
        
        try {
            // 创建适配器
            ModelAdapter adapter = adapterFactory.createAdapter(profile);
            
            // 缓存适配器
            adapterCache.put(modelName, adapter);
            
            log.info("已创建模型适配器: {} ({})", modelName, profile.getProvider());
            return Optional.of(adapter);
        } catch (Exception e) {
            log.error("创建模型适配器失败: {}", modelName, e);
            return Optional.empty();
        }
    }
    
    /**
     * 根据指针类型获取模型适配器
     */
    public Optional<ModelAdapter> getModelAdapterByPointer(String pointerType) {
        String modelName = getModelName(pointerType);
        if (modelName == null) {
            return Optional.empty();
        }
        return getModelAdapter(modelName);
    }
    
    /**
     * 更新模型配置
     */
    public void updateModelProfile(ModelProfile profile) {
        // 移除缓存
        adapterCache.remove(profile.getModelName());
        
        // 更新配置中的模型
        configManager.getGlobalConfig().getModelProfiles()
            .removeIf(p -> p.getModelName().equals(profile.getModelName()));
        configManager.getGlobalConfig().getModelProfiles().add(profile);
        
        configManager.saveGlobalConfig();
        
        log.info("已更新模型配置: {}", profile.getModelName());
    }
    
    /**
     * 添加新模型配置
     */
    public void addModelProfile(ModelProfile profile) {
        profile.setCreatedAt(System.currentTimeMillis());
        configManager.getGlobalConfig().getModelProfiles().add(profile);
        configManager.saveGlobalConfig();
        
        log.info("已添加模型配置: {}", profile.getModelName());
    }
    
    /**
     * 删除模型配置
     */
    public void removeModelProfile(String modelName) {
        // 移除缓存
        adapterCache.remove(modelName);
        
        // 从配置中移除
        configManager.getGlobalConfig().getModelProfiles()
            .removeIf(p -> p.getModelName().equals(modelName));
        configManager.saveGlobalConfig();
        
        log.info("已删除模型配置: {}", modelName);
    }
    
    /**
     * 设置模型指针
     */
    public void setModelPointer(String pointerType, String modelName) {
        ModelPointers pointers = configManager.getGlobalConfig().getModelPointers();
        if (pointers == null) {
            pointers = new ModelPointers();
            configManager.getGlobalConfig().setModelPointers(pointers);
        }
        
        switch (pointerType.toLowerCase()) {
            case "main" -> pointers.setMain(modelName);
            case "task" -> pointers.setTask(modelName);
            case "reasoning" -> pointers.setReasoning(modelName);
            case "quick" -> pointers.setQuick(modelName);
            default -> {
                log.warn("未知的模型指针类型: {}", pointerType);
                return;
            }
        }
        
        configManager.saveGlobalConfig();
        log.info("已设置模型指针 {} -> {}", pointerType, modelName);
    }
    
    /**
     * 清除适配器缓存
     */
    public void clearAdapterCache() {
        adapterCache.clear();
        log.info("已清除模型适配器缓存");
    }
    
    /**
     * 更新模型最后使用时间
     */
    public void updateLastUsed(String modelName) {
        configManager.findModelProfile(modelName).ifPresent(profile -> {
            profile.setLastUsed(System.currentTimeMillis());
            updateModelProfile(profile);
        });
    }
}
