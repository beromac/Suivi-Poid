# Suivi Poids

Application Android (Kotlin + Jetpack Compose) pour suivre sa perte de poids :

- **Poids** : saisie à la main, dernière pesée, tendance lissée sur 7 jours, variation sur 30 jours, courbe
- **Composition corporelle** : masse grasse et masse maigre (saisies avec la pesée, ou envoyées par la balance)
- **Activités** : saisie à la main (marche, vélo, course...), minutes par semaine, calories, distance
- **Objectif** : poids cible, durée, progression et rythme nécessaire

## Deux façons d'alimenter l'appli

**1. Saisie manuelle (aucune connexion requise)**
Onglet Poids : « Ajouter une pesée ». Onglet Activités : « Ajouter une activité ».
Les saisies sont enregistrées uniquement sur le téléphone et peuvent être supprimées une par une.

**2. Garmin via Health Connect (facultatif)**
Garmin Connect envoie le poids, la composition corporelle et les activités à Health Connect,
que l'appli peut lire (Android 14 ou plus récent). Si Health Connect n'est pas disponible,
l'appli l'indique dans un petit bandeau masquable et continue de fonctionner avec les saisies manuelles.

```
Montre / balance Garmin -> Garmin Connect -> Health Connect -> Suivi Poids
```

Les deux sources se combinent : si les deux contiennent une pesée le même jour, la moyenne est utilisée.
L'appli ne fait que lire Health Connect : elle n'y écrit rien et n'envoie aucune donnée sur Internet.

## Obtenir l'APK avec GitHub (sans installer Android Studio)

1. Crée un dépôt vide sur GitHub, puis envoie ce dossier :
   ```
   git init
   git add .
   git commit -m "Première version"
   git branch -M main
   git remote add origin https://github.com/TON_COMPTE/TON_DEPOT.git
   git push -u origin main
   ```
2. Onglet **Actions** du dépôt : le workflow « Compiler l'APK » se lance tout seul (5 à 10 minutes).
3. Ouvre l'exécution terminée, télécharge l'artefact **SuiviPoids-apk** (un zip contenant `app-debug.apk`).
4. Copie l'APK sur le téléphone et installe-le (autoriser les sources inconnues si Android le demande).

Pour relancer la compilation à la main : Actions > Compiler l'APK > Run workflow.

## Développer avec Android Studio

Ouvre le dossier dans Android Studio (version récente), laisse Gradle se synchroniser, puis lance l'appli
sur un téléphone ou un émulateur.

## Limites connues

- Les saisies manuelles sont liées à l'installation : désinstaller l'appli les efface. Pas de sauvegarde automatique.
- Avec Health Connect, la masse grasse et la masse maigre peuvent être absentes selon le modèle de balance.
- L'appli est un outil de suivi, pas un avis médical. Un rythme de perte supérieur à environ 1 kg par semaine
  est signalé comme difficile à tenir.
