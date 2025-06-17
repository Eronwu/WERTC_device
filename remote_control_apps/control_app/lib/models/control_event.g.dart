// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'control_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ControlEvent _$ControlEventFromJson(Map<String, dynamic> json) => ControlEvent(
  type: json['type'] as String,
  x: (json['x'] as num).toDouble(),
  y: (json['y'] as num).toDouble(),
  endX: (json['endX'] as num?)?.toDouble(),
  endY: (json['endY'] as num?)?.toDouble(),
  duration: (json['duration'] as num?)?.toInt(),
  timestamp: (json['timestamp'] as num).toInt(),
);

Map<String, dynamic> _$ControlEventToJson(ControlEvent instance) =>
    <String, dynamic>{
      'type': instance.type,
      'x': instance.x,
      'y': instance.y,
      'endX': instance.endX,
      'endY': instance.endY,
      'duration': instance.duration,
      'timestamp': instance.timestamp,
    };
