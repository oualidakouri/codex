# Validation Maison OS 1.2.0

Les journaux et détails sont dans [verification-1.2.0](../downloads/verification-1.2.0/README.md).

| Vérification | Résultat |
|---|---|
| Compilation APK, APK de tests et lint | Réussie ; 0 erreur lint, 26 avertissements |
| Tests JVM | 55 réussis, aucun ignoré |
| Android ciblé sur l’APK finale | 7 scénarios réussis |
| Lecture réseau du lecteur | Vrai HTTP local et décodage/lecture Media3 observés, arrêt par le bouton |
| Catalogue initial | 7 récitants, 114 noms, métadonnées versionnées et notices incluses |
| Mise à jour APK | Même certificat ; contrôle version/package/signature exécuté |
| API météo, MP3Quran et actualités en direct | Non vérifiées : proxy cloud HTTP 403 |
| Son audible et endurance Android 16 | À vérifier sur la tablette |
| Home Assistant et interrupteurs | Reportés, pas connectés |

APK finale : 18009121 octets ; SHA-256 `64d339288148715c00272290e22b169a72238c8cef9ccf313e7ad802ffd900a2`. Les sept scénarios ciblés ont été exécutés sur cette compilation ; les autres tests Android ne sont pas comptés comme exécutés dans cette campagne.

## Essais sur la tablette

1. Mettre à jour depuis Paramètres → Mises à jour sur la version 1.1, ou installer l’APK par-dessus sans désinstallation. Retrouver caméras, profils et médias, et constater le retrait d’Interrupteurs.
2. Dans Météo, vérifier aujourd’hui et les trois jours futurs pour votre ville : dates, mini/maxi, pluie et indication vestimentaire. Couper Internet puis vérifier que le cache reste daté, sans extrapolation.
3. Dans Coran, choisir un récitant puis une sourate, lancer depuis le dashboard, arrêter, reprendre, changer d’écran et mettre l’interface en veille. Vérifier le volume et l’arrêt depuis la notification.
4. Dans Paramètres, enregistrer une URL audio personnelle directe MP3/AAC ou HLS. Tester réseau disponible et coupé ; vérifier l’erreur sans fuite de l’URL dans le journal.
5. Tester un adhan pendant une récitation : celle-ci doit s’arrêter et ne pas reprendre seule.
6. Dans Actualités, vérifier France et votre ville, les sources/dates et les liens dans le navigateur. Changer la ville ou le RSS, puis tester le cache hors ligne.
7. Personnaliser les nouveaux widgets de la première page, exporter/restaurer une configuration et ressaisir les URL personnelles exclues des sauvegardes.
8. Refaire les contrôles des caméras et une session prolongée sur Android 16. Aucun résultat du cloud ne remplace cette validation matérielle.
