package io.leavesfly.koder.agent.executor;

import io.leavesfly.koder.agent.AgentConfig;
import io.leavesfly.koder.agent.AgentRegistry;

import io.leavesfly.koder.tool.Tool;
import io.leavesfly.koder.tool.ToolResponse;
import io.leavesfly.koder.tool.ToolUseContext;
import io.leavesfly.koder.tool.executor.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 代理执行器
 * 负责代理的执行和工具权限控制
 */
@Slf4j
@Service
@RequiredArgsConstructor

//todo
public class AgentExecutor {

    private final AgentRegistry agentRegistry;
    private final ToolExecutor toolExecutor;

    //todo

}
