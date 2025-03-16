  midi-clj
==============

#### A streamlined midi API for Clojure

midi-clj is forked from Project Overtone, and it is meant to simplify the code and add features

Mainly Midi Timecode support. This creates an exposed atom called `!vars` which contains current time information

I've also made the decision to remove all Java UI elements from this library, as I believe that any UI should exist outside of this library


    (use 'midi)

    ; Once you know the correct device names for your devices you can save the
    ; step of opening up the GUI chooser by putting a unique part of the name
    ; as an argument to midi-in or midi-out.  The first device with a name that
    ; matches with lookup is returned.
    (def ax (midi-in "axiom"))

    ; Connect ins and outs easily
    (midi-route keyboard phat-synth)

    ; Trigger a note (note 40, velocity 100)
    (midi-note-on phat-synth 40 100)
    (Thread/sleep 500)
    (midi-note-off phat-synth 0)

    ; Or the short-hand version to start and stop a note
    (midi-note phat-synth 40 100 500)

    ; And the same thing with a sequence of notes
    (midi-play phat-synth [40 47 40] [80 50 110] [250 500 250])



### Project Info:

I have not uploaded this fork anywhere yet, so it cannot just be added to yout `deps.edn` file or `project.clj`

#### Source Repository
Downloads and the source repository can be found on GitHub:

  http://github.com/zbear0808/midi-clj



### Authors

* Jeff Rose
* Zubair Ahmed
