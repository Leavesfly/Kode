package io.leavesfly.koder.cli.repl;

import io.leavesfly.koder.cli.command.CommandContext;
import io.leavesfly.koder.cli.command.CommandRegistry;
import io.leavesfly.koder.cli.command.CommandResult;
import io.leavesfly.koder.cli.service.AIQueryService;
import io.leavesfly.koder.cli.terminal.TerminalRenderer;
import io.leavesfly.koder.core.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jline.reader.*;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * REPL引擎
 * 负责读取-求值-打印循环的核心逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REPLEngine {

    private final CommandRegistry commandRegistry;
    private final TerminalRenderer renderer;
    private final AIQueryService aiQueryService;

    private REPLSession session;
    private LineReader lineReader;
    private boolean running = false;

    /**
     * 启动REPL
     */
    public void start() {
        // 初始化会话
        session = new REPLSession(UUID.randomUUID().toString());

        // 创建行读取器
        lineReader = LineReaderBuilder.builder()
                .terminal(renderer.getTerminal())
                .build();

        // 显示欢迎信息
        showWelcome();

        // 主循环
        running = true;
        mainLoop();
    }

    /**
     * 主循环
     */
    private void mainLoop() {
        while (running) {
            try {
                // 读取输入
                String input = readInput();

                if (input == null || input.trim().isEmpty()) {
                    continue;
                }

                // 处理输入
                boolean shouldExit = processInput(input);

                if (shouldExit) {
                    break;
                }

            } catch (UserInterruptException e) {
                // Ctrl+C - 取消当前操作
                if (session.isLoading()) {
                    renderer.printWarning("\n操作已取消");
                    session.setLoading(false);
                } else {
                    renderer.printInfo("\n使用 /exit 或 Ctrl+D 退出");
                }
            } catch (EndOfFileException e) {
                // Ctrl+D - 退出
                break;
            } catch (Exception e) {
                log.error("REPL错误", e);
                renderer.printError("发生错误: " + e.getMessage());
            }
        }

        renderer.printSuccess("\n再见！");
    }

    /**
     * 读取用户输入
     */
    private String readInput() {
        String prompt = session.isLoading() ? "..." : "koder> ";
        return lineReader.readLine(prompt);
    }

    /**
     * 处理输入
     */
    private boolean processInput(String input) {
        // 检查是否为命令
        if (commandRegistry.isCommand(input)) {
            return executeCommand(input);
        } else {
            return handleUserMessage(input);
        }
    }

    /**
     * 执行命令
     */
    private boolean executeCommand(String input) {
        CommandRegistry.CommandInput commandInput = commandRegistry.parse(input);

        if (commandInput == null) {
            renderer.printError("无效的命令格式");
            return false;
        }

        var commandOpt = commandRegistry.find(commandInput.getCommandName());

        if (commandOpt.isEmpty()) {
            renderer.printError("未知命令: /" + commandInput.getCommandName());
            renderer.printInfo("使用 /help 查看可用命令");
            return false;
        }

        // 构建命令上下文
        CommandContext context = CommandContext.builder()
                .args(commandInput.getArgs())
                .rawInput(input)
                .session(session)
                .output(new TerminalOutputImpl())
                .build();

        // 执行命令
        CommandResult result = commandOpt.get().execute(context);

        // 处理结果
        if (!result.isSuccess() && result.getMessage() != null) {
            renderer.printError(result.getMessage());
        }

        if (result.isShouldClearScreen()) {
            renderer.clearScreen();
            session.clearMessages();
        }

        return result.isShouldExit();
    }

    /**
     * 处理用户消息
     */
    private boolean handleUserMessage(String input) {
        // 设置加载状态
        session.setLoading(true);

        try {
            // 准备系统提示词
            String systemPrompt = buildSystemPrompt();

            // 显示思考提示
            renderer.printInfo("\n正在思考...");

            // 用于收集完整响应
            StringBuilder fullResponse = new StringBuilder();
            AtomicBoolean hasError = new AtomicBoolean(false);

            // 调用AI查询
            aiQueryService.query(input, session, systemPrompt)
                    .doOnNext(response -> {
                        switch (response.getType()) {
                            case TEXT -> {
                                // 显示文本响应
                                renderer.println(response.getContent());
                                fullResponse.append(response.getContent());
                            }
                            case TOOL_USE -> {
                                // 显示工具调用
                                renderer.printInfo("\n[调用工具: " + response.getToolName() + "]");
                            }
                            case THINKING -> {
                                // 显示思考过程（如果启用详细模式）
                                if (session.isVerbose()) {
                                    renderer.printWarning("[思考] " + response.getContent());
                                }
                            }
                            case COMPLETE -> {
                                // 完成
                                renderer.println("");
                            }
                        }
                    })
                    .doOnError(error -> {
                        hasError.set(true);
                        renderer.printError("\n错误: " + error.getMessage());
                        log.error("AI查询失败", error);
                    })
                    .doOnComplete(() -> {
                        if (!hasError.get() && fullResponse.length() == 0) {
                            renderer.printWarning("\n(无响应)");
                        }
                    })
                    .blockLast(); // 阻塞等待完成

        } catch (Exception e) {
            renderer.printError("\n发生错误: " + e.getMessage());
            log.error("处理用户消息失败", e);
        } finally {
            session.setLoading(false);
        }

        return false;
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return """
                你是Koder，一个智能的AI编程助手。
                
                你的能力：
                - 理解和分析代码
                - 编写和修改代码文件
                - 执行shell命令
                - 搜索文件和内容
                - 提供编程建议
                
                当前工作目录: """ + session.getWorkingDirectory() + """
                
                请提供清晰、准确、有帮助的回答。
                """;
    }

    /**
     * 显示欢迎信息
     */
    private void showWelcome() {
        renderer.println("\n" + 
            "╔══════════════════════════════════════════╗\n" +
            "║        Koder - AI编程助手 (Java版)      ║\n" +
            "╚══════════════════════════════════════════╝\n");
        renderer.printInfo("输入 /help 查看帮助");
        renderer.printInfo("输入 /exit 退出程序\n");
    }

    /**
     * 停止REPL
     */
    public void stop() {
        running = false;
    }

    /**
     * TerminalOutput实现
     */
    private class TerminalOutputImpl implements CommandContext.TerminalOutput {
        @Override
        public void println(String message) {
            renderer.println(message);
        }

        @Override
        public void success(String message) {
            renderer.printSuccess(message);
        }

        @Override
        public void error(String message) {
            renderer.printError(message);
        }

        @Override
        public void warning(String message) {
            renderer.printWarning(message);
        }
    }
}
