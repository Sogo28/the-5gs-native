// assets/shaders/axis.vert
precision mediump float;

// Une 'uniform' est une variable globale que l'on passe depuis le code Kotlin.
// 'u_MvpMatrix' contiendra la matrice Model-View-Projection combinée.
uniform mat4 u_MvpMatrix;

// Un 'attribute' est une donnée d'entrée pour chaque sommet (vertex).
// 'a_Position' est la position (x, y, z) de notre sommet dans l'espace du modèle.
attribute vec4 a_Position;

void main() {
    // gl_Position est une variable spéciale qui définit la position finale du sommet.
    gl_Position = u_MvpMatrix * a_Position;
}