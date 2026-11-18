# 🎵 SongSnap – Errate deine Spotify-Songs

Android-Ratespiel: Wähle eine deiner Spotify-Playlists, hör einen winzigen Ausschnitt und errate den Song.
Jeder Fehlversuch oder „Skip“ verlängert den Ausschnitt:

**0,1 s → 0,5 s → 1 s → 2 s → 4 s → 8 s → 16 s**

| Modus | Startpunkt |
|---|---|
| ⏮ **Beginning** | ganz am Anfang des Songs |
| 🎲 **Random** | zufällig zwischen 0 und (Songlänge − 16 s) |

Punkte: 100 / 80 / 60 / 45 / 30 / 20 / 10 je nach Stufe. Highscores werden pro Playlist, Modus und Rundenzahl gespeichert.

---

## Voraussetzungen

- Android 8.0 oder neuer
- **Spotify-App** auf dem Handy installiert und eingeloggt
- **Spotify Premium** (Spotify verlangt Premium für Entwickler-Apps und für das gezielte Abspielen von Songs)

## 1. APK bekommen

### Variante A – automatisch über GitHub (ohne Android Studio)
1. Auf github.com ein neues (gern privates) Repository anlegen.
2. Den kompletten Inhalt dieses Ordners hochladen (inkl. des versteckten Ordners `.github`), z. B. per
   `git init && git add . && git commit -m init && git remote add origin <URL> && git push -u origin main`.
3. Im Tab **Actions** läuft „APK bauen“ (~5 Minuten).
4. Fertig: Unter **Releases → latest** liegt `SongSnap.apk` – direkt auf dem Handy herunterladen und installieren
   („Installation aus unbekannten Quellen“ erlauben).

### Variante B – Android Studio
Ordner öffnen → warten bis Gradle synchronisiert → **Build › Build APK(s)**.
Die APK liegt danach unter `app/build/outputs/apk/`.

> Beim ersten Build wird das Spotify App Remote SDK (`spotify-app-remote-release-0.8.0.aar`) automatisch von
> Spotifys GitHub nach `app/libs/` geladen.

## 2. Spotify einrichten (einmalig, ~3 Minuten)

Die App führt dich beim ersten Start durch diese Schritte – alle Werte lassen sich dort antippen und kopieren:

1. <https://developer.spotify.com/dashboard> → **Create app**
2. **Redirect URIs** eintragen:
   - `songsnap://callback`
   - `http://127.0.0.1:43821/callback` (für den alternativen Login)
3. Bei „Which API/SDKs are you planning to use?“: **Web API** und **Android** anhaken.
4. **Android package:** `com.songsnap.game`
   **SHA1 fingerprint:** `FA:81:20:77:A1:5A:E2:07:1E:8E:BD:39:D3:09:44:E6:6E:4F:BF:2D`
5. Speichern, **Client ID** kopieren und in der App einfügen.
6. Wenn andere mitspielen sollen: Unter **User Management** deren Spotify-E-Mail eintragen (max. 5 Personen im Entwicklungsmodus).

Der SHA1-Wert passt zum mitgelieferten Signaturschlüssel `app/songsnap.jks` – egal ob du über GitHub oder Android Studio baust.

## Gut zu wissen

- **Welche Playlists?** Seit Spotifys API-Änderungen von Februar 2026 gibt die API Songs nur für Playlists heraus,
  die dir gehören oder bei denen du mitarbeitest. Fremde Playlists erscheinen mit 🔒 – kopiere sie in Spotify
  einfach in eine eigene Playlist. „Lieblingssongs“ funktionieren immer.
- **Wie werden 0,1 s möglich?** Die Spotify-App startet die Wiedergabe kurz vorher stumm, SongSnap gibt den Ton
  für genau die Ausschnittlänge frei und schaltet dann wieder stumm. Deshalb bewegt sich während eines Ausschnitts
  kurz die Medien-Lautstärke-Stummschaltung – das ist Absicht.
- Musik muss **auf dem Handy selbst** laufen (nicht per Spotify Connect auf einem Lautsprecher), sonst greift die
  Stummschaltung nicht.
- Klappt „Mit Spotify verbinden“ nicht, nimm den **alternativen Login**.

## Technik

Kotlin · Jetpack Compose · Spotify App Remote SDK 0.8.0 · Spotify Web API (Authorization Code + PKCE) · Coil

```
app/src/main/java/com/songsnap/game/
├── MainActivity.kt          Einstieg, Login-Rückleitung
├── GameViewModel.kt         Spiellogik, Stufen, Punkte, Highscores
├── data/                    Spotify-Login (PKCE), Web-API, Einstellungen
├── player/SnippetPlayer.kt  präzise Ausschnitte über die Spotify-App
└── ui/                      Screens & Animationen
```
