// assets/shaders/axis.frag
precision mediump float;

// 'u_Color' est la couleur que nous enverrons depuis notre code Kotlin.
uniform vec4 u_Color;

void main() {
    // gl_FragColor est une variable spéciale qui définit la couleur finale du pixel.
    gl_FragColor = u_Color;
}