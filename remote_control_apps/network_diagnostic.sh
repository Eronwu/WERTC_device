#!/bin/bash

# 网络连接诊断脚本
# 用于诊断手机端控制应用无法连接到设备应用的问题

echo "=== 网络连接诊断脚本 ==="
echo "时间: $(date)"
echo ""

# 设备信息
DEVICE_ADB_ID="67188a9846568f84"  # 设备端
PHONE_ADB_ID="RFCX5096TGF"       # 手机端
DEVICE_IP="192.168.10.11"
EXPECTED_PORT="4321"

echo "设备信息:"
echo "- 设备端 ADB ID: $DEVICE_ADB_ID"
echo "- 手机端 ADB ID: $PHONE_ADB_ID"
echo "- 设备IP: $DEVICE_IP"
echo "- 期望端口: $EXPECTED_PORT"
echo ""

# 检查ADB连接
echo "=== 1. 检查ADB连接状态 ==="
adb devices
echo ""

# 检查设备端WebSocket服务状态
echo "=== 2. 检查设备端WebSocket服务状态 ==="
echo "检查设备端logcat中的WebSocketService信息:"
adb -s $DEVICE_ADB_ID logcat -d | grep -i websocket | tail -10
echo ""

# 检查设备端端口监听状态
echo "=== 3. 检查设备端端口监听状态 ==="
echo "检查端口$EXPECTED_PORT是否在监听:"
adb -s $DEVICE_ADB_ID shell netstat -an | grep $EXPECTED_PORT
echo ""
echo "检查所有监听的端口:"
adb -s $DEVICE_ADB_ID shell netstat -an | grep LISTEN | grep -E ":[0-9]+" | head -10
echo ""

# 检查设备端网络接口
echo "=== 4. 检查设备端网络接口 ==="
echo "设备端IP地址信息:"
adb -s $DEVICE_ADB_ID shell ip addr show | grep -E "inet.*192\.168"
echo ""

# 从手机端测试网络连通性
echo "=== 5. 从手机端测试网络连通性 ==="
echo "Ping测试 ($DEVICE_IP):"
adb -s $PHONE_ADB_ID shell ping -c 3 $DEVICE_IP 2>/dev/null || echo "Ping失败"
echo ""

echo "Telnet测试端口连通性 ($DEVICE_IP:$EXPECTED_PORT):"
timeout 5 adb -s $PHONE_ADB_ID shell "echo 'test' | nc $DEVICE_IP $EXPECTED_PORT" 2>/dev/null && echo "端口连通" || echo "端口不通或连接被拒绝"
echo ""

# 检查手机端网络配置
echo "=== 6. 检查手机端网络配置 ==="
echo "手机端IP地址信息:"
adb -s $PHONE_ADB_ID shell ip addr show | grep -E "inet.*192\.168"
echo ""
echo "手机端代理设置:"
adb -s $PHONE_ADB_ID shell settings get global http_proxy
echo ""

# 检查手机端应用日志
echo "=== 7. 检查手机端应用日志 ==="
echo "最近的连接错误日志:"
adb -s $PHONE_ADB_ID logcat -d | grep -E "(SocketException|WebSocket|No route to host|Connection refused)" | tail -5
echo ""

# 实时监控连接尝试
echo "=== 8. 实时监控连接尝试 ==="
echo "开始实时监控手机端连接尝试（按Ctrl+C停止）:"
echo "请在手机应用中点击扫描设备..."
adb -s $PHONE_ADB_ID logcat -c  # 清除日志
sleep 2
echo "监控中..."
adb -s $PHONE_ADB_ID logcat head | grep -E "(DeviceDiscoveryService|WebSocket|SocketException|Connection)" &
LOGCAT_PID=$!

# 等待用户操作
echo "请在手机应用中扫描设备，然后按任意键继续..."
read -n 1 -s

# 停止监控
kill $LOGCAT_PID 2>/dev/null
echo ""
echo "监控结束"
echo ""

# 诊断建议
echo "=== 9. 诊断建议 ==="
echo "根据以上信息，请检查:"
echo "1. 设备端WebSocket服务是否正常启动并监听0.0.0.0:4321"
echo "2. 两个设备是否在同一网段(192.168.10.x)"
echo "3. 是否存在防火墙或代理设置阻止连接"
echo "4. 端口是否被重定向到其他端口(如49962)"
echo ""
echo "如果发现端口重定向问题，请运行:"
echo "adb -s $DEVICE_ADB_ID shell settings put global http_proxy :0"
echo ""
echo "=== 诊断完成 ==="
