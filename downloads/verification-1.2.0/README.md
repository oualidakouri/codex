# Validation Maison OS 1.2.0 — 18 octobre 2026

## Artefact final

APK `../maison-os-1.2.0.apk`, package fr.maisonos.diagnostic, version 1.2.0 / code 12. Taille : 18009121 octets. SHA-256 : `64d339288148715c00272290e22b169a72238c8cef9ccf313e7ad802ffd900a2`.

- `gradle-final.txt` : compilation APK, APK de tests et lint réussie.
- `jvm-results.xml` : **55 tests JVM réussis**, zéro échec, erreur ou test ignoré. Les résultats incluent huit nouveaux scénarios : trois jours futurs au changement d’heure, données manquantes, RSS/Atom et refus XXE, titres/sources/liens, ville non devinée, catalogue/sourates disponibles, URL audio privée exclue des exports et migration des widgets.
- `lint.txt` : **0 erreur, 26 avertissements**, principalement style KTX, versions disponibles et HTTP local ONVIF conservé.
- `signature.txt` : certificat identique aux APK précédentes ; clé privée exclue de Git et des assets.
- `github-download.json` : manifeste et APK téléchargés depuis les URL publiques après publication ; HTTP 200, taille et SHA-256 conformes.
- `checksums.sha256` : empreinte de l’APK. Catalogue initial et notices comparés à ceux présents dans le ZIP APK.

## Android réellement exécuté

`android-targeted.txt` : **OK (7 tests)** sur cette même compilation finale :

1. Catalogue embarqué de sept récitations et 114 noms de sourates.
2. Migration chiffrée du schéma 3 : retrait des widgets Interrupteurs, conservation de la carte existante et ajout Coran/Actualités.
3. Vraie alarme AlarmManager → receiver → MediaPlayer avec son de test.
4. Couleurs réellement appliquées au titre et à la navigation dans les thèmes sombre et clair.
5. Absence de l’onglet Interrupteurs, douas sans français affiché, accès à l’écran Coran.
6. Acceptation d’une future APK avec le bon certificat ; refus d’un autre package, d’une version non future et d’un autre certificat.
7. Bouton Lecture → vraie connexion HTTP à un serveur de fixture → décodage/lecture Media3 observés par l’API, puis bouton Arrêter et état inactif du service.

Émulateur Android 11/API 30, SwiftShader et sans KVM, taille 640 × 400 à densité 80 (1280 dp logiques). Le flux audio de test est un WAV synthétique généré uniquement dans le test. Aucun enregistrement religieux n’est livré dans les APK. L’émulateur utilise `-no-audio` : lecture observée dans les API, pas de validation sonore humaine. Les autres tests instrumentés sont compilés mais n’ont pas tous été réexécutés dans cette campagne.

## Sources, réseau et limites

Les sept métadonnées de récitation et les 114 noms proviennent d’une source versionnée, avec fichiers/empreintes dans `../../third_party/QURAN-AUDIO-SOURCES.txt`. L’API MP3Quran permet l’actualisation ; aucun enregistrement n’est généré ou préchargé. Les 798 adresses dérivées du catalogue initial n’ont pas été vérifiées individuellement en direct.

Les requêtes MP3Quran, Open-Meteo, Franceinfo et Google Actualités sont refusées par le proxy du cloud (HTTP 403). Les tests de prévisions et titres utilisent des fixtures clairement identifiées. L’accès réel à ces fournisseurs, les radios MP3/AAC/HLS, la géolocalisation et les restrictions Android 16 restent à tester avec la connexion de la tablette. Aucun titre ou relevé fictif n’apparaît comme donnée réelle dans l’application.

Le fonctionnement prolongé, la qualité audible, l’interruption lors d’un véritable adhan, les caméras physiques et les politiques de batterie constructeur restent à vérifier sur le matériel. Home Assistant n’est ni déployé ni connecté ; les connectiques sont reportées à sa mise en place par l’utilisateur.
