import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:network_info_plus/network_info_plus.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import '../models/device.dart';

class DeviceDiscoveryService {
  static const int _defaultPort = 4321;
  static const int _timeoutSeconds = 2;
  
  final NetworkInfo _networkInfo = NetworkInfo();
  final StreamController<Device> _deviceController = StreamController<Device>.broadcast();
  
  Stream<Device> get deviceStream => _deviceController.stream;
  
  Future<List<Device>> discoverDevices() async {
    final List<Device> devices = [];
    
    try {
      // Get current network info
      final wifiIP = await _networkInfo.getWifiIP();
      if (wifiIP == null) {
        debugPrint('No WiFi connection found');
        return devices;
      }
      
      debugPrint('Current IP: $wifiIP');
      
      // Extract network prefix (e.g., 192.168.1.x)
      final ipParts = wifiIP.split('.');
      if (ipParts.length != 4) {
        debugPrint('Invalid IP format');
        return devices;
      }
      
      final networkPrefix = '${ipParts[0]}.${ipParts[1]}.${ipParts[2]}';
      debugPrint('Scanning network: $networkPrefix.x');
      
      // Scan IP range
      final List<Future<Device?>> scanTasks = [];
      for (int i = 1; i <= 254; i++) {
        final targetIP = '$networkPrefix.$i';
        scanTasks.add(_scanDevice(targetIP, _defaultPort));
      }
      
      // Wait for all scans to complete
      final results = await Future.wait(scanTasks);
      
      // Filter out null results
      for (final device in results) {
        if (device != null) {
          devices.add(device);
          _deviceController.add(device);
        }
      }
      
      debugPrint('Found ${devices.length} devices');
      
    } catch (e) {
      debugPrint('Error during device discovery: $e');
    }
    
    return devices;
  }
  
  Future<Device?> _scanDevice(String ipAddress, int port) async {
    try {
      // Create WebSocket connection
      final uri = Uri.parse('ws://$ipAddress:$port');
      final channel = WebSocketChannel.connect(uri);
      
      // Send a simple ping message
      channel.sink.add('{"type":"ping"}');
      
      // Wait for response with timeout
      final response = await channel.stream.first.timeout(
        const Duration(seconds: _timeoutSeconds),
      );
      
      // Close the connection
      await channel.sink.close();
      
      // Parse device info from response
      try {
        final responseStr = response.toString();
        final json = jsonDecode(responseStr);
        if (json['type'] == 'device_info') {
          return Device(
            id: json['device_id'] ?? ipAddress,
            name: json['device_name'] ?? 'Unknown Device',
            ipAddress: ipAddress,
            port: port,
          );
        }
      } catch (e) {
        debugPrint('Error parsing device response: $e');
      }
      
    } catch (e) {
      // Connection failed - device not available
      // This is expected for most IPs, so we don't log it
    }
    
    return null;
  }
  
  void dispose() {
    _deviceController.close();
  }
}

