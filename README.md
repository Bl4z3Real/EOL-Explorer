# EOL Explorer

Browser con l'aspetto degli anni '90 e il motore di oggi.
Uno stesso stile, due app: **PC** (Electron, motore Chromium) e **Android** (WebView nativa).

Funzioni comuni: schede, preferiti, cronologia, pagina iniziale "portale", ricerca a categorie,
**Macchina del tempo** (apre i siti come nel 1999 tramite l'Internet Archive), download.
Solo PC: temi (EOL, Classic 98, Aqua 2000, Notte neon), zoom, trova nella pagina, strumenti sviluppatore,
scorciatoie da tastiera (Ctrl+T, Ctrl+W, Ctrl+L, Ctrl+D, Ctrl+F, F5, Alt+←/→).
Solo Android: blocco pop-up, versione desktop dei siti, condivisione, si può impostare come browser predefinito.

Attenzione: questo codice non è stato compilato né provato prima della consegna.
Se un comando dà errore, incolla il messaggio in chat e lo correggiamo.

---

## PC (Windows)

Serve Node.js 20 o più recente (https://nodejs.org).

    cd desktop
    npm install
    npm start          # prova il browser
    npm run dist       # crea installer e versione portatile in desktop/dist

## Android

**Con Android Studio** (il più semplice)
1. Apri la cartella `android` con Android Studio e attendi la sincronizzazione.
2. Menu *Build › Build Bundle(s) / APK(s) › Build APK(s)*.
3. L'APK è in `android/app/build/outputs/apk/debug/app-debug.apk`: copialo sul telefono e installalo
   (consenti "installa app sconosciute" per il file manager che usi).

Richiede Android 10 o superiore.

## Senza installare nulla: GitHub

1. Crea un repository su GitHub e carica tutto il contenuto di questa cartella
   (compresa `.github`).
2. Scheda *Actions › Build EOL Explorer › Run workflow*.
3. A fine lavoro scarichi da *Artifacts* l'installer Windows (`.exe`) e l'`app-debug.apk`.

## Personalizzare

- Icona: sostituisci `desktop/build/icon.ico`, `desktop/build/icon.png`, `desktop/renderer/icon.png`,
  `android/app/src/main/assets/icon.png` e le `ic_launcher*.png` in `android/app/src/main/res/mipmap-*`.
- Siti e categorie della home: `TILES` in `desktop/renderer/app.js` e in `android/app/src/main/assets/home.html`.
- Motori di ricerca: `ENGINES` in `app.js`, `engines` in `MainActivity.kt`.
