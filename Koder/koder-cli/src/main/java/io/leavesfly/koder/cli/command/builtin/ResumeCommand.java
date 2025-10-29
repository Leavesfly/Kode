package io.leavesfly.koder.cli.command.builtin;

import io.leavesfly.koder.cli.command.Command;
import io.leavesfly.koder.cli.command.CommandContext;
import io.leavesfly.koder.cli.command.CommandResult;
import io.leavesfly.koder.cli.repl.REPLSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * resume命令 - 恢复之前的对话
 */
@Component
@RequiredArgsConstructor
public class ResumeCommand implements Command {

    private final REPLSession session;

    @Override
    public String getName() {
        return "resume";
    }

    @Override
    public String getDescription() {
        return "恢复之前的对话";
    }

    @Override
    public String getUsage() {
        return "/resume";
    }

    @Override
    public CommandResult execute(CommandContext context) {
        // TODO: 实现对话持久化和恢复功能
        // 需要：
        // 1. 保存对话到文件
        // 2. 列出可恢复的对话
        // 3. 加载选定的对话

        return CommandResult.success("\n对话恢复功能待实现\n可用的对话:\n  (暂无)\n");
    }
}
