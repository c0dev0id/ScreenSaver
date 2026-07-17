# Automatic Screen Handling for Motorcycle Navigation Systems

Situation:
Motorcycle Navigation Tablets have power hungry high performance screens.
They are operated outside in bright sunlight.
While riding, they're powered by the motorcycle.
Automatic brightness works well for day/night ride.

Issues:
When the motorcycle is turned off, the screen stays on on full power in sunlight.
The battery depletes within 30 minutes.

This app can solve it, by implementing auto brightness limits and a auto off timer that react on AC power state changes.

While on AC:
- allow auto brightness to reach 100% screen brightness
- turn screen on

When switched to battery:
- limit auto brightness to 80% (configurable) screen brightness
- option to turn screen off after a configurable amount of minutes after AC loss. 
