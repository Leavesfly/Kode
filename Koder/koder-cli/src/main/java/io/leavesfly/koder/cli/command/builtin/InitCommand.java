package io.leavesfly.koder.cli.command.builtin;

import io.leavesfly.koder.cli.command.Command;
import io.leavesfly.koder.cli.command.CommandContext;
import io.leavesfly.koder.cli.command.CommandResult;
import io.leavesfly.koder.cli.repl.REPLSession;
import io.leavesfly.koder.cli.service.AIQueryService;
import io.leavesfly.koder.core.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 初始化命令 - 分析代码库并创建.koder.json配置文件
 * 
 * 功能：
 * 1. 分析当前代码库结构
 * 2. 生成或改进 .koder.json 配置文件
 * 3. 包含构建/测试命令、代码规范等信息
 * 4. 标记项目已完成onboarding
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitCommand implements Command {

    private final AIQueryService aiQueryService;
    
    private static final String PROJECT_FILE = ".koder.json";

    @Override
    public String getName() {
        return "init";
    }

    @Override
    public String getDescription() {
        return "初始化项目配置 - 分析代码库并创建" + PROJECT_FILE + "文件";
    }

    @Override
    public String getUsage() {
        return "/init";
    }

    @Override
    public CommandResult execute(CommandContext context) {
        log.info("执行初始化命令");
        
        context.getOutput().println("\n🔍 正在分析代码库...\n");
        
        // 检查是否已存在项目配置文件
        Path projectPath = Paths.get(System.getProperty("user.dir"), PROJECT_FILE);
        boolean fileExists = Files.exists(projectPath);
        
        if (fileExists) {
            context.getOutput().println("📝 检测到现有的 " + PROJECT_FILE + " 文件，将进行改进\n");
        } else {
            context.getOutput().println("📝 将创建新的 " + PROJECT_FILE + " 文件\n");
        }
        
        // 构建AI提示词
        String prompt = buildAnalysisPrompt(fileExists);
        
        // 使用AI分析代码库
        try {
            // 获取当前会话
            Object sessionObj = context.getSession();
            if (!(sessionObj instanceof REPLSession session)) {
                return CommandResult.failure("未找到当前会话");
            }
            
            // 添加用户消息
            context.getOutput().println("💭 AI正在分析...\n");
            context.getOutput().println("─".repeat(70));
            
            StringBuilder resultBuilder = new StringBuilder();
            boolean[] hasContent = {false};
            
            // 流式输出AI响应
            aiQueryService.query(prompt, session, null)
                    .doOnNext(response -> {
                        if (response.getType() == AIQueryService.AIResponseType.TEXT) {
                            String content = response.getContent();
                            context.getOutput().println(content);
                            resultBuilder.append(content);
                            hasContent[0] = true;
                        } else if (response.getType() == AIQueryService.AIResponseType.THINKING) {
                            context.getOutput().println("\n💭 " + response.getContent());
                        }
                    })
                    .doOnError(error -> {
                        log.error("初始化失败", error);
                        context.getOutput().error("\n❌ 初始化失败: " + error.getMessage());
                    })
                    .doOnComplete(() -> {
                        context.getOutput().println("\n" + "─".repeat(70));
                        context.getOutput().success("\n✅ 项目初始化完成！");
                        context.getOutput().println("\n提示：");
                        context.getOutput().println("  - " + PROJECT_FILE + " 文件已创建/更新");
                        context.getOutput().println("  - 该文件将用于指导AI助手更好地理解你的项目");
                        context.getOutput().println("  - 你可以手动编辑该文件来调整配置\n");
                    })
                    .blockLast(); // 等待完成
            
            if (!hasContent[0]) {
                return CommandResult.failure("AI未返回任何结果");
            }
            
            return CommandResult.success("");
            
        } catch (Exception e) {
            log.error("执行初始化命令失败", e);
            return CommandResult.failure("初始化失败: " + e.getMessage());
        }
    }

    /**
     * 构建代码库分析提示词
     */
    private String buildAnalysisPrompt(boolean fileExists) {
        String action = fileExists ? "改进现有的" : "创建新的";
        
        return String.format("""
                请分析这个代码库并%s %s 文件，该文件应包含：
                
                1. **构建/测试/运行命令**
                   - Maven/Gradle构建命令
                   - 单元测试运行方法
                   - 应用启动方式
                
                2. **代码规范**
                   - 导入语句规范
                   - 代码格式化要求
                   - 类型和命名约定
                   - 错误处理规范
                   - 注释语言偏好（中文）
                
                3. **项目结构**
                   - 主要模块说明
                   - 包结构规范
                   - 依赖关系
                
                4. **开发工具**
                   - IDE配置建议
                   - 推荐的插件
                
                该文件将提供给AI编程助手（如你自己）使用，帮助它们更好地理解和操作这个代码库。
                请让文件简洁明了，大约20-30行。
                
                %s
                
                如果存在其他配置文件（如.cursorrules、.github/copilot-instructions.md等），请确保包含它们的内容。
                """,
                PROJECT_FILE,
                fileExists ? "\n如果已经存在 " + PROJECT_FILE + "，请改进它。" : "",
                fileExists ? "注意：当前目录已有 " + PROJECT_FILE + " 文件，请在此基础上优化。" : ""
        );
    }
}
