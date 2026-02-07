# jug-muc-2026-performance Vortrag

## vorab

* Aufhängen 3x DIN A3 am Eingang:
> Ratespiel
> Wir werden heute Abend ein Programm zur Verarbeitung von vielen Personendaten bauen (Vorname, Nachname, Geburtstag).
> Wir suchen den Tag an dem die meisten Menschen Geburtstag haben und wie viele das sind.
> Rate: wie viele Sekunden brauchen wir um 100 Millionen Datensätze zu verarbeiten?
> Die Eingabedatei ist eine komma-separierte Textdatei mit Fix-Breite-Spalten. Zeilen sind mit \n getrennt. Spalten sind mit ; getrennt.
> Es gibt kein Escaping oder Quoting. Datumswerte sind im Format 'YYYY-MM-DD'.
> Die Eingabedatei ist gezippt.
> Hardware ist mein Arbeitsplatzrechner: AMD Ryzen 9 9950X, 64GB RAM, Windows 11, PCIe 5 SSD
> Keine Gewähr
* Darunter logarithmischer Zahlenstrahl mit Abriss-Zahlen darunter
* Beschriftung: "Es gibt einen kleinen süßen Preis zu gewinnen."
* Lösung sind 3,5 Sekunden
* Entsprechend Skala von 100 Minuten bis 1 Millisekunde


> zwei ferngesteuerte Autos fahren im Raum zwischen den Stühlen herum. Eines langsam, das andere schnell

## Vortrag

### Intro: Was würdest Du kaufen?

* Welches ferngesteuerte Auto kaufst Du Dir?
* Welches Handy hast Du Dir ausgesucht?
* Mit welcher Grafikkarte gibst Du am Pausenhof an?

* Mit der schnellsten Grafikkarte
* Du Kaufst das ruckelfreie Handy
* Das ferngesteuerte Auto, das 30 km/h fährt
  * und bei dem Du den Kindern sagen musst, dass sie sich nicht blitzen lassen dürfen.

### Performance ist Kaufkriterium

* In einem Artikel von 2017 schreibt Google, dass Du um 32% mehr Leute auf Deiner mobilen Seite während des Ladens verlierst, wenn sich die Ladezeit von 1s auf 3s erhöht [Quelle](https://www.thinkwithgoogle.com/intl/en-emea/marketing-strategies/app-and-mobile/find-out-how-you-stack-new-industry-benchmarks-mobile-page-speed/)
* Ein Report von Akamai behauptet, die Onlinestores mit 2.7s Ladezeit auf Mobilgeräten hätten die höchsten Konversionsraten, Seiten mit 2.8s Ladezeit hätten 1% weniger Konversionen,
  * Seiten mit 3.0s Ladezeit haben 2.4% weniger Konversionsrate [Quelle](https://www.igds.org/fileadmin/uploads/igds/Documents/Research_Reports/2017/akamai-state-of-online-retail-performance-spring-2017.pdf?utm_source=chatgpt.com)
* Mal ganz ehrlich: wieviel Erfolg ChatGPT, wenn 1m Ladezeit?

* Und wenn Du zum Beispiel eure Inhouse-Anwendung zur Zeiterfassung umschreibst
  * weil der 4 Sekunden Splash-Screen alle Kollegen nervt
  * und Deine 100 Kollegen morgens, mittags zweimal und abends sich Zeit sparen
  * dann sparst du der Belegschaft in Summe eine halbe Stunde pro Tag
  * Oder 400€ im Monat [ca 43€/Stunde Quelle](https://www.destatis.de/DE/Themen/Arbeit/Arbeitskosten-Lohnnebenkosten/_inhalt.html?utm_source=chatgpt.com)
  * und bekommst das mit Deinem nächsten Gehalt zusätzlich ausgezahlt
  * Ähh, nein aber von den netten Kolleginnen und Kollegen möglicherweise eine Tüte Gummibärchen geschenkt

* Und darum soll es heute gehen
  * Teil 1 
    * Angaben von Performance in der API-Dokumentation
    * Vereinbarung von Performance für konkrete Projekte und Produkte
    * Ein paar Ideen zur Performance
    * Sinn und Unsinn von Performance-Optimierung
  * Teil 2
    * Hands-On Optimierung in Java
    * Genauso übertragbar auf deine lieblings-Hobbysprache
      * in Visual Basic, PHP oder Python

* Viele kenne ich vom Sehen | vom nebeneinander Sitzen
* Um uns besser kennen zu lernen | um Bedeutung von Performance zu verstehen
* Kurze Umfrage: In welchem Bereich arbeitet Ihr?
> Umfrage "Wer von Euch coded im Bereich Webentwicklung?"
> Umfrage "Wer von Euch schreibt HTTP Backends?"
  * Mehrfachnennungen möglich
> Umfrage "Wer von Euch baut Desktop-Anwendungen?"
> Umfrage "Wer von Euch arbeitet mit Daten aus IoT, Telemetik?"
> Umfrage "Wer von Euch schreibt Software, die in der Cloud läuft - z.B. AWS, Azure, Google Compute Engine?"

* Es gibt Bereiche in der IT, in denen ist Performance das offensichtliche Kriterium
  * Beim Bit-Coin-Mining
  * Bei 3D-Grafik
  * Ich habe selbst viel mit Embedded Systemen und IoT zu tun
    * oft kleine Hardware, Stromverbrauch, Hitzeentwicklung wichtig
  * Spieleentwicklung
  * Hochfrequenzhandel

### Javadoc (dann Überleitung via "was bedeutet das eigentlich?")
> Würze: Tiersammelkarten

* Was bedeutet schnell?
* Wie können wir Geschwindigkeit angeben?

* schauen wir uns die übliche API-Dokumentation an [Apidia opencsv](https://apidia.net/mvn/com.opencsv/opencsv/5.9/?pck=com.opencsv.bean&cls=.CsvToBeanBuilder#build-)
  * Da wird die Funktion beschrieben
  * was kommt raus, was geht rein
  * mit welchen Fehlerzuständen muss ich rechnen
  * und wie sicher, wie schnell ist das?
* Es ist nicht angegeben, weil es
  * uns meist zum Glück nicht interessiert
  * weil wir schnelle Hardware haben
  * weil es auch einfach schwer anzugeben ist
    * je nach Computer
    * je nach Eingabedaten
    * je nach Betriebssystem
    * je nach JVM

> Würze: Übersicht JavaDoc Performanceangaben

* In der offiziellen JavaDoc
* Hauptsächlich bei Collections
* Angaben wie "constant time", "linear time", "logarithmic time"

### Big-O

* Deshalb in Informatik Konzept von "Big-O-Notation"
  * Wir sagen, inwiefern die Laufzeit von der Größe der Eingabedaten abhängt
  * Braucht ein Programm immer gleich viel Zeit?
    * O(1) - konstante Zeit
    * => vorzeigen mit graph-tool
  * oder braucht es bei doppelt so vielen Daten auch doppelt so viel Zeit?
    * O(n) - lineare Zeit
    * => vorzeigen mit graph-tool
  * Genauer gesagt:
    * Finden wir
      * eine Horizontale,
      * eine Gerade,
      * eine Parabel,
      * eine Exponentialfunktion,
    * Unter der die Laufzeit immer liegt, egal wie groß die Datenmenge auch sein mag?
      * => Komplexitat
  * Genauer gesagt: Zeitkomplexität (über Speicher-Komplexität heute nicht)

* Konstante Faktoren werden bei Big-O ignoriert
  * in linear: in schleife "do something" 3x drinnen
  * in linear: drei schleifen je mit einmal "do something"
  * und wenn wir innerhalb von "do something" in Zukunft drei Dinge tun?
  * Alles in der theoretischen Welt von Big-O: O(n)

### Problem mit Dependencies, Updates

* Hat Deine konkrete Methode O(n) Komplexität?
  * Wenn du das herausfinden willst
  * Musst Du auch jeden Code kennen, dem Du Deinen Input weitergibst

* Es ist wichtig Verlässlichkeit zu vereinbaren
* Aber in der Methoden-Signatur stehen nur "funktionale" Aspekte
  * "Korrektheit"
* Nicht aber "nicht-funktionale" Aspekte
  * "Sicherheit"
  * "Clean Code"
  * "Performance"
* Mit Dependencies kann Dein Code jederzeit langsam werden

### Unzulänglichkeit von Big-O für konkretes Projekt (Auto-Annonce: Je mehr Gas umso schneller, aber wie schnell ist das Auto wirklich? - Oder Arbeitszeugnis: "Er arbeitete" ohne Adjektiv)

> Comic "Kind Zimmer aufräumen"

### Performance vereinbaren als Zeit in Sekunden (nicht Komplexität) - für App, nicht für Library

* Als ich für eine große Versicherung während meiner Ausbildung vor über 20 Jahren Java-Programme geschrieben habe
  * da stand in den teils 100-Seitigen Lasten- und Pflichtenheften
  * oft nur von Features, Architektur, Tests
  * wenn ich auf diesen Button drücke, soll diese Seite ausgedruckt werden
  * der neue Knopf unten rechts soll die Einträge filtern
  * alles in Schriftgröße 12, Times New Roman
* das sind die funktionalen Anforderungen
* und über die nicht-funktionalen Anforderungen kein Wort
  * nichts über Sicherheit
  * nichts über Clean Code
  * nichts über Performance

> Würze = OData Verkehrsbetriebe

* Als ich für Verkehrsbetrieb gearbeitet habe
  * brauchten wir Schnittstelle zum SAP-System
  * alles wurde vereinbart:
    * Requests, Responses, Fehlercodes, usw
    * Kein REST (das fanden sie "überholt"), sondern OData
  * nach Wochen stellten sie uns die Schnittstelle zur Verfügung
  * und wir machen Abfrage
    * keine Antwort
    * also Timeout auf 5 Minuten gesetzt
    * keine Antwort
  * nachgefragt
    * doch sollte funktionieren
    * irgendwann herausgefunden: es gab keine Antwort, weil die Antwortzeit 60 Minuten betrug
  * wir hatten vergessen, eine Antwortzeit zu vereinbaren
  * SAP: Klar, wer einmal einen Report braucht, kann so eine Anfrage machen und sich derweilen einen Kaffee holen
  * aber wir haben die Infos gebraucht um unsere API-Responses zu generieren
  * also:
    * Hintergrundprozess fragt stündlich die Daten ab
    * Ergebnis cachen wir im Arbeitsspeicher (und zur Sicherheit für den Neustart in unserer Datenbank)
    * und liefern es selbst innerhalb von 50ms aus
  * Und als sich herumgesprochen hat, dass wir diese Daten so schnell liefern können
  * haben sich die anderen Abteilungen die Daten lieber von uns geholt als von der Quelle
  * also haben wir die Daten stündlich abgefragt, im Arbeitsspeicher und in der Datenbank gecacht und selbst innerhalb von 50ms ausgeliefert
  * und als sich herumgesprochen hat, dass wir die Daten so schnell liefern können, haben sich die anderen Abteilungen lieber von uns geholt als von der Quelle

* Wir sehen wie wichtig es ist, nicht nur die funktionalen Anforderungen zu vereinbaren, sondern auch die nicht-funktionalen Anforderungen
  * In manchen meiner Jobs: mehr Lastenhefte und Pflichtenhefte
  * 100 Seiten über die Features
  * Wenn Du in so einer Situation bist:
    * Definiere in einem kurzen Nebensatz die Performance-Anforderung
    * Ganz konkret: z.B. auf MacBook Air 2020, mit 1000 Datensätzen, soll die Seite innerhalb von 2 Sekunden laden
    * Ein Satz - und sofort Klarheit

> Folie Scrum Board mit Definition of Done

* Und in jedem Scrum-Team
  * Empfehlung: nicht pro User-Story, sonder übergreifend
  * als "Definition of Done"
  * und dort dann konkret: "Für den Kunden mit meinem Pixel 6 Handy, zeigen wir den Preis eines jeden Produkts innerhalb von 2 Sekunden an"
  * oder "Der Sachbearbeiter kann das PDF eines Vertrages innerhalb von 14 Sekunden finden und herunterladen"
    * und da rechnen wir dann den Splash-Screen und die Downloadzeit mit ein

* Also so wie wir die funktionalen Anforderungen mit unseren üblichen Unit- und Integrationstests absichern
* und in der CI-Pipeline jedesmal abweisen, was nicht standhält
* brauchen wir auch für die nicht-funktionalen Anforderungen automatisierte Prüfverfahren
  * Tests mit realistischen Daten- und Zugriffsmustern
  * und erwartetem Ergebnis, tatsächlichem Ergebnis
  * rot oder grün

* So wie ich genauso quantifizierbare Anforderungen an Clean Code mit Code-Analyse-Tools absichere
  * zum Beispiel kann ich SonarQube empfehlen um Clean Code sicherzustellen
  * das habe ich in 2017 mitentwickeln dürfen
* Und für die Security machen wir im aktuellen Projekt automatisierte Checks der Dependencies
  * Snyk war zum Beispiel einmal bei mir im Podcast zu Besuch
    * ein tolles Tool für diese nicht-funktionale Anforderung
* Und für Performance?
* Für Performance empfehle ich:
  * Tests auf einer definierten Hardware
    * pragmatisch ist eine definierte Cloud-Instanz
    * besser ist eine physische Maschine mit einem Linux mit wenig Hintergrundprozessen
  * Tests mit realistischen Daten
    * zum Beispiel eine anonymisierte Kopie der Produktionsdatenbank
    * zum Beispiel ein Backup (das sowieso regelmäßig gemacht wird) täglich wiederherstellen
  * Und als erwartetes Ergebnis, Assert:
    * entweder Fixwert
      * das ist aber manchmal unpassend, weil wir uns ja freuen, wenn es weniger als das Limit ist
      * und wenn 2 Sekunden Antwortzeit gefordert ist, dann wollen wir eine Verschlechterung von 0.5 auf 1.5 Sekunden trotzdem bemerken
    * also besser ein Wert, der plötzliche Verschlechterungen gegenüber dem Durchschnitt der letzten 30 Tage erkennt
      * auf das Messen von Zeiten gehen wir später noch ein

### Clean Code vs Performance - Dreamteam oder Widersacher? (Überleitung: deshalb nur dort optimieren, wo es wirklich nötig ist)

* Clean Code vs Performance
  * letztendlich kaufmännische Entscheidung
    * Geld ausgeben für Entwicklerzeit
    * Geld ausgeben für Hardware
    * Kommt darauf an: wie viel Last hast Du, wie oft wirst Du das Programm ändern wollen?
  * Logging kostet
    * Logge nicht mehr als Du lesen kannst!
  * Lasse alles "schön", was nicht Bottleneck ist!
* Klettergurt:
  * Komfortabel gibt es günstig
  * Nur wenn Du das Gewicht reduzieren musst, wird es teuer (und weniger komfortabel)

### Performance-Ideen (Caching, Dinge vorbereiten, return early, Datenformate am Beispiel Versicherungs-HTML, Parallelisieren)

> Folien mit Helden

* Caching
  * Zusatzaufwand
  * bringt oft Hauptgewinn

* Wähle das beste Datenformat
  * JSON/XML einfach zu lesen, gut mit Schemas
    * In der Regel durch die Größe eher langsam
  * CSV kleiner, aber nicht verschachtelt
  * Binärformate wie Protobuf aufwendiger, aber deutlich schneller

> Würze HTML-Spaces
* Als ich vor 20 Jahren am Anfang meiner Karriere Java Struts Webanwendung für Versicherung geschrieben habe
  * da wollte ich es 120% gut machen
  * habe den Code super elegant geschrieben
  * sauber eingerückt
  * das Laden der Webseite wurde langsam, ich suche nach dem Bottleneck
    * Das HTML-File war gigantisch!
    * 95% Whitespaces!
    * Ich habe dann die spitzen Klammern der Tags erst in der neuen Zeile gesetzt
    * Performance-Problem: gelöst (x-fach schneller)

* Mein Tipp:
  * Nutze etwas wie JSON
  * Eventuell Structure of Arrays statt Array of Structures
  * dann: Komprimieren
  * => klein, aber prinzipiell noch menschenlesbar
  * nicht Unmengen an Objekten und Garbage Collection

* Tipp: fange bereits an zu verarbeiten, wenn Dir die ersten Daten vorliegen (im Stream verarbeiten, nicht erst warten bis die ganze Datei da ist)

* Machs einmal von Hand (wie Karten-Sortieraufgabe für Gruppe von Azubis in Schweiz)

* Parallelisieren
  * Mehrere Threads, Prozesse, Server
  * Aber: Synchronisation

* Nutze eine gute Programmiersprache
  * z.B. Perl, Python oder Visual Basic - wenn Dir die Performance egal ist
  * oder aber Assembler, Rust, C oder C++ - wenn Du Performance um jeden Preis brauchst
  * oder Java, wenn Du Dich auf die Business-Logik konzentrieren willst, aber trotzdem die Möglichkeit für sehr gute Performance haben willst

> Würze: Klettergurt: Komfortabel gibt es günstig, nur wenn Du das Gewicht reduzieren musst, wird es teuer (und weniger komfortabel)

### Betriebswirtschaftliche Aspekte (Kundenzufriedenheit, Maintainability, Opportunity Cost, Horizontal Skalieren=Komplexität)
> Würze: Comic "Boss zuckt nur bei Cloud-Kosten"
> Würze: uCORE 4000€ Server mit DB und konstanten Abfragen => 40€ Raspberry PI

* Es geht darum die Reibung zu minimieren - also versuche nicht "Performance einzubauen", sondern "Bottlenecks zu entfernen"

* Computer werden schneller - Dein Programm wird automatisch günstiger zu betreiben

### Performance-Cycle: Messen, Analysieren, Optimieren, Messen

* Wir sollten nur dann von Clean, Lesbarem Code abweichen, wenn es ein Problem gibt
* Premature Optimization Is The Root Of All Evil
  * Also nicht optimieren, bevor es ein Problem gibt
  * Aber das Problem darfst Du gerne schon früh im Prozess erkennen
  * Wenn Du das Problem in der Analysephase erkennst
    * brauchst Du nicht erst eine fertige App beim Kunden
  * Trotzdem: verifiziere kurz (POC oder Berechnung), dass es wirklich ein Problem ist

* Deshalb:
  * Gibt es ein Problem? => Messen
    * z.B. mit Java Microbenchmark Harness (JMH) oder currentTimeMillis
    * Bei HTML-Ladezeiten: nicht "time to first byte", sondern "time to interactive" (Google SRE)
  * Wo ist das Problem? => Analysieren
    * z.B. mit IntelliJ Ultimate Profiler
  * Wie lösen wir das Problem? => Optimieren
    * am besten mit Unit Tests
  * Hat es geholfen? => Messen
    * Falls nein, Rollback, andere Optimierung versuchen
  * Und gibt es jetzt trotzdem immer noch ein Problem?

* Wir suchen das Bottleneck
* Die eine Stelle, die den schlimmsten Effekt hat
* Oder zumindest eine Stelle, wo wir mit wenig Aufwand viel gewinnen

### Sind die anderen Schuld? Beispiel: Spring Boot, Java Melody

* Versuche möglichst schnell zu wissen wo das Problem liegt
* Es muss nicht immer an Dir liegen!
* Deshalb:
  * Nutze Tools wie z.B. Java Melody
  * Beispielprogramm: JUG-Forum starten (langsam)
  * Bevor wir in den Code schauen: sind es überhaupt wir, die das Problem verursachen?
  * In pom.xml: javamelody
  * neu starten
  * neu seite laden
  * immer noch langsam :)
  * Aber: in Java Melody
    * Detailanalyse
    * SQL-Statements
    * Deshalb: Custom SQL-Query

* Mehr Last gefällig? JMeter

### Aufgabe

* Genug der Theorie für den Moment
* Wir schauen uns eine konkrete Aufgabenstellung für ein Java-Programm an
> Ausgehend von der Eingabedatei mit persönlichen Daten, finde den Tag im Jahr, an dem die meisten Menschen Geburtstag haben.
> Gib den Tag und die Anzahl der Personen aus, die an diesem Tag Geburtstag haben.
> Die Eingabedatei ist eine komma-separierte Textdatei mit Fix-Breite-Spalten. Zeilen sind mit \n getrennt. Spalten sind mit ; getrennt.
> Es gibt kein Escaping oder Quoting. Datumswerte sind im Format 'YYYY-MM-DD'.
> Die Eingabedatei ist gezippt.

> (Überleitung: wenn es an dir liegt: IntelliJ Ultimate Profiler)
> Würze: Welche IDE nutzt Du?

* Es gibt viele Tools: Java flight recorder, Spring Acutator, Grafana (letzte Woche)
* Wir nutzen:
  * Messen: JMH - Java Microbenchmark Harness
  * Analysieren: IntelliJ Ultimate Profiler

### Konzepte auch auf Python übertragbar

### Gewinner küren

### Schlussworte

* Keine Ode an Performance
* Sondern Toolset um nicht nur Featurereiche
* sondern auch nützliche Anwendungen
 
# Quellen, Nachweise, Ideen

* Zitat Linus Torvalds: "Some people say you should not micro optimize, but if what you love is micro optimization, that is what you should do." [Quelle](hhttps://youtube.com/shorts/Z65bQJKsnLk?si=b3IP8RDA7015u_zJ)
* "Measure, don’t guess" – McConnell / allgemeiner Performance-Grundsatz
* "Premature optimization is the root of all evil." – Donald Knuth
* "Make it work, make it right, make it fast." – Kent Beck bzw Kernighan/Pike

https://apidia.net/java/OpenJDK/25/?pck=java.base-all-classes&cls=java.util.LinkedHashSet (APIdia von Stefan)
```
Like HashSet, it provides constant-time performance for the basic operations (add, contains and remove), assuming the hash function disperses elements properly among the buckets. Performance is likely to be just slightly below that of HashSet, due to the added expense of maintaining the linked list, with one exception: Iteration over a LinkedHashSet requires time proportional to the size of the set, regardless of its capacity. Iteration over a HashSet is likely to be more expensive, requiring time proportional to its capacity.
```


todo
aus web app username raus

