# jug-muc-2026-performance
bartfastiel's presentation for Java User Group Munich about performance optimization

## task

Given an input file with personal data records, find the day of the year, where most people celebrate their birthday.

### input file format

Comma-separated text file with fixed width columns. Lines separated by `\n`. Columns separated by `;`.
No escaping nor quoting allowed. Dates are in 'YYYY-MM-DD' format. First line is a header.

The input textfile is zipped.

example:
```
First name;Last name       ;Birth date;
Hans      ;Fischer         ;1964-03-12;
Susi      ;Fischer         ;1959-08-06;
Hans      ;Schmidt         ;1985-09-13;
```

## Vortrag

### vorab

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

### live

[zwei ferngesteuerte Autos fahren im Raum zwischen den Stühlen herum. Eines langsam, das andere schnell]

* Welches ferngesteuerte Auto kaufst Du Dir?
* Welches Handy hast Du Dir ausgesucht?
* Mit welcher Grafikkarte gibst Du am Pausenhof an?

* Mit der schnellsten Grafikkarte
* Mit dem ruckelfreien Handy
* Das ferngesteuerte Auto, das 30 km/h fährt
  * und bei dem Du den Kindern sagen musst, dass sie sich nicht blitzen lassen dürfen.

* Die Geschwindigkeit ist Kaufkriterium

* In einem Artikel von 2017 schreibt Google, dass Du um 32% mehr Leute auf Deiner mobilen Seite während des Ladens verlierst, wenn sich die Ladezeit von 1s auf 3s erhöht [Quelle](https://www.thinkwithgoogle.com/intl/en-emea/marketing-strategies/app-and-mobile/find-out-how-you-stack-new-industry-benchmarks-mobile-page-speed/)
* Ein Report von Akamai behauptet, die Onlinestores mit 2.7s Ladezeit auf Mobilgeräten hätten die höchsten Konversionsraten, Seiten mit 2.8s Ladezeit hätten 1% weniger Konversionen,
  * Seiten mit 3.0s Ladezeit haben 2.4% weniger Konversionsrate [Quelle](https://www.igds.org/fileadmin/uploads/igds/Documents/Research_Reports/2017/akamai-state-of-online-retail-performance-spring-2017.pdf?utm_source=chatgpt.com)
 
* Und wenn Du zum Beispiel eure Inhouse-Anwendung zur Zeiterfassung umschreibst
  * weil der 4 Sekunden Splash-Screen alle Kollegen nervt
  * und Deine 100 Kollegen morgens, mittags zweimal und abends sich Zeit sparen
  * dann sparst du der Belegschaft in Summe eine halbe Stunde pro Tag
  * Oder 400€ im Monat [ca 43€/Stunde Quelle](https://www.destatis.de/DE/Themen/Arbeit/Arbeitskosten-Lohnnebenkosten/_inhalt.html?utm_source=chatgpt.com)
  * und bekommst das mit Deinem nächsten Gehalt zusätzlich ausgezahlt
  * Ähh, nein aber von den netten Kolleginnen und Kollegen möglicherweise eine Tüte Gummibärchen geschenkt

* Und darum soll es heute gehen
  * Warum Performance wichtig ist
  * Wie man Performance misst und bewertet
  * Vor allem aber auch, wie man sie ganz konkret verbessert
    * in beliebigen Programmiersprachen wie Python
    * in Visual Basic und Turbo Pascal
    * und wir natürlich in unserer aller Lieblingssprache Java
* wir nehmen uns heute ein bis zwei Stunden Zeit
  * mit einigen Folien
  * mit ganz viel Live-Coding
  * und Euren Fragen und Anregungen zwischendrin
    * meldet euch gerne jederzeit

* Es gibt so viele unterschiedliche Arbeitsfelder
* Java ist so vielseitig
> Umfrage "Wer von Euch coded im Bereich Online-Shop?"
> Umfrage "Wer von Euch coded im Bereich Embedded?" (Mehrfachnennungen möglich)
> Umfrage "Wer baut Software für Big-Data und KI?"
> Umfrage "Hat jemand von Euch beruflich mit Blockchain zu tun?"
> Umfrage "Oder schreibt Software im Bereich Grafik, CAD oder Spielentwicklung?"

* Es gibt Bereiche in der IT, in denen ist Performance das offensichtliche Kriterium
  * Beim Bit-Coin-Mining
  * Bei 3D-Grafik
  * Ich habe selbst viel mit Embedded Systemen und IoT zu tun
    * oft kleine Hardware, Stromverbrauch, Hitzeentwicklung wichtig
  * Spieleentwicklung
  * Hochfrequenzhandel
  * Big Data und KI
  * Online-Shops

* Und es gibt Bereiche da wird es als "nicht-funktionale Anforderung" abgetan
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

* Oder schauen wir uns die übliche API-Dokumentation an [Apidia opencsv](https://apidia.net/mvn/com.opencsv/opencsv/5.9/?pck=com.opencsv.bean&cls=.CsvToBeanBuilder#build-)
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

> Comic "Kind Zimmer aufräumen"

* Im konkreten Projekt jedoch, ist die Performance entscheidend
* Die Entwickler von opencsv wissen nicht, wie ihre Bibliothek eingesetzt wird
* Aber Du weißt, dass Dein Programm voraussichtlich mit X Anwendern, auf Hardware Y, Datenmenge Z verarbeiten soll
* Deshalb schreibe ich in Projekte heute Anforderungen an die Performance fest
* Bei jedem Auftrag nach außen
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

* Dazu noch eine Anekdote:
  * In einem Projekt für die öffentlichen Verkehrsmittel
    * war ich im Datenmanagement-Team (Telematik, IoT)
    * wir hatten eine riesige Datenbank, aber schnelle SQL-Statements und saubere Java-Architektur
    * dann haben wir einmal Daten aus dem SAP-System benötigt
    * 1000 Fahrzeugdaten, 2-3 MB groß, nichts ungewöhnliches
    * für unsere Berechnungen und für weitere Fremdsysteme
  * also: Schnittstelle vereinbart
    * War odata, weil unser SQL mit REST-API-Ansatz angeblich zu old-school war
    * alle funktionalen Anforderungen abgestimmt
    * als es fertig war, machen wir den Zugriff, aber der Server antwortet nicht
    * also haben wir den Timeout auf 5 Minuten gesetzt
    * wir schicken den Request ab
    * keine Antwort
    * also melden wir den Bug und bekommen als Antwort
      * es handelt sich nicht um einen Bug
      * sondern wir müssen den Timeout auf 60 Minuten setzen
    * 60 Minuten für 3 MB Daten
    * wir hatten vergessen eine Antwortzeit zu vereinbaren
    * Klar, wer einmal einen Report braucht, kann so eine Anfrage machen und sich derweilen einen Kaffee holen
    * aber wir haben die Infos gebraucht um unsere API-Responses zu generieren
    * also:
      * Hintergrundprozess fragt stündlich die Daten ab
      * Ergebnis cachen wir im Arbeitsspeicher (und zur Sicherheit für den Neustart in unserer Datenbank)
      * und liefern es selbst innerhalb von 50ms aus
    * Und als sich herumgesprochen hat, dass wir diese Daten so schnell liefern können
    * haben sich die anderen Abteilungen die Daten lieber von uns geholt als von der Quelle
    * :)

* Genug der Theorie für den Moment
* Wir schauen uns eine konkrete Aufgabenstellung für ein Java-Programm an
> Ausgehend von der Eingabedatei mit persönlichen Daten, finde den Tag im Jahr, an dem die meisten Menschen Geburtstag haben.
> Gib den Tag und die Anzahl der Personen aus, die an diesem Tag Geburtstag haben.
> Die Eingabedatei ist eine komma-separierte Textdatei mit Fix-Breite-Spalten. Zeilen sind mit \n getrennt. Spalten sind mit ; getrennt.
> Es gibt kein Escaping oder Quoting. Datumswerte sind im Format 'YYYY-MM-DD'.
> Die Eingabedatei ist gezippt.

