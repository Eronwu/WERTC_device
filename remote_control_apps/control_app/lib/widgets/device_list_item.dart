import 'package:flutter/material.dart';
import '../models/device.dart';

class DeviceListItem extends StatelessWidget {
  final Device device;
  final VoidCallback onTap;

  const DeviceListItem({
    Key? key,
    required this.device,
    required this.onTap,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: device.isConnected ? Colors.green : Colors.blue,
          child: Icon(
            Icons.phone_android,
            color: Colors.white,
          ),
        ),
        title: Text(
          device.name,
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('${device.ipAddress}:${device.port}'),
            const SizedBox(height: 4),
            Row(
              children: [
                Icon(
                  device.isConnected ? Icons.check_circle : Icons.radio_button_unchecked,
                  size: 16,
                  color: device.isConnected ? Colors.green : Colors.grey,
                ),
                const SizedBox(width: 4),
                Text(
                  device.isConnected ? 'Connected' : 'Available',
                  style: TextStyle(
                    color: device.isConnected ? Colors.green : Colors.grey[600],
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ],
        ),
        trailing: const Icon(Icons.arrow_forward_ios),
        onTap: onTap,
      ),
    );
  }
}

