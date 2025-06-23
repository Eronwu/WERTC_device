#!/bin/bash

# Network Optimization Script for Ultra-Low Latency Remote Control
# This script helps optimize network settings for better performance

echo "=== Network Optimization for Ultra-Low Latency Remote Control ==="

# Function to check if running on macOS
check_macos() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        return 0
    else
        return 1
    fi
}

# Function to optimize network settings
optimize_network() {
    echo "Optimizing network settings..."
    
    if check_macos; then
        # macOS optimizations
        echo "Applying macOS network optimizations..."
        
        # Increase UDP buffer sizes
        sudo sysctl -w kern.ipc.maxsockbuf=8388608
        sudo sysctl -w kern.ipc.somaxconn=2048
        
        # Optimize TCP settings
        sudo sysctl -w net.inet.tcp.recvspace=65536
        sudo sysctl -w net.inet.tcp.sendspace=65536
        sudo sysctl -w net.inet.tcp.mssdflt=1448
        
        # Optimize UDP settings
        sudo sysctl -w net.inet.udp.recvspace=65536
        sudo sysctl -w net.inet.udp.sendspace=65536
        
        echo "macOS network optimizations applied"
    else
        # Linux optimizations
        echo "Applying Linux network optimizations..."
        
        # Increase UDP buffer sizes
        sudo sysctl -w net.core.rmem_max=8388608
        sudo sysctl -w net.core.wmem_max=8388608
        sudo sysctl -w net.core.rmem_default=65536
        sudo sysctl -w net.core.wmem_default=65536
        
        # Optimize TCP settings
        sudo sysctl -w net.core.netdev_max_backlog=5000
        sudo sysctl -w net.ipv4.tcp_rmem="4096 87380 8388608"
        sudo sysctl -w net.ipv4.tcp_wmem="4096 65536 8388608"
        
        echo "Linux network optimizations applied"
    fi
}

# Function to check network connectivity
check_connectivity() {
    echo "Checking network connectivity..."
    
    # Get local IP
    local_ip=$(ifconfig | grep "inet " | grep -v 127.0.0.1 | awk '{print $2}' | head -1)
    echo "Local IP: $local_ip"
    
    # Check WiFi connection
    if check_macos; then
        wifi_info=$(networksetup -getairportpower en0 2>/dev/null)
        if [[ $? -eq 0 ]]; then
            echo "WiFi Status: $wifi_info"
        fi
    else
        wifi_info=$(iwconfig 2>/dev/null | grep -i essid)
        if [[ $? -eq 0 ]]; then
            echo "WiFi Status: $wifi_info"
        fi
    fi
    
    # Test local network
    echo "Testing local network connectivity..."
    ping -c 3 8.8.8.8 > /dev/null 2>&1
    if [[ $? -eq 0 ]]; then
        echo "✓ Internet connectivity: OK"
    else
        echo "✗ Internet connectivity: FAILED"
    fi
}

# Function to provide optimization recommendations
provide_recommendations() {
    echo ""
    echo "=== Optimization Recommendations ==="
    echo "1. Ensure both devices are on the same WiFi network"
    echo "2. Disable WiFi AP isolation in router settings"
    echo "3. Use 5GHz WiFi if available (less interference)"
    echo "4. Position devices closer to WiFi router"
    echo "5. Close unnecessary network-intensive applications"
    echo "6. Consider using WiFi hotspot mode if AP isolation cannot be disabled"
    echo ""
    echo "=== WebRTC Optimizations Applied ==="
    echo "✓ Increased bitrate to 8 Mbps for better quality"
    echo "✓ Increased frame rate to 60 fps for lower latency"
    echo "✓ Optimized ICE connection timeouts"
    echo "✓ Added frame rate control to prevent buffer buildup"
    echo "✓ Configured VP8 codec for lower latency"
    echo "✓ Disabled CPU overuse detection"
    echo "✓ Optimized RTP parameters for screen sharing"
}

# Function to test WebRTC ports
test_webrtc_ports() {
    echo "Testing WebRTC-related ports..."
    
    # Test common WebRTC ports
    ports=(8080 8081 8082 8083 8084 8085 8086 8087 8088 8089 8090)
    
    for port in "${ports[@]}"; do
        if nc -z localhost $port 2>/dev/null; then
            echo "✓ Port $port: OPEN"
        else
            echo "✗ Port $port: CLOSED"
        fi
    done
}

# Main execution
main() {
    echo "Starting network optimization..."
    
    # Check if running with sudo
    if [[ $EUID -ne 0 ]]; then
        echo "Warning: Some optimizations require sudo privileges"
        echo "Run with sudo for full optimization: sudo $0"
        echo ""
    fi
    
    check_connectivity
    echo ""
    
    if [[ $EUID -eq 0 ]]; then
        optimize_network
        echo ""
    fi
    
    test_webrtc_ports
    echo ""
    
    provide_recommendations
    
    echo "=== Optimization Complete ==="
    echo "Please restart your applications for changes to take effect."
}

# Run main function
main "$@" 