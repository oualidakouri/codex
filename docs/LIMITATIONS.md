# Limites à connaître et à vérifier

## Android

- Les tests sur une tablette Android 16 réelle restent nécessaires : un numéro de version annoncé ne prouve ni l’API, ni la RAM physique, ni les codecs opérationnels.
- Pas de caméra en arrière-plan, de service caméra permanent, de contournement de Doze, de verrou administrateur ou de réveil forcé. L’analyse est arrêtée en quittant le premier plan ; elle doit être réactivée explicitement au retour.
- La veille proposée est noire **logiciellement**. Elle ne coupe pas physiquement l’écran ; la consommation de la dalle reste à mesurer. Le réveil ne fonctionne pas par caméra après extinction physique imposée par Android.
- La caméra détecte un changement de luminance, pas une personne. Ombres, éclairage, vibration et images vidéo peuvent créer des faux positifs. Le seuil est configurable.
- Le choix facultatif de Maison OS comme lanceur est le mécanisme prévu pour l’ouverture au démarrage. Il faut vérifier le redémarrage et le comportement du constructeur ; aucun démarrage d’activité depuis un broadcast BOOT_COMPLETED n’est forcé.
- La configuration est relue au lancement après fermeture ou arrêt du processus. Le système peut tuer le processus ; aucune promesse de relance automatique universelle après plantage. Le mode lanceur est à tester pour la récupération quotidienne.
- Le contrôle de l’état de charge relève du matériel/constructeur. L’application surveille la batterie et réduit sa charge de travail, sans prétendre imposer une limite matérielle de charge.
- `INTERNET` est nécessaire aux sockets LAN Android. `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` et `CHANGE_WIFI_MULTICAST_STATE` permettent l’observation réseau et la découverte ; aucune recherche SSID ou localisation n’est demandée. `CAMERA` est demandée au déclenchement du test frontal.
- Android 16/API 36 est ciblé sans activation expérimentale de la restriction LAN. Les mécanismes d’autorisation LAN des nouvelles versions, et tout appareil ayant activé la restriction expérimentale, doivent être validés sur ces versions avant d’être annoncés compatibles. Android 17 n’est pas certifié par ce build. Références officielles à revoir lors du changement de cible : [accès au réseau local](https://developer.android.com/privacy-and-security/local-network-permission), [restrictions de services au premier plan](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

## Caméras

- Un appareil iCSee n’est pas nécessairement compatible RTSP, ONVIF ou DVRIP. Chaque protocole est testé séparément. Aucun identifiant cloud/QR n’est traduit artificiellement en IP.
- IPv4 privée uniquement pour cette première version ; pas de DNS, IPv6, HTTPS ONVIF saisi manuellement ou réseaux routés multiples. Le service média/PTZ peut être en HTTPS s’il est annoncé par le service ONVIF HTTP, avec vérification TLS normale. Un XAddr annoncé avec une autre IP/nom est refusé, même si le fabricant le considère valide.
- La découverte peut être bloquée par un réseau invité, l’isolation Wi-Fi, le multicast, un VLAN ou un firmware qui ne répond pas. La fenêtre de cinq secondes n’est pas une recherche exhaustive ; ajout manuel disponible.
- DVRIP : login plaintext DVRIP-Web avec hash Sofia. Les firmwares imposant une autre négociation chiffrée sont indiqués en échec, sans désactivation de sécurité. Vidéo DVRIP, audio bidirectionnel, presets et SD ne sont pas implémentés.
- ONVIF : sous-ensemble Profile S, premier profil média, WS-Security UsernameToken Digest et HTTP Digest MD5/SHA-256 avec qop auth. Pas de synchronisation de l’horloge caméra, de Media2, de digest auth-int ni de profil choisi graphiquement. Le décalage de l’horloge peut faire échouer WSSE. L’URI RTSP obtenue doit être testée séparément.
- Vidéo : les codecs dépendent de Media3 et du matériel, pas seulement de leur déclaration dans Android. H.264 prioritaire ; H.265 et audio particuliers restent à valider. Le temps de première image n’est pas la latence caméra-écran. Commencer par un sous-flux basse définition fourni par la caméra.
- Le PTZ continu n’apparaît qu’après annonce ONVIF ou autorisation manuelle DVRIP. Pas de commandes zoom, presets ou diagonales tant que ces capacités ne sont pas validées.
- **STOP ne peut pas être garanti en cas de perte du réseau ou de mort du processus.** ONVIF demande une durée maximale de 0,5 seconde en plus du STOP ; il faut vérifier que le firmware la respecte. DVRIP utilise le STOP de compatibilité de la référence (`DirectionUp`, `Preset=-1`, `Step=5`), sans prétendre qu’il fonctionne partout. Les impulsions sont courtes, puis STOP est tenté, y compris après annulation. Une socket bloquante peut retarder l’envoi jusqu’au timeout. Ne pas utiliser la caméra sans surveillance tant que mouvement et arrêt ne sont pas validés physiquement.
- Aucun accès au réseau domestique depuis le cloud de développement n’est supposé. Aucune caméra réelle n’a été authentifiée dans le cloud.

## Périmètre logiciel

Pas encore de dashboard déplaçable, salat/adhan programmé, base de douas, import d’animation vidéo, moteurs de règles configurables, Tesla ou capteurs réels. Les boutons de diagnostic ne les font pas passer pour opérationnels. Le son test provient uniquement d’un fichier personnel choisi par l’utilisateur. Les fichiers exportés via le sélecteur système sont sous le contrôle de l’utilisateur ; un fournisseur de documents cloud éventuel appartient à Android, pas à un backend requis par Maison OS.
