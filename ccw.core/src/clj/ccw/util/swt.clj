(ns ccw.util.swt)

(ccw.util.bundle/set-bundle-classloader! "ccw.core")

(import 'org.eclipse.swt.SWT)
(import 'org.eclipse.swt.layout.FormAttachment)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Layout: FormLayout
;; doc.: http://www.eclipse.org/articles/article.php?file=Article-Understanding-Layouts/index.html
(def alignments {:top SWT/TOP, :bottom SWT/BOTTOM, :left SWT/LEFT, :right SWT/RIGHT, :default SWT/DEFAULT})
(defn as-alignment [a] (or (alignments a) a))
(def form-attachment-setter 
  {:numerator    #(set! (.numerator %1) %2)
   :denominator  #(set! (.denominator %1) %2)
   :alignment    #(set! (.alignment %1) (as-alignment %2))
   :control      #(set! (.control %1) %2)
   :offset       #(set! (.offset %1) %2)
   :pct          #(do (set! (.numerator %1) %2)
                      (set! (.denominator %1) 100))})
(defn form-attachment 
  [spec] 
  (let [spec (merge {:offset 0 :alignment :default} spec)
        fa (FormAttachment.)]
    (doseq [[attr val] spec]
      ((form-attachment-setter attr) fa val))
    fa))

(import 'org.eclipse.swt.layout.FormData)
(def form-data-setters 
  {:width   #(set! (.width %1) %2) 
   :height  #(set! (.height %1) %2)
   :bottom  #(set! (.bottom %1) (form-attachment %2))
   :top     #(set! (.top %1) (form-attachment %2))
   :left    #(set! (.left %1) (form-attachment %2))
   :right   #(set! (.right %1) (form-attachment %2))})
(defn form-data [& {:as spec}]
  (let [fd (FormData.)]
    (doseq [[attr val] spec]
      ((form-data-setters attr) fd val))
    fd))

(import 'org.eclipse.swt.layout.FormLayout)
(def form-layout-setters 
  {:margin-bottom #(set! (.marginBottom %1) %2)
   :margin-height #(set! (.marginHeight %1) %2)
   :margin-left   #(set! (.marginLeft   %1) %2)
   :margin-right  #(set! (.marginRight  %1) %2)
   :margin-top    #(set! (.marginTop    %1) %2)
   :spacing       #(set! (.spacing      %1) %2)})

(defn form-layout [& {:as spec}]
  (let  [fl (FormLayout.)]
    (doseq [[attr val] spec]
      ((form-layout-setters attr) fl val))
    fl))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Layout: FlowLayout
(import '[org.eclipse.swt.layout FillLayout])
(defn fill-layout 
  ([] (FillLayout.))
  ([{:keys [margin]}]
    (if margin
      (let [f (fill-layout)]
        (set! (.marginWidth f) margin)
        (set! (.marginHeight f) margin)
        f)
      (fill-layout))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Event Mgt
(import '[org.eclipse.swt.widgets Listener])
(import '[org.eclipse.swt.events KeyListener])

(defn listener* [f] 
  (reify Listener
    (handleEvent [this event] (f event))))

(defmacro listener [event-name & body]
  `(listener* (fn [~event-name] ~@body)))

(defn key-listener* 
  [f]
  (reify KeyListener 
    (keyPressed [this event])
    (keyReleased [this event] (f event))))

(defmacro key-listener [event-name & body]
  `(key-listener* (fn [~event-name] ~@body)))

;;;;;;
;;;;;;
(import 'org.eclipse.swt.widgets.Display)
(import 'org.eclipse.swt.widgets.Shell)
(defmacro ui [display-name & body]
  `(if-let [~display-name (Display/getCurrent)]
     ~@body
     (.asyncExec (Display/getDefault)
       (fn []
         (let [~display-name (Display/getCurrent)]
           ~@body)))))

(defn display [] (Display/getCurrent))

;;; RECOPIE DE ccw.util.eclipse TODO fusionner
(defn active-shell []
  (-> (org.eclipse.swt.widgets.Display/getDefault)
    .getActiveShell))

;; Shell SWT.<> Styles:
; BORDER, CLOSE, MIN, MAX, NO_TRIM, RESIZE, TITLE, ON_TOP, TOOL, SHEET
; APPLICATION_MODAL, MODELESS, PRIMARY_MODAL, SYSTEM_MODAL
(defn new-shell 
  ([] (new-shell (display)))
  ([display & options] ; Subject to change 
    (Shell. display (apply bit-or options))))


;;; OBJECTIF : montrer comment on peut faire du dynamique en clojure pour les IHMs

(require '[ccw.leiningen.launch :as launch])
(defn do-stuff [text] (launch/lein "foobar" text))
;;; TEST
(defn ts []
  (ui display
    (let [dialog	 (doto (new-shell display
                                   SWT/ON_TOP
                                   SWT/TITLE
                                   SWT/APPLICATION_MODAL)
                    (.setText "Leiningen command line")
                    (.setLayout (form-layout :spacing 0
                                             :margin-left 5
                                             :margin-right 5
                                             :margin-bottom 5
                                             :margin-top 5))
              (.addListener org.eclipse.swt.SWT/Traverse
                (listener e
                  (when (= org.eclipse.swt.SWT/TRAVERSE_ESCAPE (.detail e))
                    (.close (.widget e))
                    (set! (.detail e) org.eclipse.swt.SWT/TRAVERSE_NONE)
                    (set! (.doit e) false)))))
          command-input (doto (org.eclipse.swt.widgets.Text. dialog 0)
                          (.setText "<command line, e.g. run or repl :server, etc.>")
                          (.setToolTipText "Click Enter to execute, Click Esc to cancel")
                          (.setLayoutData (form-data :width 400)))

          _ (doto command-input
              (.setSelection 0 (count (.getText command-input)))
              (.addKeyListener (key-listener e
                                             (when (= \return (.character e))
                                               (do-stuff (.getText command-input))
                                               (.close dialog)))))
          cursor (.getCursorLocation display)]
      (doto dialog
        .pack
        (.setLocation (.x cursor) (.y cursor))
        .open)
      (.setFocus command-input))))

