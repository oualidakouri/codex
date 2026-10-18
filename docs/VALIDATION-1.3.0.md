# Validation Maison OS 1.3.0

Preuves et limites détaillées : [verification-1.3.0](../downloads/verification-1.3.0/README.md).

| Vérification | Résultat |
|---|---|
| APK et APK de tests compilées | Réussi |
| Tests JVM | 62 réussis, aucun ignoré |
| Lint | 0 erreur, 27 avertissements |
| Android après correction | 7 scénarios distincts réussis dans la campagne ; 3 réexécutés sur l’APK finale |
| Coran : commandes dashboard et persistance | Réussi sur émulateur |
| Lecture réseau, position et volume Media3 | WAV HTTP réel de test, déplacement observé |
| Citrouille : rendu 3D et transparence | Pixels OpenGL vérifiés, pupilles mobiles |
| Démonstration sans caméra | Explicitement simulée |
| Délai automatique et analyse caméra | Caméra virtuelle de l’émulateur, arrêt arrière-plan observé |
| Suivi d’un humain et performances Android 16 | Non vérifiés, essais tablette nécessaires |
| Signature et contrôles de mise à jour | Même certificat ; mauvais package/version/certificat refusés |

APK : 18085293 octets ; SHA-256 `55a924e61eb7478fabcefaf0dcd142acabf5bc29bd563ecf4d425cf2487d7562`. La campagne initiale compte six réussites et un échec caméra. La campagne finale de trois tests confirme la correction caméra et reprend la fermeture de scène et la sécurité de mise à jour ; les versions précédentes ne sont pas ajoutées aux résultats de cette version.

## Essais sur la tablette

1. Dans Paramètres → Mises à jour, télécharger la version 1.3.0 et confirmer l’installation Android sans désinstaller. Retrouver caméras, profils et médias.
2. Dans le dashboard, sélectionner un récitant et une sourate, puis essayer Son −/+, sourates précédente/suivante et récitants précédent/suivant. À l’arrêt, aucun bouton de sélection ne doit lancer le son ; pendant la lecture, la nouvelle sélection remplace la piste.
3. Sur une récitation chargée, déplacer la barre : vérifier temps écoulé et reprise à la position choisie. Une radio non déplaçable doit garder la barre désactivée. Vérifier le volume média Android séparément.
4. Dans Paramètres → Laboratoire · Citrouille 3D, lancer la démonstration sans caméra : le message doit indiquer SIMULATION. Vérifier le fond transparent, le relief et la fermeture au toucher/Retour.
5. Lancer le test avec caméra et autoriser celle-ci. Se placer de face, dans une pièce éclairée, bouger à gauche/droite et en hauteur ; vérifier le sens du regard. Sortir du champ : les pupilles doivent revenir au centre. Aucun enregistrement n’est produit.
6. Activer l’apparition automatique, choisir un délai et une durée, attendre sans toucher puis vérifier la disparition. Le mode automatique remplace la veille sombre ; le désactiver pour retrouver la veille habituelle.
7. Pendant l’overlay, changer d’application et vérifier l’arrêt caméra. Tester un véritable adhan, la protection thermique/batterie et le retour du dashboard. Observer température, mémoire et autonomie pendant une session prolongée sur Android 16.

Le suivi humain et le confort réel sur la tablette ne peuvent pas être validés par les tests de rendu ni par une caméra virtuelle.
