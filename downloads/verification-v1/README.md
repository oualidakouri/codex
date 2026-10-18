# Preuves de livraison V1 — 18 octobre 2026

- APK : `../maison-os-1.0.0.apk`, version 1.0.0 (code 10), package `fr.maisonos.diagnostic`.
- `gradle.txt` : compilation finale APK, APK de tests, tests JVM et lint réussis.
- `jvm-results.xml` : 37 tests exécutés, 0 échec, 0 erreur, 0 ignoré. `jvm-run.txt` conserve leur exécution ; le build final les réutilise sans changement du module core.
- `lint.txt` : 0 erreur, 18 avertissements.
- `signature.txt` : signature APK vérifiée, même certificat que la livraison de diagnostic.
- `checksums.sha256` : empreinte de cette APK.

## Tests Android — exécutions successives

Émulateur Android 11/API 30 sans KVM, rendu SwiftShader. AVD prévu en 1280 × 800 ; exécutions finales avec taille 640 × 400 et densité 80 pour réduire le coût du rendu tout en gardant une largeur logique de 1280 dp. Il ne s’agit pas d’un test matériel Android 16 ni d’une mesure de performance représentative.

`android-before-prayer-fix.txt` conserve la campagne intermédiaire : huit méthodes terminées avec succès (chiffrement/export, glisser-déposer, migration, douas, XML, vraie alarme audio, simulation/navigation et ZIP), puis abandon lors du test de configuration de ville. Un timeout Android de focus a été observé pendant les essais de cet écran. Cette campagne entière n’est donc pas marquée réussie.

`android-activity-recreation.txt` : régression du retour au premier plan après recréation, passée séparément sur le candidat précédent avec la correction de cycle de vie.

Après remplacement du dialogue de configuration par un écran dans la même fenêtre et déplacement du calcul hors du fil UI, `android-prayer-final.txt` valide le parcours réel Salat → Paris → enregistrement → affichage des prières sur l’APK finale : **OK (1 test)**.

Au total, les dix méthodes ont chacune un résultat réussi au cours des campagnes successives. La suite complète de dix méthodes n’a pas été relancée intégralement sur l’APK finale. Les fichiers distinguent les candidats et les exécutions ; aucun échec intermédiaire n’est transformé en succès.

Les caméras physiques, l’audio audible, la présence réelle, les restrictions Android 16 et la stabilité prolongée restent à vérifier sur la tablette. Voir `../../docs/VALIDATION-V1.md`.
