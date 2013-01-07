# Overtone midi-clj Change Log

## Version 0.5.0 (7th Jan 2012)

* Added support for reading MIDI files via fns in overtone.midi.file
* Support stringed hex messages (such as "F0 7E 7F 06 01 F7") for sysex messages
* Prevent midi-handle-events from crashing on non-ShortMessage messages.
* Now throws an IllegalArgumentException in midi-in and midi-out if the device couldn't be resolved.
* MIDI sysex messages may now be received by supplying an extra function argument to midi-handle-events


## Version 0.4.0 (20th May 2012)

* changing properties of midi event messages to be full names
 - e.g. :vel => :velocity

* putting midi timestamp as a property in the event map, rather than
as a second argument to the midi-handle-events handler function.
