# Koder - Java重构版

基于Java 17 + Spring Boot 3.x + Maven重构的Kode终端AI助手项目。

## 项目结构

```
Koder/
├── koder-core/      # 核心模块（配置、权限、上下文、消息）
├── koder-models/    # 模型适配模块（适配器工厂、提供商实现）
├── koder-tools/     # 工具系统模块（工具接口、内置工具实现）
├── koder-cli/       # CLI交互模块（REPL、命令系统、终端渲染）
├── koder-mcp/       # MCP集成模块（客户端、传输协议）
└── koder-agent/     # 智能代理模块（加载器、执行器）
```

## 技术栈

- **Java**: 17
- **构建工具**: Maven 3.9+
- **框架**: Spring Boot 3.2+
- **终端UI**: JLine 3.x
- **JSON处理**: Jackson 2.x
- **HTTP客户端**: Spring WebClient
- **日志**: SLF4J + Logback

## 快速开始

### 1. 编译项目

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
cd Koder
mvn clean compile -DskipTests
```

### 2. 运行Koder

**方式一：单个JAR文件（推荐、便携）** ✨

```bash
# 首次需要打包
./build-jar.sh

# 运行
java -jar koder-cli/target/koder.jar
# 或使用快捷脚本
./run-jar.sh
```

打包后的JAR文件（~50MB）可以独立运行，无需其他依赖，可复制到任何机器使用：

```bash
# 复制JAR到目标机器
cp koder-cli/target/koder.jar ~/bin/

# 直接运行
java -jar ~/bin/koder.jar
```

**方式二：系统级安装（全局命令）** 🚀

```bash
# 打包并安装
./build-jar.sh
./install.sh

# 在任何目录下运行
koder
```

安装后，`koder`命令将可在任何目录下使用，如同系统命令。

**方式三：使用快速启动脚本（开发）**

```bash
./run-koder.sh
```

**方式四：使用Maven插件（开发）**

```bash
mvn spring-boot:run -pl koder-cli -DskipTests
```

### 3. 使用Koder

启动后进入REPL界面：

```
╔══════════════════════════════════════════╗
║        Koder - AI编程助手 (Java版)      ║
╚══════════════════════════════════════════╝

输入 /help 查看帮助
输入 /exit 退出程序

koder> 
```

可用命令：
- `/help` - 显示帮助信息
- `/model` - 查看或切换AI模型
- `/agents` - 管理智能代理
- `/config` - 配置管理
- `/mcp` - MCP服务器管理
- `/exit` - 退出程序

## 模块集成

详细的模块集成说明请参考 [MODULE_INTEGRATION.md](./MODULE_INTEGRATION.md)

### 集成架构

```
KoderCliApplication (启动入口)
  ↓
ModuleIntegrationInitializer (Order=1)
  ├→ 初始化工具系统 (ToolExecutor)
  ├→ 初始化代理系统 (AgentRegistry)
  ├→ 初始化MCP系统 (MCPClientManager)
  └→ 验证集成完整性
  ↓
REPLEngine (启动REPL)
  ├→ CommandRegistry (命令路由)
  ├→ AIQueryService (AI查询)
  └→ TerminalRenderer (终端渲染)
```

### 核心组件

- **ToolExecutor**: 管理所有工具，支持动态注册
- **AgentRegistry**: 加载和管理智能代理
- **MCPClientManager**: 管理MCP服务器连接
- **CommandRegistry**: 注册和路由CLI命令
- **AIQueryService**: 处理AI查询和流式响应

## 构建项目

```bash
# 编译所有模块
mvn clean install

# 运行CLI
cd koder-cli
mvn spring-boot:run

# 打包可执行JAR
mvn clean package
```

## 包结构

所有代码统一使用 `io.leavesfly.koder` 包路径：

- `io.leavesfly.koder.core` - 核心功能
- `io.leavesfly.koder.model` - 模型适配
- `io.leavesfly.koder.tool` - 工具系统
- `io.leavesfly.koder.cli` - CLI交互
- `io.leavesfly.koder.mcp` - MCP集成
- `io.leavesfly.koder.agent` - 智能代理

## 开发说明

### 环境要求

- JDK 17+
- Maven 3.9+

### IDE推荐配置

- 启用Lombok注解处理器
- 配置代码格式化为Java标准
- 启用Java 17语法支持（Record、Switch表达式等）

## 配置文件

### 全局配置

位置：`~/.koder.json`

包含模型配置、主题设置、MCP服务器等全局设置。

### 项目配置

位置：`.koder.json`

包含工具授权、上下文文件、项目级MCP配置等。

## License

Apache-2.0
