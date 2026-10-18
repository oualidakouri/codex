# Étude et provenance iCSee

Source : https://github.com/voidnullvalue/Icsee-android

Révision étudiée : `74a2ed41923f1c2b3e880222aa3d76af10389c82` (consultée pour cette preuve).

Documents examinés : README.md, PROTOCOL_NOTES.md, PROTOCOL_STATUS.md, SECURITY.md et LICENSE. Code examiné : découverte UDP/parsing, session/login/keepalive, construction PTZ et lecture RTSP Media3.

## Réutilisation MIT

Fichiers adaptés sous `core/src/main/kotlin/fr/maisonos/core/vendor/` :

- `dvrip/DvripHeader.kt`
- `dvrip/DvripFrame.kt`
- `dvrip/DvripPayloads.kt`
- `crypto/SofiaHash.kt`
- `ptz/PtzCommand.kt`
- `ptz/PtzRequestBuilder.kt`

Changements : préfixe de package et commentaire d’attribution. Les mentions MIT © 2026 |VOID| sont conservées dans `third_party/ICSEE-LICENSE.txt` et `app/src/main/assets/ICSEE-LICENSE.txt`. Le hash Sofia sert uniquement à l’interopérabilité de ce protocole ; ce n’est pas un algorithme de stockage de mots de passe.

Les clients JVM DVRIP/ONVIF et le petit découvreur de Maison OS adaptent les contraintes de la preuve, avec tests locaux, tailles bornées et binding Wi-Fi. Le probe UDP et le STOP DVRIP reprennent les formes documentées dans la référence. Aucun SDK constructeur ni code de récupération de mots de passe, compte de secours, changement de mot de passe ou accès cloud n’est intégré.

## Conclusions pour Maison OS

La référence confirme certains comportements **sur le matériel de ses auteurs** ; cela ne certifie pas les caméras de l’utilisateur. Son état de protocole distingue des chemins réellement testés, des inférences et des limites. Notamment, un canal média DVRIP peut accepter une demande sans envoyer de vidéo ; la preuve Maison OS utilise donc RTSP pour l’image et teste l’authentification DVRIP séparément.

Les notes de sécurité décrivent des défauts de certains firmwares Xiongmai, dont des comptes faibles et des possibilités de récupération de secrets. La preuve n’utilise pas ces mécanismes. Recommandation : réseau IoT isolé, comptes personnels, aucun port exposé à Internet. Un client ne peut pas corriger les faiblesses du firmware caméra.
