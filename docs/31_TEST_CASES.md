# 31 — Test Cases

Representative test cases across major feature areas. Each should be logged with Expected Result / Actual Result / Pass-Fail during a test pass.

## Permissions
| TC | Steps | Expected Result |
|---|---|---|
| TC-01 | Fresh install, tap toggle | Permission dialogs appear in correct order (notifications → mic → battery) |
| TC-02 | Deny mic permission | Chaos Mode does not start; clear explanation shown |
| TC-03 | Deny notification permission (Android 13+) | Foreground service cannot start; feature disabled with message |
| TC-04 | Grant all permissions | Chaos Mode activates successfully |

## Chaos Mode Toggle
| TC | Steps | Expected Result |
|---|---|---|
| TC-05 | Tap toggle ON | Notification appears, processed audio audible within ~150ms of speaking |
| TC-06 | Tap toggle OFF | Notification clears, processing stops immediately |
| TC-07 | Rapid toggle ON/OFF x20 | No crash, no resource leak, consistent behavior each time |

## Presets
| TC | Steps | Expected Result |
|---|---|---|
| TC-08 | Select each built-in preset while active | Sound changes audibly and immediately for each |
| TC-09 | Save current settings as custom preset | Preset appears in picker, persists after app restart |
| TC-10 | Load corrupted custom preset | Falls back to nearest built-in default without crashing |

## Volume Boost
| TC | Steps | Expected Result |
|---|---|---|
| TC-11 | Set boost to 500% | Output audibly louder/more distorted, not silent/fully clipped flat |
| TC-12 | Toggle limiter off at high boost | Documented behavior change observed (more raw clipping) |

## Audio Focus
| TC | Steps | Expected Result |
|---|---|---|
| TC-13 | Receive a phone call while Chaos Mode active | Processing pauses cleanly, resumes or stops appropriately after call ends |

## Background Stability
| TC | Steps | Expected Result |
|---|---|---|
| TC-14 | Background app for 10+ minutes with Chaos Mode active | Session remains active, notification still present |
| TC-15 | Force-kill app via OS task manager | Service attempts restart per `START_STICKY`; returns to Stopped state, does not silently resume audio |

## VoIP Compatibility
| TC | Steps | Expected Result |
|---|---|---|
| TC-16 | Activate Chaos Mode during a live WhatsApp call | Other party audibly hears the processed effect |
| TC-17 | Repeat for Discord, Telegram, game voice chat | Consistent behavior per `29_COMPATIBILITY.md` matrix |
