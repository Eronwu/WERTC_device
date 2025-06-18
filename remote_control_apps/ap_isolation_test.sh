#!/bin/bash

# AP隔离测试和解决方案脚本

echo "=== AP隔离问题诊断和解决方案 ==="
echo "时间: $(date)"
echo ""

# 设备信息
DEVICE_ADB_ID="67188a9846568f84"  # 设备端
PHONE_ADB_ID="RFCX5096TGF"       # 手机端
DEVICE_IP="192.168.10.11"
PHONE_IP="192.168.10.123"
PORT="4321"

echo "设备信息:"
echo "- 设备端: $DEVICE_ADB_ID ($DEVICE_IP)"
echo "- 手机端: $PHONE_ADB_ID ($PHONE_IP)"
echo "- WebSocket端口: $PORT"
echo ""

# 测试1: 基本连通性
echo "=== 测试1: 基本网络连通性 ==="
echo "从手机端ping设备端:"
adb -s $PHONE_ADB_ID shell ping -c 2 $DEVICE_IP 2>/dev/null
PING_RESULT=$?

if [ $PING_RESULT -eq 0 ]; then
    echo "✅ Ping成功 - 网络连通正常"
else
    echo "❌ Ping失败 - 可能存在AP隔离"
fi
echo ""

# 测试2: 端口连通性
echo "=== 测试2: WebSocket端口连通性 ==="
echo "测试端口 $DEVICE_IP:$PORT:"
timeout 3 adb -s $PHONE_ADB_ID shell "echo 'test' | nc $DEVICE_IP $PORT" 2>/dev/null
NC_RESULT=$?

if [ $NC_RESULT -eq 0 ]; then
    echo "✅ 端口连通 - WebSocket服务可访问"
else
    echo "❌ 端口不通 - WebSocket服务不可访问"
fi
echo ""

# 测试3: 检查WebSocket服务状态
echo "=== 测试3: WebSocket服务状态 ==="
echo "检查设备端WebSocket服务:"
WS_STATUS=$(adb -s $DEVICE_ADB_ID shell netstat -an | grep $PORT)
if [ -n "$WS_STATUS" ]; then
    echo "✅ WebSocket服务正在监听端口$PORT"
    echo "$WS_STATUS"
else
    echo "❌ WebSocket服务未在端口$PORT监听"
fi
echo ""

# 诊断结果
echo "=== 诊断结果 ==="
if [ $PING_RESULT -ne 0 ] && [ -n "$WS_STATUS" ]; then
    echo "🔍 诊断: WiFi AP隔离问题"
    echo "   - WebSocket服务正常运行"
    echo "   - 设备间无法直接通信"
    echo "   - 这是典型的AP隔离症状"
    echo ""
    
    echo "=== 解决方案 ==="
    echo "方案1 (推荐): 禁用WiFi路由器AP隔离"
    echo "   1. 登录路由器管理界面 (通常是 http://192.168.10.1)"
    echo "   2. 查找'AP隔离'、'客户端隔离'或'Station Isolation'设置"
    echo "   3. 禁用该功能"
    echo "   4. 重启路由器"
    echo ""
    
    echo "方案2 (临时): 使用热点模式"
    echo "   1. 在设备端开启WiFi热点"
    echo "   2. 手机端连接到设备热点"
    echo "   3. 重新测试连接"
    echo ""
    
    echo "方案3 (测试): 尝试通过路由器中转"
    echo "   - 某些情况下，通过路由器IP可能可以访问"
    echo "   - 但这通常不适用于P2P应用"
    
elif [ $PING_RESULT -eq 0 ] && [ $NC_RESULT -ne 0 ]; then
    echo "🔍 诊断: 防火墙或端口阻塞问题"
    echo "   - 基本网络连通正常"
    echo "   - 特定端口被阻塞"
    echo ""
    
    echo "=== 解决方案 ==="
    echo "1. 检查设备端防火墙设置"
    echo "2. 尝试使用其他端口"
    echo "3. 检查应用权限设置"
    
elif [ $PING_RESULT -eq 0 ] && [ $NC_RESULT -eq 0 ]; then
    echo "✅ 网络连接正常"
    echo "   问题可能在应用层面，建议:"
    echo "   1. 检查应用日志"
    echo "   2. 重启应用服务"
    echo "   3. 清除应用缓存"
    
else
    echo "❌ 复杂网络问题"
    echo "   建议联系网络管理员或使用其他网络环境测试"
fi
echo ""

# 提供快速测试命令
echo "=== 快速测试命令 ==="
echo "重新测试连通性:"
echo "adb -s $PHONE_ADB_ID shell ping -c 3 $DEVICE_IP"
echo ""
echo "重新测试端口:"
echo "adb -s $PHONE_ADB_ID shell 'echo test | nc $DEVICE_IP $PORT'"
echo ""
echo "检查WebSocket日志:"
echo "adb -s $DEVICE_ADB_ID logcat | grep -i websocket"
echo ""
echo "=== 测试完成 ==="