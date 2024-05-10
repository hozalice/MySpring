#!/bin/bash

# Répertoires
TEMP_SRC="temp_src"
MY_CLASSES="classes"


# Création du répertoire temporaire
# Vérifier si le répertoire de destination existe
if [ -d "$TEMP_SRC" ]; then
    # Si le répertoire de destination existe, supprimez-le
    echo "Le répertoire de destination existe déjà. Suppression en cours..."
    rm -r "$TEMP_SRC"
    echo "Répertoire de destination supprimé avec succès."
fi
mkdir -p "$TEMP_SRC"
echo "Répertoire temporaire pour source créé"

# Création du répertoire temporaire
# Vérifier si le répertoire de destination existe
if [ -d "$MY_CLASSES" ]; then
    # Si le répertoire de destination existe, supprimez-le
    echo "Le répertoire de destination existe déjà. Suppression en cours..."
    rm -r "$MY_CLASSES"
    echo "Répertoire de destination supprimé avec succès."
fi
mkdir -p "$MY_CLASSES"
echo "Répertoire temporaire pour les .class"

find "src" -name "*.java" -exec cp {} "$TEMP_SRC" \;

# Compilation des fichiers Java du répertoire source vers classes
javac -cp "lib\*" -d "$MY_CLASSES" "$TEMP_SRC/*.java"
echo "Fichiers Java compilés dans le répertoire classes"

# Création du fichier jar
jar cf "lib/front-controller.jar" -C "$MY_CLASSES" .
echo "Fichier jar créé avec succès."