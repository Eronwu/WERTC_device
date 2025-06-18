# 网络连接问题诊断指南

## 问题描述
手机端控制应用无法发现设备应用，出现"Connection refused"错误，并且发现端口重定向问题（期望4321端口，实际连接到41414端口）。

## 设备信息更正
- 手机端（控制应用）: adb:RFCX5096TGF
- 设备端（DeviceApp）: adb:67188a9846568f84
- 设备IP应为: 192.168.10.11

## 已修复的问题
1. **WebSocket服务器绑定问题**: 修改了设备应用的WebSocketService，现在绑定到所有网络接口(0.0.0.0:4321)而不是仅localhost
2. **增加了详细的网络调试日志**: 在设备应用启动时会显示所有可用的网络接口
3. **改进了客户端错误日志**: 控制应用现在会记录具体的网络连接错误

## 诊断步骤

### 1. 确认设备应用状态
在设备应用(67188a9846568f84)上检查logcat输出:
```bash
adb -s 67188a9846568f84 logcat | grep WebSocketService
```

应该看到类似输出:
```
WebSocketService: WebSocket server started on 0.0.0.0:4321
WebSocketService: Available network interface: wlan0 - 192.168.10.11
```

### 2. 检查端口重定向问题
首先确认设备应用实际监听的端口:
```bash
adb -s 67188a9846568f84 shell netstat -an | grep 4321
```

应该看到:
```
tcp6       0      0 :::4321                 :::*                    LISTEN
```

### 3. 确认网络连通性
在手机端(RFCX5096TGF)测试网络连接:
```bash
# 通过adb shell测试
adb -s RFCX5096TGF shell
ping -c 3 192.168.10.11
telnet 192.168.10.11 4321
```

### 3. 检查防火墙设置
确认设备没有防火墙阻止4321端口:
```bash
# 在设备上检查端口监听状态
adb -s RFCX5096TGF shell netstat -an | grep 4321
```

### 4. 重启服务
如果问题仍然存在，尝试重启WebSocket服务:
1. 在设备应用中停止并重新启动服务
2. 或者重启整个设备应用

## 常见解决方案

### 方案1: 解决端口重定向问题
如果发现WebSocket连接时端口从4321变为其他端口(如41414)，这通常是由于:
- 网络代理或防火墙重定向
- Android系统的网络安全策略
- WebSocket库的端口重映射

解决方法:
```bash
# 检查设备的网络配置
adb -s 67188a9846568f84 shell settings get global http_proxy
# 清除可能的代理设置
adb -s 67188a9846568f84 shell settings put global http_proxy :0
```

### 方案2: 重新编译设备应用
由于修改了WebSocketService的网络绑定，需要重新编译并安装设备应用:
```bash
cd /path/to/DeviceApp
./gradlew clean
./gradlew assembleDebug
adb -s 67188a9846568f84 install -r app/build/outputs/apk/debug/app-debug.apk
```

### 方案2: 检查网络配置
确认两个设备在同一个WiFi网络中:
- 设备IP: 192.168.10.117
- 手机IP: 应该在192.168.10.x范围内

### 方案3: 临时解决方案
如果问题持续，可以尝试:
1. 重启WiFi路由器
2. 重新连接WiFi网络
3. 使用静态IP地址

## 验证修复
修复后，在控制应用的日志中应该看到:
```
Attempting to connect to: ws://192.168.10.117:4321
Found 1 devices
```

而不是:
```
Network error connecting to 192.168.10.117:4321 - SocketException: No route to host
```