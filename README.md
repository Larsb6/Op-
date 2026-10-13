# DragonEgg

Paper-Plugin fuer **Minecraft 1.21.11** (Paper API `1.21.11-R0.1-SNAPSHOT`, Java 21).
Kein NMS, keine Reflection, keine deprecated APIs.

## Funktionen

1. **Extra-Leben** – Beim Spawn des Enderdrachen wird `MAX_HEALTH` auf den
   Standardwert (200 HP) mal `extra-health.multiplier` gesetzt und der Drache
   voll geheilt. Der Wert wird immer vom Vanilla-Standard abgeleitet, damit ein
   Respawn den Multiplikator nicht stapelt.
2. **Schadenszaehlung** – Pro Spieler wird der Schaden am Drachen gezaehlt.
   Pfeile, Tridents und andere Projektile zaehlen fuer den Schuetzen. Es wird
   nie mehr gezaehlt als das verbleibende Leben des Drachen.
3. **Eivergabe** – Beim Tod des Drachen erhaelt der Spieler mit dem meisten
   Schaden das Drachenei, sofern er online ist, sonst Platz 2, sonst der
   naechste Online-Spieler der Rangliste. Ist niemand davon online, faellt das
   Ei am Austrittsportal auf den Boden. Das Vanilla-Ei auf dem Portal wird
   entfernt. Bei vollem Inventar faellt das Ei vor die Fuesse des Gewinners.
   Bei Gleichstand gewinnt, wer den Wert zuerst erreicht hat.
4. **End-Sperre** – Nach dem Drachentod kann das End fuer die Timer-Dauer nicht
   freiwillig verlassen werden (Austrittsportal und Teleports in andere Welten
   werden abgebrochen, mit Hinweis im Chat). Der Respawn nach dem Tod ist
   davon nicht betroffen.
5. **Glowing** – Der aktuelle Besitzer des Eis leuchtet, aber nur fuer die
   Restzeit des Timers. Stirbt er, wird das Glowing entfernt und nach dem
   Respawn nicht erneut gesetzt. Nimmt ein anderer Spieler das Ei, bekommt er
   die Restzeit. Verlaesst das Ei das Inventar, endet der Effekt. Nach Relog
   oder Milch wird er fuer die Restzeit wieder gesetzt.
6. **Reset** – Alle Zaehler werden geleert, wenn ein neuer Drache spawnt.
7. **Chat-Nachricht** – Rote Nachricht an alle, sobald das Ei vergeben wurde
   (`%player%` als Platzhalter).
8. **ENDFIGHT-Bossbar** – Rote Bossbar (`BarColor.RED`, `BarStyle.SOLID`) mit
   Titel `ENDFIGHT - mm:ss`, nur fuer Spieler im End. Sie schrumpft mit dem
   Timer, kommt beim Betreten des Ends dazu und verschwindet beim Verlassen.

Punkt 4, 5 und 8 benutzen **einen einzigen Timer**, der beim Tod des Drachen
startet und nie zurueckgesetzt wird (`timer.duration-seconds`, Standard 600).

## Befehle

| Befehl | Beschreibung |
| --- | --- |
| `/dragonegg reload` | config.yml neu einlesen und sofort anwenden |
| `/dragonegg status` | Schalter und Restzeit anzeigen |
| `/dragonegg top` | Schadensrangliste des letzten Drachen |

Permission: `dragonegg.admin` (Standard: OP).

## Schalter

`enabled: false` schaltet das komplette Plugin ab. Zusaetzlich hat jede
Funktion ihren eigenen Schalter: `extra-health.enabled`, `egg-reward.enabled`,
`end-lock.enabled`, `glowing.enabled`, `bossbar.enabled`,
`chat-message.enabled`. Wird waehrend eines laufenden Timers eine Funktion
(oder das ganze Plugin) per `/dragonegg reload` abgeschaltet, endet sie sofort.

## Bauen

```bash
./gradlew build
```

Ergebnis: `build/libs/DragonEgg-1.0.0.jar`.
