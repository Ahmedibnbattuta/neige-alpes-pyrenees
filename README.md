# Neige Alpes & Pyrénées — application Android

Liste de 65 stations de ski des Alpes (France, Suisse, Autriche, Italie, Allemagne, Slovénie)
et des Pyrénées (France, Espagne, Andorre), avec pour chacune la **hauteur de neige en cm**
à trois altitudes : bas du domaine, milieu et sommet. Également : chutes de neige prévues
sur 3 jours et température au sommet.

## Fonctions
- Recherche par nom de station, massif ou pays (accents ignorés)
- Filtres : Toutes / Alpes / Pyrénées / Favoris
- Tri : neige au sommet, neige en bas, altitude, nom
- Favoris mémorisés sur le téléphone
- Thème clair / sombre automatique

## Source des données
[Open-Meteo](https://open-meteo.com) — API gratuite, sans clé, licence CC BY 4.0
(usage non commercial gratuit). L'application interroge le modèle météo à l'altitude exacte
de chaque niveau (`elevation=`). Ce sont des **estimations de modèle**, pas les relevés
officiels publiés par les stations.

## Obtenir l'APK sans ordinateur (GitHub)
1. Créez un dépôt GitHub et envoyez-y ce dossier.
2. Le workflow `.github/workflows/build-apk.yml` compile l'APK à chaque envoi.
3. Onglet **Actions** → dernière exécution → téléchargez `neige-alpes-pyrenees-apk`.
4. Sur le téléphone, ouvrez le fichier `.apk` et autorisez l'installation d'applications inconnues.

## Compiler avec Android Studio
Ouvrez le dossier dans Android Studio (Ladybug ou plus récent), laissez Gradle se synchroniser,
puis **Run ▶** sur un téléphone ou un émulateur. En ligne de commande : `./gradlew assembleDebug`.

## Structure
```
app/src/main/java/fr/neige/stations/
├── MainActivity.kt
├── data/Stations.kt      ← liste des stations (coordonnées, altitudes) — à compléter
├── data/SnowApi.kt       ← appels Open-Meteo
└── ui/                   ← écran Jetpack Compose, ViewModel, thème
```

## Pistes pour la suite
- Écran de détail par station (graphique sur 7 jours)
- Carte des stations
- Widget d'écran d'accueil pour ses favoris
- Version web (projet initial) partageant la même liste de stations
