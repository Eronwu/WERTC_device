import 'package:json_annotation/json_annotation.dart';

part 'device.g.dart';

@JsonSerializable()
class Device {
  final String id;
  final String name;
  final String ipAddress;
  final int port;
  final bool isConnected;

  Device({
    required this.id,
    required this.name,
    required this.ipAddress,
    required this.port,
    this.isConnected = false,
  });

  factory Device.fromJson(Map<String, dynamic> json) => _$DeviceFromJson(json);
  Map<String, dynamic> toJson() => _$DeviceToJson(this);

  Device copyWith({
    String? id,
    String? name,
    String? ipAddress,
    int? port,
    bool? isConnected,
  }) {
    return Device(
      id: id ?? this.id,
      name: name ?? this.name,
      ipAddress: ipAddress ?? this.ipAddress,
      port: port ?? this.port,
      isConnected: isConnected ?? this.isConnected,
    );
  }

  @override
  String toString() {
    return 'Device(id: $id, name: $name, ip: $ipAddress:$port, connected: $isConnected)';
  }
}

