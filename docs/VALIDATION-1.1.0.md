# Validation Maison OS 1.1.0

Le code et les résultats complets sont dans [verification-1.1.0](../downloads/verification-1.1.0/README.md).

| Vérification | Résultat |
|---|---|
| Compilation APK, APK de tests, lint | Réussie ; lint 0 erreur, 23 avertissements |
| Tests JVM | 47 réussis, aucun ignoré |
| Android ciblé | 8 scénarios réussis sur le premier candidat |
| Contrôle APK sur la compilation finale | 1 scénario rejoué et réussi : version, package et certificat |
| Signature de mise à jour | Identique au diagnostic et à la V1 |
| Téléchargement GitHub public | Manifeste et APK HTTP 200 ; taille et SHA-256 conformes |
| Caméras et Smart Life physiques | Non vérifiés depuis le cloud |
| Accès météo réel et position | Non vérifiés ; Open-Meteo bloqué par le proxy cloud |
| Endurance et restrictions Android 16 | À tester sur la tablette |

APK finale : 17812504 octets, SHA-256 `29084dedd214ec2a3a25df5e61fffab90b0814d6b34917bd21ec52e401a02138`. Les campagnes et leurs candidats sont distingués dans le rapport ; la suite Android complète n’a pas été rejouée sur la dernière compilation.

## Essais recommandés sur la tablette

1. Installer par-dessus la version existante sans la désinstaller : retrouver les caméras, identifiants, médias et dispositions.
2. Choisir un des six thèmes et contrôler la navigation ; modifier la taille d’une carte par les boutons, fermer et rouvrir l’application.
3. Pour la seconde caméra, enregistrer IP/identifiants, tester les ports, DVRIP puis RTSP. Si nécessaire, choisir le format iCSee/Xiongmai ; l’absence d’ONVIF dans le scan /24 n’en prouve pas l’absence réelle.
4. Vérifier les deux miniatures sur la première page, l’agrandissement, le retour et l’arrêt des lecteurs masqués. Utiliser les sous-flux.
5. Choisir la ville météo, demander la position approximative ou reprendre la salat ; vérifier la date des données, puis le cache après coupure d’Internet.
6. Dans Interrupteurs, tester une simulation. Connecter uniquement un appareil Tuya 3.3 dont la clé locale et le point de données sont connus, puis vérifier physiquement son état. Les autres firmwares restent indiqués non pris en charge.
7. Dans Paramètres, vérifier que Maison OS 1.1.0 est à jour. À la prochaine publication, tester téléchargement, confirmation Android et conservation des données.
8. Refaire les essais de salat, adhan, veille et stabilité du [rapport V1](VALIDATION-V1.md) sur une session prolongée.
