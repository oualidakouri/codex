# Validation Maison OS 1.3.0 — 18/10/2026 22:43 CEST

APK `../maison-os-1.3.0.apk`, package `fr.maisonos.diagnostic`, code 13. Taille : 18085293 octets. SHA-256 : `55a924e61eb7478fabcefaf0dcd142acabf5bc29bd563ecf4d425cf2487d7562`. Même certificat que les versions précédentes ; aucune clé privée publiée.

- `gradle-final.txt` : compilation APK, APK de tests et lint réussies. Les tâches JVM inchangées réutilisent leurs résultats Gradle de cette campagne.
- `jvm-results.xml` : **62 tests JVM réussis**, zéro échec, erreur ou test ignoré. Navigation avec catalogues incomplets, bouclage, autre personne plutôt qu’autre lecture du même récitant, miroir du regard, délai et blocages du déclenchement, migration sans perte de widget.
- `lint.txt` : **0 erreur, 27 avertissements** ; aucun échec ignoré.
- `android-targeted.txt` : **3 tests Android réussis sur l’APK finale** : caméra automatique, fermeture de la démonstration et sécurité de mise à jour. `android-before-camera-fix.txt` conserve la campagne précédente : six réussis, un échec caméra corrigé ensuite. Au total, sept scénarios distincts passent ; les autres résultats ne sont pas présentés comme une exécution sur l’APK finale.
- `pumpkin-render.png` : pixels RGBA lus depuis un vrai framebuffer OpenGL ES de l’émulateur. Fond transparent et changement des pupilles vérifiés ; aucune caméra ni présence humaine simulée dans cette image de rendu.
- `signature.txt` et `checksums.sha256` : signature historique et empreinte finale.

## Scénarios Android de cette campagne

1. Avant la dernière correction CameraX (rendu inchangé) : compilation réelle des shaders et rendu des maillages, alpha nul en dehors du modèle, géométrie visible et pixels des pupilles modifiés par la direction du regard.
2. Démonstration citrouille explicitement simulée, fermeture par le bouton, suppression à la perte du premier plan et refus de démarrer pendant un état adhan injecté par le test. Ce dernier n’est pas un essai sonore réel de l’adhan.
3. Avant la dernière correction CameraX (migration inchangée) : migration chiffrée d’une ancienne configuration, conservation des widgets et retrait des anciens interrupteurs.
4. Avant la dernière correction CameraX (commandes inchangées) : commandes du widget Coran : volume −/+, sourate suivante/précédente, récitant suivant/précédent, écriture persistante et timeline désactivée à l’arrêt.
5. Avant la dernière correction CameraX (service audio inchangé) : véritable lecture HTTP d’un WAV synthétique de test, durée/déplaçabilité Media3, action sur la timeline, position réellement déplacée, réglage du volume du lecteur et arrêt du service. Aucun audio religieux n’est inclus dans les fixtures.
6. Délai automatique de la citrouille, permission caméra, ouverture CameraX, analyse de vraies images de la **caméra virtuelle**, puis arrêt au passage en arrière-plan. Ce test ne prouve pas la détection d’un humain ni un suivi correct sur le matériel domestique.
7. Acceptation d’une future APK signée avec le même certificat, refus d’un autre certificat, d’un autre package et d’une version non future.

La première campagne a exposé une attente d’images après ouverture de la caméra : l’aperçu caché a été retiré du binding CameraX et le test de capture dispose d’une durée de scène de 60 s pour ne pas expirer pendant l’initialisation. Les assertions sur l’arrivée effective d’images et l’arrêt restent obligatoires. La campagne finale vérifie le pipeline corrigé.

Émulateur Android 11/API 30 sans KVM, SwiftShader, 640 × 400 à densité 80 (1280 dp logiques). Le rendu de preuve est un framebuffer de 320 × 200. Émulateur sans audio audible : position/lecture/volume observés via les API. Les autres scénarios instrumentés sont compilés mais non comptés comme exécutés dans cette campagne.

## Non vérifié sur le matériel

Suivi d’un vrai visage, sens du regard devant la tablette, faux positifs, fluidité et autonomie Android 16, arrêt thermique/batterie réel, comportement constructeur, stabilité prolongée, interruption sonore par un vrai adhan et caméras domestiques. Le détecteur frontal Android ne reconnaît ni le corps ni l’identité et peut échouer selon lumière/pose/taille. Les fichiers audio fournisseurs et l’API MP3Quran ne sont pas validés par le WAV local.

Le mode automatique est désactivé par défaut, remplace volontairement la veille sombre et exige l’application au premier plan. Aucune permission de superposition système ni caméra au boot/arrière-plan. Les accès GitHub depuis le cloud utilisent son réseau/proxy et ne constituent pas une preuve d’accès anonyme ; l’utilisateur a confirmé séparément le fonctionnement de la mise à jour après passage du dépôt en public.
