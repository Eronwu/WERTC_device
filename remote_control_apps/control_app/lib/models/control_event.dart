import 'package:json_annotation/json_annotation.dart';

part 'control_event.g.dart';

@JsonSerializable()
class ControlEvent {
  final String type;
  final double x;
  final double y;
  final double? endX;
  final double? endY;
  final int? duration;
  final int timestamp;

  ControlEvent({
    required this.type,
    required this.x,
    required this.y,
    this.endX,
    this.endY,
    this.duration,
    required this.timestamp,
  });

  factory ControlEvent.fromJson(Map<String, dynamic> json) => _$ControlEventFromJson(json);
  Map<String, dynamic> toJson() => _$ControlEventToJson(this);

  factory ControlEvent.click(double x, double y) {
    return ControlEvent(
      type: 'click',
      x: x,
      y: y,
      timestamp: DateTime.now().millisecondsSinceEpoch,
    );
  }

  factory ControlEvent.swipe(double startX, double startY, double endX, double endY, int duration) {
    return ControlEvent(
      type: 'swipe',
      x: startX,
      y: startY,
      endX: endX,
      endY: endY,
      duration: duration,
      timestamp: DateTime.now().millisecondsSinceEpoch,
    );
  }
}

