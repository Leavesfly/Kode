#!/bin/bash

# Koder 快速参考

cat << 'EOF'
╔══════════════════════════════════════════════════════════════╗
║                  Koder - 快速参考                           ║
╚══════════════════════════════════════════════════════════════╝

📦 构建 & 打包
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  编译项目:       mvn clean compile -DskipTests
  打包JAR:        ./build-jar.sh
  完整构建:       mvn clean package -DskipTests

🚀 运行方式
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  JAR运行:        ./run-jar.sh
                  java -jar koder-cli/target/koder.jar

  Maven运行:      ./run-koder.sh
                  mvn spring-boot:run -pl koder-cli

  系统命令:       koder  (需先运行 ./install.sh)

💾 安装
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  系统级安装:     ./build-jar.sh && ./install.sh
  安装位置:       ~/.koder/koder.jar
  启动脚本:       ~/bin/koder

📝 开发
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  编译单模块:     mvn compile -pl koder-core
  清理构建:       mvn clean
  跳过测试:       -DskipTests

📂 重要文件
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  可执行JAR:      koder-cli/target/koder.jar (50MB)
  配置文件:       ~/.koder.json
  代理目录:       ~/.koder/agents/
  项目配置:       .koder.json

⚙️ 环境要求
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Java版本:       JDK 17+
  JAVA_HOME:      /Library/Java/JavaVirtualMachines/jdk-17.jdk
  Maven版本:      3.3.9+

🔧 常用命令
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  /help           显示帮助
  /model          模型管理
  /agents         代理管理
  /config         配置管理
  /mcp            MCP服务器
  /exit           退出程序

📚 文档
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  README.md               项目概述
  MODULE_INTEGRATION.md   模块集成说明
  INTEGRATION_REPORT.md   集成报告

EOF
