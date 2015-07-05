#extension GL_OES_EGL_image_external : require
precision mediump float;
//varying vec4 v_Color;
varying vec2 v_TextureCoord;
uniform samplerExternalOES sTexture;

void main() {
    //gl_FragColor = vec4(1.0f,0.1f,0.1f,1.0f);
    gl_FragColor = texture2D(sTexture, v_TextureCoord);
}
