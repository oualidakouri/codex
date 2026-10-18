# Validation de Maison OS 1.1.0 — 18 octobre 2026

## Compilation finale

- APK : `../maison-os-1.1.0.apk`, package `fr.maisonos.diagnostic`, code 11.
- Taille : 17812504 octets ; SHA-256 : `29084dedd214ec2a3a25df5e61fffab90b0814d6b34917bd21ec52e401a02138`.
- `gradle-final.txt` : APK, APK de tests et analyse Android compilées avec succès.
- `jvm-results.xml` : 47 tests exécutés, zéro échec, erreur ou test ignoré. Les builds finaux ont réutilisé ces résultats sans changement du module core.
- `lint.txt` : 0 erreur et 23 avertissements (style KTX, versions de bibliothèques et HTTP local assumé pour ONVIF notamment).
- `github-download.json` : manifeste et APK relus depuis les URL publiques après publication ; HTTP 200, taille et SHA-256 conformes.
- `signature.txt` : certificat identique à l’APK de diagnostic et à la V1 ; publication refusée par le script si ce certificat change.

## Android

Émulateur Android 11/API 30, sans KVM, rendu SwiftShader ; taille 640 × 400 et densité 80 pour garder 1280 dp logiques. Ces tests ne constituent pas une mesure de performance ni un essai matériel Android 16.

`android-targeted.txt` : **OK (8 tests)** sur le candidat initial (SHA-256 `999c25e677fd7daec14b8ea1b363cf7c5cd8700ebc43e85eaee97eee6d661c04`) :

1. Doua sans traduction à l’écran et commandes d’interrupteur explicitement simulées.
2. Configuration chiffrée et exports sans secrets des caméras ou des interrupteurs.
3. Migration chiffrée du diagnostic conservant la caméra et son mot de passe.
4. Couleur réellement appliquée aux textes du titre et de la navigation, en thème sombre et clair.
5. Changement de largeur d’un widget par boutons, avec relecture de la disposition enregistrée.
6. Migration de la V1 conservant les deux secrets caméra et plaçant deux cartes sur la première page.
7. Configuration de Paris par l’interface et affichage des cinq prières.
8. Vérification de quatre vraies APK signées : accepter la version future avec la bonne signature ; refuser autre certificat, autre package et version non future.

Après ajustement de la fréquence de vérification pour contacter la source à chaque nouveau lancement (retours rapprochés espacés d’une minute), `android-final-updater.txt` rejoue le dernier scénario sur l’APK finale : **OK (1 test)**. Les huit scénarios n’ont pas été relancés intégralement sur cette dernière compilation ; le code des écrans et de la migration est identique.

## Fixtures et matériel

Les tests de protocole Tuya 3.3 utilisent un vecteur indépendant TinyTuya et une socket de fixture fragmentée. Un accusé de réception sans état ne confirme pas une commutation. Aucun interrupteur réel n’est marqué connecté.

Les tests météo portent sur des réponses de référence : pas de relevé inventé ni de température de batterie substituée. Le proxy du cloud refuse l’accès à l’API Open-Meteo (HTTP 403) ; l’accès réel météo/géolocalisation doit être vérifié sur la tablette et sa connexion. GitHub raw est accessible depuis le cloud.

Caméras physiques (notamment le second firmware), affichage simultané, codecs/PTZ, Smart Life réel, installateur Android 16, audio audible et endurance restent non vérifiés sur le matériel depuis le cloud. Voir `../../docs/LIMITATIONS-V1.md`.
