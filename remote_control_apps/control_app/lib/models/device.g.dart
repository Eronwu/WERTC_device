// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'device.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Device _$DeviceFromJson(Map<String, dynamic> json) => Device(
  id: json['id'] as String,
  name: json['name'] as String,
  ipAddress: json['ipAddress'] as String,
  port: (json['port'] as num).toInt(),
  isConnected: json['isConnected'] as bool? ?? false,
);

Map<String, dynamic> _$DeviceToJson(Device instance) => <String, dynamic>{
  'id': instance.id,
  'name': instance.name,
  'ipAddress': instance.ipAddress,
  'port': instance.port,
  'isConnected': instance.isConnected,
};
