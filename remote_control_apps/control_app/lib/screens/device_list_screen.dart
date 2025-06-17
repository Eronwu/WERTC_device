import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/device.dart';
import '../services/device_discovery_service.dart';
import '../widgets/device_list_item.dart';
import 'control_screen.dart';

class DeviceListScreen extends StatefulWidget {
  const DeviceListScreen({Key? key}) : super(key: key);

  @override
  State<DeviceListScreen> createState() => _DeviceListScreenState();
}

class _DeviceListScreenState extends State<DeviceListScreen> {
  final DeviceDiscoveryService _discoveryService = DeviceDiscoveryService();
  List<Device> _devices = [];
  bool _isScanning = false;

  @override
  void initState() {
    super.initState();
    _discoveryService.deviceStream.listen((device) {
      setState(() {
        _devices.add(device);
      });
    });
    _startScan();
  }

  @override
  void dispose() {
    _discoveryService.dispose();
    super.dispose();
  }

  Future<void> _startScan() async {
    setState(() {
      _isScanning = true;
      _devices.clear();
    });

    try {
      final devices = await _discoveryService.discoverDevices();
      setState(() {
        _devices = devices;
      });
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error scanning for devices: $e')),
      );
    } finally {
      setState(() {
        _isScanning = false;
      });
    }
  }

  void _connectToDevice(Device device) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => ControlScreen(device: device),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Available Devices'),
        actions: [
          IconButton(
            icon: _isScanning 
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.refresh),
            onPressed: _isScanning ? null : _startScan,
          ),
        ],
      ),
      body: Column(
        children: [
          if (_isScanning)
            const LinearProgressIndicator(),
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Text(
              'Found ${_devices.length} device(s)',
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ),
          Expanded(
            child: _devices.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.devices,
                          size: 64,
                          color: Colors.grey[400],
                        ),
                        const SizedBox(height: 16),
                        Text(
                          _isScanning 
                              ? 'Scanning for devices...'
                              : 'No devices found',
                          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                            color: Colors.grey[600],
                          ),
                        ),
                        if (!_isScanning) ...[
                          const SizedBox(height: 8),
                          Text(
                            'Make sure the device app is running\nand connected to the same network',
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: Colors.grey[500],
                            ),
                          ),
                        ],
                      ],
                    ),
                  )
                : ListView.builder(
                    itemCount: _devices.length,
                    itemBuilder: (context, index) {
                      final device = _devices[index];
                      return DeviceListItem(
                        device: device,
                        onTap: () => _connectToDevice(device),
                      );
                    },
                  ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _isScanning ? null : _startScan,
        child: const Icon(Icons.search),
      ),
    );
  }
}

