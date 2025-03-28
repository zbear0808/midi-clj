  midi-clj Now with TIMECODE
==============

#### A streamlined midi API for Clojure

midi-clj is forked from Project Overtone, and it is meant to simplify the code and add features

Mainly Midi Timecode support. This creates an exposed atom called `!vars` which contains current time information

I've also made the decision to remove all Java UI elements from this library, as I believe that any UI should exist outside of this library


    (use 'midi)

    ;; in this example "mtc" is the name of the midi port, on windows i'm using LoopMidi to create an open port
    (-> (midi-out "mtc")
        (start-mtc))
    ;; example code to move position and playback speed
    (swap! !vars assoc
         :speed-multiplier 2
         :base-millis 10000
         :steps 0)
    ;; example fn you might call after playback of your song is over
    (defn move-to-beginning []
      (swap! !vars assoc 
             :base-millis 0 
             :steps 0 
             :offset-millis 0 
             :paused? true))
        


  



### Project Info:

I have not uploaded this fork anywhere yet, so it cannot just be added to yout `deps.edn` file or `project.clj`, 
if anyone asks I'll learn how to use maven and upload it, but i'm lazy and don't wanna learn it if nobody is ever gonna use it other than me. 

#### Source Repository
Downloads and the source repository can be found on GitHub:

  http://github.com/zbear0808/midi-clj



### Authors

* Jeff Rose
* Zubair Ahmed
