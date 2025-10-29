package io.leavesfly.koder.cli.command.builtin;

import io.leavesfly.koder.cli.command.Command;
import io.leavesfly.koder.cli.command.CommandContext;
import io.leavesfly.koder.cli.command.CommandResult;
import org.springframework.stereotype.Component;

/**
 * listen命令 - 启动语音输入模式
 */
@Component
public class ListenCommand implements Command {

    @Override
    public String getName() {
        return "listen";
    }

    @Override
    public String getDescription() {
        return "启动语音输入模式";
    }

    @Override
    public String getUsage() {
        return "/listen";
    }

    @Override
    public CommandResult execute(CommandContext context) {
        // TODO: 实现语音输入功能
        // 需要集成语音识别API
        return CommandResult.success("\n语音输入功能待实现\n");
    }
}
