(ns overtone.midi
  #^{:author "Jeff Rose"
     :doc "A higher-level API on top of the Java MIDI apis.  It makes
           it easier to configure midi input/output devices, route
           between devices, read/write control messages to devices,
           play notes, etc."}
    (:import
     (java.util.regex Pattern)
     (javax.sound.midi Sequencer Synthesizer
                       MidiSystem MidiDevice Receiver Transmitter 
                       ShortMessage SysexMessage 
                       MidiDevice$Info)
     (javax.swing JFrame JScrollPane JList
                  DefaultListModel ListSelectionModel)
     (java.awt.event MouseAdapter)
     (java.util.concurrent FutureTask )) 
  (:require [overtone.at-at :as at-at]
            [clojure.pprint :refer [cl-format]]))

; Java MIDI returns -1 when a port can support any number of transmitters or
; receivers, we use max int.
(def MAX-IO-PORTS Integer/MAX_VALUE)

(def midi-player-pool (at-at/mk-pool))

(def mtc-pool (at-at/mk-pool))

(defn midi-devices
  "Get all of the currently available midi devices."
  []
  (for [^MidiDevice$Info info (MidiSystem/getMidiDeviceInfo)]
    (let [device (MidiSystem/getMidiDevice info)
          n-tx   (.getMaxTransmitters device)
          n-rx   (.getMaxReceivers device)]
      (with-meta
        {:name         (.getName info)
         :description  (.getDescription info)
         :vendor       (.getVendor info)
         :version      (.getVersion info)
         :sources      (if (neg? n-tx) MAX-IO-PORTS n-tx)
         :sinks        (if (neg? n-rx) MAX-IO-PORTS n-rx)
         :info         info
         :device       device}
        {:type :midi-device}))))




(defn midi-device?
  "Check whether obj is a midi device."
  [obj]
  (= :midi-device (type obj)))

(defn midi-ports
  "Get the available midi I/O ports (hardware sound-card and virtual
  ports). NOTE: devices use -1 to signify unlimited sources or sinks."
  []
  (filter #(and (not (instance? Sequencer   (:device %1)))
                (not (instance? Synthesizer (:device %1))))
          (midi-devices)))

(defn midi-sources 
  "Get the midi input sources."
  [] 
  (filter #(not (zero? (:sources %1))) (midi-ports)))

(defn midi-sinks
  "Get the midi output sinks."
  []
  (filter #(not (zero? (:sinks %1))) (midi-ports)))

(defn midi-find-device
  "Takes a set of devices returned from either (midi-sources)
  or (midi-sinks), and a search string.  Returns the first device
  where either the name or description matches using the search string
  as a regexp."
  [devs dev-name]
  (first (filter
          #(let [pat (Pattern/compile dev-name Pattern/CASE_INSENSITIVE)]
             (or (re-find pat (:name %1))
                 (re-find pat (:description %1))))
          devs)))

(defn- list-model
  "Create a swing list model based on a collection."
  [items]
  (let [model (DefaultListModel.)]
    (doseq [item items]
      (.addElement model item))
    model))

(defn- midi-port-chooser
  "Brings up a GUI list of the provided midi ports and then calls
  handler with the port that was double clicked."
  [title ports]
  (let [frame   (JFrame. title)
        model   (list-model (for [port ports]
                              (str (:name port) " - " (:description port))))
        options (JList. model)
        pane    (JScrollPane. options)
        future-val (FutureTask. #(nth ports (.getSelectedIndex options)))
        listener (proxy [MouseAdapter] []
                   (mouseClicked
                     [event]
                     (if (= (.getClickCount event) 2)
                       (.setVisible frame false)
                       (.run future-val))))]
    (doto options
      (.addMouseListener listener)
      (.setSelectionMode ListSelectionModel/SINGLE_SELECTION))
    (doto frame
      (.add pane)
      (.pack)
      (.setSize 400 600)
      (.setVisible true))
    future-val))

(defn- with-receiver
  "Add a midi receiver to the sink device info. This is a connection
   from which the MIDI device will receive MIDI data"
  [sink-info]
  (let [^MidiDevice dev (:device sink-info)]
    (when (not (.isOpen dev))
      (.open dev))
    (assoc sink-info :receiver (.getReceiver dev))))

(defn- with-transmitter
  "Add a midi transmitter to the source info. This is a connection from
   which the MIDI device will transmit MIDI data."
  [source-info]
  (let [^MidiDevice dev (:device source-info)]
    (if (not (.isOpen dev))
      (.open dev))
    (assoc source-info :transmitter (.getTransmitter dev))))

(defn midi-in
  "Open a midi input device for reading.  If no argument is given then
  a selection list pops up to let you browse and select the midi
  device."
  ([] (with-transmitter
        (.get (midi-port-chooser "Midi Input Selector" (midi-sources)))))
  ([in]
   (let [source (cond
                  (string? in) (midi-find-device (midi-sources) in)
                  (midi-device? in) in)]
     (if source
       (with-transmitter source)
       (throw (IllegalArgumentException.
               (str "Did not find a matching midi input device for: " in)))))))

(defn midi-out
  "Open a midi output device for writing.  If no argument is given
  then a selection list pops up to let you browse and select the midi
  device."
  ([] (with-receiver
        (.get (midi-port-chooser "Midi Output Selector" (midi-sinks)))))

  ([out] (let [sink (cond
                      (string? out) (midi-find-device (midi-sinks) out)
                      (midi-device? out) out)]
           (if sink
             (with-receiver sink)
             (throw (IllegalArgumentException.
                     (str "Did not find a matching midi output device for: " out)))))))

(defn midi-route
  "Route midi messages from a source to a sink.  Expects transmitter
  and receiver objects returned from midi-in and midi-out."
  [source sink]
  (let [^Transmitter tran (:transmitter source)]
    (.setReceiver tran (:receiver sink))))

(def midi-shortmessage-status
  {ShortMessage/ACTIVE_SENSING         :active-sensing
   ShortMessage/CONTINUE               :continue
   ShortMessage/END_OF_EXCLUSIVE       :end-of-exclusive
   ShortMessage/MIDI_TIME_CODE         :midi-time-code
   ShortMessage/SONG_POSITION_POINTER  :song-position-pointer
   ShortMessage/SONG_SELECT            :song-select
   ShortMessage/START                  :start
   ShortMessage/STOP                   :stop
   ShortMessage/SYSTEM_RESET           :system-reset
   ShortMessage/TIMING_CLOCK           :timing-clock
   ShortMessage/TUNE_REQUEST           :tune-request})

(def midi-sysexmessage-status
  {SysexMessage/SYSTEM_EXCLUSIVE         :system-exclusive
   SysexMessage/SPECIAL_SYSTEM_EXCLUSIVE :special-system-exclusive})

(def midi-shortmessage-command
  {ShortMessage/CHANNEL_PRESSURE :channel-pressure
   ShortMessage/CONTROL_CHANGE   :control-change
   ShortMessage/NOTE_OFF         :note-off
   ShortMessage/NOTE_ON          :note-on
   ShortMessage/PITCH_BEND       :pitch-bend
   ShortMessage/POLY_PRESSURE    :poly-pressure
   ShortMessage/PROGRAM_CHANGE   :program-change})

(def midi-shortmessage-keys
  (merge midi-shortmessage-status midi-shortmessage-command))

;;
;; Note-off event:
;; MIDI may send both Note-On and Velocity 0 or Note-Off.
;;
;; http://www.jsresources.org/faq_midi.html#no_note_off
(defn midi-msg
  "Make a clojure map out of a midi ShortMessage object."
  [^ShortMessage obj & [ts]]
  (let [ch     (.getChannel obj)
        cmd    (.getCommand obj)
        d1     (.getData1 obj)
        d2     (.getData2 obj)
        status (.getStatus obj)]
    {:channel   ch
     :command   (if (and (= ShortMessage/NOTE_ON cmd)
                         (== 0 (.getData2 obj) 0))
                  :note-off
                  (midi-shortmessage-keys cmd))
     :msg       obj
     :note      d1
     :velocity  d2
     :data1     d1
     :data2     d2
     :status    (midi-shortmessage-keys status)
     :timestamp ts}))

(defn midi-handle-events
  "Specify handlers that will independently receive all MIDI events and
   sysex messages from the input device.  Both handlers should be a
   function of one argument, which will be a map of the message
   information"
  ([input short-msg-fn] (midi-handle-events input short-msg-fn (fn [sysex-msg] nil)))
  ([input short-msg-fn sysex-msg-fn]
   (let [receiver (proxy [Receiver] []
                    (close [] nil)
                    (send [msg timestamp] (cond (instance? ShortMessage msg)
                                                (short-msg-fn
                                                 (assoc (midi-msg msg timestamp)
                                                        :device input))

                                                (instance? SysexMessage msg)
                                                (sysex-msg-fn
                                                 {:timestamp timestamp
                                                  :data (.getData msg)
                                                  :status (.getStatus msg)
                                                  :length (.getLength msg)
                                                  :device input}))))]
     (.setReceiver (:transmitter input) receiver)
     receiver)))

(defn midi-send-msg
  [^Receiver sink msg timestamp]
  (.send sink msg timestamp))

(defn midi-note-on
  "Send a midi on msg to the sink."
  ([sink note-num vel]
   (midi-note-on sink note-num vel 0))
  ([sink note-num vel channel]
   (let [on-msg  (ShortMessage.)]
     (.setMessage on-msg ShortMessage/NOTE_ON channel note-num vel)
     (midi-send-msg (:receiver sink) on-msg -1))))

(defn midi-note-off
  "Send a midi off msg to the sink."
  ([sink note-num]
   (midi-note-off sink note-num 0))
  ([sink note-num channel]
   (let [off-msg (ShortMessage.)]
     (.setMessage off-msg ShortMessage/NOTE_OFF channel note-num 0)
     (midi-send-msg (:receiver sink) off-msg -1))))

(defn midi-control
  "Send a control msg to the sink"
  ([sink ctl-num val]
   (midi-control sink ctl-num val 0))
  ([sink ctl-num val channel]
   (let [ctl-msg (ShortMessage.)]
     (.setMessage ctl-msg ShortMessage/CONTROL_CHANGE channel ctl-num val)
     (midi-send-msg (:receiver sink) ctl-msg -1))))

(def hex-char-values (hash-map
                      \0 0 \1 1 \2 2 \3 3 \4 4 \5 5 \6 6 \7 7 \8 8 \9 9
                      \a 10 \b 11 \c 12 \d 13 \e 14 \f 15
                      \A 10 \B 11 \C 12 \D 13 \E 14 \F 15
                      \space \space \, \space \newline \space
                      \tab \space \formfeed \space \return \space))

(defn- not-space?
  [v]  (and
        (not= \space v)
        (not= \newline v)
        (not= \return v)
        (not= \tab v)))

(defn- byte-str-to-seq
  "Turn a case-insensitive string of hex bytes into a seq of integers.
  Bytes can optionally be delimited by commas or whitespace"
  [midi-str]
  (map #(Integer. (+ (* 16 (first %)) (second %)))
       (partition-all 2 (map hex-char-values (filter not-space? (seq midi-str))))))

(defn- byte-seq-to-array
  "Turn a seq of bytes into a native byte-array of 2s-complement values."
  [bseq]
  (let [ary (byte-array (count bseq))]
    (doseq [i (range (count bseq))]
      (aset-byte ary i (unchecked-byte (nth bseq i))))
    ary))

(defmulti midi-mk-byte-array
  (fn [byte-seq] (type (first byte-seq))))

(defmethod midi-mk-byte-array
  java.lang.Character
  [byte-seq]
  (byte-seq-to-array (byte-str-to-seq byte-seq)))

(defmethod midi-mk-byte-array java.lang.Long
  [byte-seq]
  (byte-seq-to-array (map #(Integer. %) byte-seq)))

(defmethod midi-mk-byte-array java.lang.Integer
  [byte-seq]
  (byte-seq-to-array (seq byte-seq)))

(defmethod midi-mk-byte-array :default
  [byte-seq]
  (byte-seq-to-array (seq byte-seq)))

(defn- midi-mk-sysex-msg [bytes]
  (let [bytes (if (= (type bytes) (type (byte-array 0)))
                bytes
                (midi-mk-byte-array (seq bytes)))
        sys-msg (SysexMessage.)]
    (.setMessage sys-msg bytes (count bytes))
    sys-msg))

(defn midi-sysex
  "Send a midi System Exclusive msg made up of the bytes in byte-seq
   byte-array, sequence of integers, longs or a byte-string to the sink.
   If a byte string is specified, must only contain bytes encoded as hex
   values.  Commas, spaces, and other whitespace is ignored"
  [sink byte-seq]
  (let [sysex (midi-mk-sysex-msg byte-seq)]
    (midi-send-msg (:receiver sink) sysex -1)))

(defn midi-note
  "Send a midi on/off msg pair to the sink."
  ([sink note-num vel dur]
   (midi-note sink note-num vel dur 0))
  ([sink note-num vel dur channel]
   (midi-note-on sink note-num vel channel)
   (at-at/after dur #(midi-note-off sink note-num channel) midi-player-pool)))

(defn midi-play
  "Play a seq of notes with the corresponding velocities and
  durations."
  ([out notes velocities durations]
   (midi-play out notes velocities durations 0))
  ([out notes velocities durations channel]
   (loop [notes notes
          velocities velocities
          durations durations
          cur-time  0]
     (when notes
       (let [n (first notes)
             v (first velocities)
             d (first durations)]
         (at-at/after cur-time #(midi-note out n v d channel) midi-player-pool)
         (recur (next notes) (next velocities) (next durations) (+ cur-time d)))))))



;; MTC Generation Functions

(def fps->bits
  {24 2r00
   25 2r01
   29.97 2r10
   30 2r11})

(defn send-mtc-full-frame
  "Sends a full frame MTC message to the given sink."
  [sink hours minutes seconds frames fps]
  (->> [0xF0 ; Sysex start
        0x7F ; Non-Realtime Sysex ID
        0x7F ; Manufacturer Specific (General)
        0x01 ; MTC Full Message
        0x01 ; Example data unchecked-byte
        (-> fps
            (fps->bits)
            (bit-shift-left 5)
            (bit-or  #_2r01100000 hours))  ; framerate 30 fps and Hours
        minutes   ; Minutes
        seconds   ; Seconds
        frames   ; Frames
        0xF7]
       (map unchecked-byte)
       (byte-array)
       (midi-sysex sink)))

(defn send-mtc-quarter-frame
  "Sends a single quarter frame MTC message to the given sink.
  NOTE, this code takes too long to execute, it should be optimized, but idk how"
  [sink piece-number half-byte]
  (let [mtc-byte (-> piece-number
                     (unchecked-byte)
                     (bit-shift-left 4)
                     (bit-or half-byte)
                     (unchecked-byte))
       
        short-msg (ShortMessage. ShortMessage/MIDI_TIME_CODE mtc-byte -1 ) ; the 2nd byte '0' is ignored
        ]

    (midi-send-msg (:receiver sink) short-msg -1)))

(defn send-mtc-quarter-frame-sequence
  "Sends a complete sequence of quarter frame MTC messages."
  [sink hours minutes seconds frames ]
  (send-mtc-quarter-frame sink 0 
                          (-> frames
                              unchecked-byte
                              (bit-and 2r1111 ) 
                              (unchecked-byte)) )
  (send-mtc-quarter-frame sink 1 
                          (-> frames
                              (unchecked-byte)
                              (bit-shift-right 4)
                              (bit-and 2r0001) 
                              (unchecked-byte)) 
                          )
  (send-mtc-quarter-frame sink 2 
                          (-> seconds
                              unchecked-byte
                              (bit-and 2r1111)
                              (unchecked-byte)))
  (send-mtc-quarter-frame sink 3 
                          (-> seconds
                              (unchecked-byte)
                              (bit-shift-right 4)
                              (bit-and 2r0011)
                              (unchecked-byte)))
  (send-mtc-quarter-frame sink 4 
                          (-> minutes
                              unchecked-byte
                              (bit-and 2r1111)
                              (unchecked-byte)))
  (send-mtc-quarter-frame sink 5 
                          (-> minutes
                              (unchecked-byte)
                              (bit-shift-right 4)
                              (bit-and 2r0011)
                              (unchecked-byte)))
  (send-mtc-quarter-frame sink 6 
                          (-> hours
                              unchecked-byte
                              (bit-and 2r1111)
                              (unchecked-byte)))
  (send-mtc-quarter-frame sink 7 
                          (-> hours
                              (unchecked-byte)
                              (bit-shift-right 4)
                              (bit-and 2r0001)
                              (bit-or  2r0110) ; 30 fps =  0110, 24 fps = 0000, 25 fps = 0010, 29.97 fps = 0100
                              (unchecked-byte))))


(defn send-part
  [sink hours minutes seconds frames part-number fps]
  (let [part-to-half-byte {0 (-> frames
                                 (unchecked-byte)
                                 (bit-and 2r1111))
                           1 (-> frames
                                 (unchecked-byte)
                                 (bit-shift-right 4)
                                 (bit-and 2r0001))
                           2 (-> seconds
                                 unchecked-byte
                                 (bit-and 2r1111))
                           3 (-> seconds
                                 (unchecked-byte)
                                 (bit-shift-right 4)
                                 (bit-and 2r0011))
                           4 (-> minutes
                                 (unchecked-byte)
                                 (bit-and 2r1111))
                           5 (-> minutes
                                 (unchecked-byte)
                                 (bit-shift-right 4)
                                 (bit-and 2r0011))
                           6 (-> hours
                                 unchecked-byte
                                 (bit-and 2r1111))
                           7 (-> hours
                                 (unchecked-byte)
                                 (bit-shift-right 4)
                                 (bit-and 2r0001)
                                 (bit-or  (bit-shift-left (fps->bits fps) 1) #_2r0110))}]
    (->> part-number
         (part-to-half-byte)
         (unchecked-byte)
         (send-mtc-quarter-frame sink part-number))))
    
(defn milliseconds-to-timecode
  [milliseconds fps]
  (let [ms-per-second 1000
        ms-per-minute (* 60 ms-per-second)
        ms-per-hour (* 60 ms-per-minute)
        ms-per-frame (/ ms-per-second fps)

        hours (quot milliseconds ms-per-hour)
        milliseconds (mod milliseconds ms-per-hour)

        minutes (quot milliseconds ms-per-minute)
        milliseconds (mod milliseconds ms-per-minute)

        seconds (quot milliseconds ms-per-second)
        milliseconds (mod milliseconds ms-per-second)

        frames (quot milliseconds ms-per-frame)]

   [hours minutes seconds frames]))

(def !vars
  "There is a single var for the timecode, this is bc generally there should only be 1 device reading the timecode
So we'll have this atom dynamically change. Instead of having a separate atom for each CDJ's timecode" 
  (atom {:speed-multiplier 1
         :base-millis 0 
         :steps 0 ; this should get reset when the base-millis is repositioned
         :recurring-job nil
         :fps 30
         :paused? false}))

(defn start-mtc
  "Starts sending the MTC full frame message repeatedly. 
   updates `RecurringJob` at `:recurring-job` key in `!vars` that can be killed with `at-at/kill`"
  [sink]
  (let [{:keys [recurring-job]} @!vars
        step-size-ms 20 ;vibes based 16ms is 60fps, so this prob works #_(int (/ 1000 fps 4)) ; 4 is due to quarter frame messages 
        _ (prn "vars" @!vars)
        update-timecode! (fn []
                           (let [{:keys [steps paused? fps speed-multiplier base-millis]} @!vars
                                 curr-millis (-> (* steps step-size-ms speed-multiplier)
                                                 (+ base-millis)
                                                 (int))
                                 [hours minutes seconds frames] (milliseconds-to-timecode curr-millis fps)]
                             (when-not paused?
                               (send-mtc-full-frame sink hours minutes seconds frames fps)
                               (swap! !vars update :steps inc))))]
    (when recurring-job
      (at-at/kill recurring-job))

    (swap! !vars assoc :recurring-job (at-at/every step-size-ms update-timecode! mtc-pool))))


(comment
  

  (time (-> (midi-out "mtc")
            (start-mtc)))

  (swap! !vars assoc
         :speed-multiplier 2
         :base-millis 10000
         :steps 0)

  (swap! !vars assoc
         :speed-multiplier 0.5)
 
  (at-at/stop-and-reset-pool! mtc-pool)

  (swap! !vars assoc :speed-multiplier 2)

  (swap! !vars assoc :curr-millis 0)

  )