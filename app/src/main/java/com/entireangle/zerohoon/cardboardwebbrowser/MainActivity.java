/*
 * Copyright 2014 Google Inc. All Rights Reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.entireangle.zerohoon.cardboardwebbrowser;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * A Cardboard sample application.
 */
public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer {

    private static final String TAG = "MainActivity";
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;
    private static final float CAMERA_Z = 0.01f;
    private static final int COORDS_PER_VERTEX = 3;
    private static final WorldLayoutData DATA = new WorldLayoutData();

    // We keep the light always position just above the user.
    private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[] { 0.0f, 2.0f, 0.0f, 1.0f };

    private final float[] mLightPosInEyeSpace = new float[4];

    private FloatBuffer mFloorVertices;
    private FloatBuffer mFloorColors;
    private FloatBuffer mFloorNormals;

    private FloatBuffer mCubeVertices;

    private int mCubeProgram;
    private int mCubePositionParam;
    private int mCubeModelParam;
    private int mCubeModelViewParam;
    private int mCubeModelViewProjectionParam;

    private float[] mModelCube;
    private float[] mCamera;
    private float[] mView;
    private float[] mHeadView;
    private float[] mModelViewProjection;
    private float[] mModelView;
    private float[] mModelFloor;

    private float mObjectDistance = 1.5f;
    private float mFloorDepth = 20f;

    private CardboardOverlayView mOverlayView;
    public static final int GL_TEXTURE_EXTERNAL_OES                                 = 0x8D65;
    private int mTextureID;
    private SurfaceTexture mSurface;
    private float[] mSTMatrix = new float[16];
    private int muSTMatrixHandle;
    private FloatBuffer mTextCoords;
    private int mTextCoordsParam;
    private int mPointerProgram;
    private int mPointerPositionParam;

    private float[] mIntersectionPointerVertices = new float[12];
    private FloatBuffer mFbIntersectionPointerVertices;
    private int mPointerModelViewProjectionParam;
    private float[] mIntersectionPointerVertex = new float[4];
    private CustomWebView mMyWebView;
    private int mEyeViewProjectionParam;

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
     *
     * @param type The type of shader we will be creating.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return The shader object handler.
     */
    private int loadGLShader(int type, int resId) {
        String code = readRawTextFile(resId);
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     *
     * @param label Label to report in case of error.
     */
    private static void checkGLError(String label) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, label + ": glError " + error);
            throw new RuntimeException(label + ": glError " + error);
        }
    }

    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMyWebView = new CustomWebView( this );
        mMyWebView.loadUrl("http://news.google.com");
        mMyWebView.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 4.4; Nexus 5 Build/_BuildID_) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/30.0.0.0 Mobile Safari/537.36");
        mMyWebView.getSettings().setJavaScriptEnabled(true);
        mMyWebView.setWebContentsDebuggingEnabled(true);
        mMyWebView.setVerticalScrollBarEnabled(true);
        mMyWebView.setHorizontalScrollBarEnabled(true);

        mMyWebView.setWebChromeClient(new WebChromeClient(){
            public void onShowCustomView (View view, WebChromeClient.CustomViewCallback callback){
                mOverlayView.show3DToast("fullscreen");

            }
            public boolean onJsAlert (WebView view, String url, String message, JsResult result){
                mOverlayView.show3DToast(message);
                result.confirm();
                return true;
            }

        });

        setContentView(R.layout.activity_main);
        addContentView(mMyWebView,
                new ViewGroup.LayoutParams(mMyWebView.TEXTURE_WIDTH,
                        mMyWebView.TEXTURE_HEIGHT)
        );

        CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        mModelCube = new float[16];
        mCamera = new float[16];
        mView = new float[16];
        mModelViewProjection = new float[16];
        mModelView = new float[16];
        mModelFloor = new float[16];
        mHeadView = new float[16];

        mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);
        mOverlayView.show3DToast("Pull the magnet when you want to click on screen with the red point.");
        Matrix.setIdentityM(mSTMatrix, 0);
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        mTextureID = textures[0];
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureID);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE );
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE );
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_NEAREST);

            /*
             * Create the SurfaceTexture that will feed this textureID,
             * and pass it to the MediaPlayer
             */
        mSurface = new SurfaceTexture(mTextureID);
        mSurface.setDefaultBufferSize( mMyWebView.TEXTURE_WIDTH, mMyWebView.TEXTURE_HEIGHT );
        mMyWebView.surface = new Surface( mSurface );
    }

    /**
     * Creates the buffers we use to store information about the 3D world.
     *
     * OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
     * Hence we use ByteBuffers.
     *
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well.

        ByteBuffer bbVertices = ByteBuffer.allocateDirect(DATA.SQURE_COORDS.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());
        mCubeVertices = bbVertices.asFloatBuffer();
        mCubeVertices.put(DATA.SQURE_COORDS);
        mCubeVertices.position(0);

        ByteBuffer bbTexCoord = ByteBuffer.allocateDirect(DATA.TEX_COORDS.length * 4);
        bbTexCoord.order(ByteOrder.nativeOrder());
        mTextCoords = bbTexCoord.asFloatBuffer();
        mTextCoords.put(DATA.TEX_COORDS);
        mTextCoords.position(0);

        // make a floor
        ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(DATA.FLOOR_COORDS.length * 4);
        bbFloorVertices.order(ByteOrder.nativeOrder());
        mFloorVertices = bbFloorVertices.asFloatBuffer();
        mFloorVertices.put(DATA.FLOOR_COORDS);
        mFloorVertices.position(0);

        ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(DATA.FLOOR_NORMALS.length * 4);
        bbFloorNormals.order(ByteOrder.nativeOrder());
        mFloorNormals = bbFloorNormals.asFloatBuffer();
        mFloorNormals.put(DATA.FLOOR_NORMALS);
        mFloorNormals.position(0);

        ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(DATA.FLOOR_COLORS.length * 4);
        bbFloorColors.order(ByteOrder.nativeOrder());
        mFloorColors = bbFloorColors.asFloatBuffer();
        mFloorColors.put(DATA.FLOOR_COLORS);
        mFloorColors.position(0);

        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
        int passthroughShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);

        mCubeProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mCubeProgram, vertexShader);
        GLES20.glAttachShader(mCubeProgram, passthroughShader);
        GLES20.glLinkProgram(mCubeProgram);
        GLES20.glUseProgram(mCubeProgram);

        checkGLError("Cube program");


        mCubePositionParam = GLES20.glGetAttribLocation(mCubeProgram, "a_Position");
        mTextCoordsParam = GLES20.glGetAttribLocation(mCubeProgram, "a_TextureCoord");
        mCubeModelParam = GLES20.glGetUniformLocation(mCubeProgram, "u_Model");
        mCubeModelViewParam = GLES20.glGetUniformLocation(mCubeProgram, "u_MVMatrix");
        mCubeModelViewProjectionParam = GLES20.glGetUniformLocation(mCubeProgram, "u_MVP");
        muSTMatrixHandle = GLES20.glGetUniformLocation(mCubeProgram, "uSTMatrix");

        GLES20.glEnableVertexAttribArray(mCubePositionParam);
        GLES20.glEnableVertexAttribArray(mTextCoordsParam);
        checkGLError("Cube program params");

        int pointerVertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.pointer_vertex);
        int pointerFragmentShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.pointer_fragment);

        mPointerProgram = GLES20.glCreateProgram();

        GLES20.glAttachShader(mPointerProgram, pointerVertexShader);
        GLES20.glAttachShader(mPointerProgram, pointerFragmentShader);
        GLES20.glLinkProgram(mPointerProgram);
        GLES20.glUseProgram(mPointerProgram);
        mPointerModelViewProjectionParam = GLES20.glGetUniformLocation(mPointerProgram, "u_MVP");
        checkGLError("Pointer program");

        mEyeViewProjectionParam = GLES20.glGetUniformLocation(mPointerProgram, "u_EVP");
        mPointerPositionParam = GLES20.glGetAttribLocation(mPointerProgram, "a_Position");
        checkGLError("Pointer params");
        checkGLError("Floor program params");

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Object first appears directly in front of user.
        Matrix.setIdentityM(mModelCube, 0);
        Matrix.translateM(mModelCube, 0, 0, 0, -mObjectDistance);
        Matrix.setIdentityM(mModelFloor, 0);
        Matrix.translateM(mModelFloor, 0, 0, -mFloorDepth, 0); // Floor appears below user.

        checkGLError("onSurfaceCreated");
    }

    /**
     * Converts a raw text file into a string.
     *
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return The context of the text file, or null in case of error.
     */
    private String readRawTextFile(int resId) {
        InputStream inputStream = getResources().openRawResource(resId);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        // Build the camera matrix and apply it to the ModelView.
        Matrix.setLookAtM(mCamera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        headTransform.getHeadView(mHeadView, 0);
        getFocusIntersectionVertex(mIntersectionPointerVertex, WorldLayoutData.SQURE_VERTEX, WorldLayoutData.SQUARE_NORMAL);
        mIntersectionPointerVertices = getPointerCoordinate(mIntersectionPointerVertex);
        ByteBuffer bbVertices = ByteBuffer.allocateDirect(mIntersectionPointerVertices.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());
        mFbIntersectionPointerVertices = bbVertices.asFloatBuffer();
        mFbIntersectionPointerVertices.put(mIntersectionPointerVertices);
        mFbIntersectionPointerVertices.position(0);

        synchronized(this) {
            mSurface.updateTexImage();
            mSurface.getTransformMatrix(mSTMatrix);
        }

        checkGLError("onReadyToDraw");
    }

    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        checkGLError("mColorParam");

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(mView, 0, eye.getEyeView(), 0, mCamera, 0);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating cube position and light.
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);
        Matrix.multiplyMM(mModelView, 0, mView, 0, mModelCube, 0);
        Matrix.multiplyMM(mModelViewProjection, 0, perspective, 0, mModelView, 0);
        drawCube();
        drawPointer();
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    public void drawPointer(){
        GLES20.glUseProgram(mPointerProgram);
        GLES20.glVertexAttribPointer(mPointerPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, mFbIntersectionPointerVertices);

        GLES20.glUniformMatrix4fv(mPointerModelViewProjectionParam, 1, false, mModelViewProjection, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGLError("Drawing pointer");
    }

    /**
     * Draw the cube.
     *
     * We've set all of our transformation matrices. Now we simply pass them into the shader.
     */
    public void drawCube() {
        GLES20.glUseProgram(mCubeProgram);

        // Set the Model in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(mCubeModelParam, 1, false, mModelCube, 0);

        // Set the ModelView in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(mCubeModelViewParam, 1, false, mModelView, 0);

        // Set the position of the cube
        GLES20.glVertexAttribPointer(mCubePositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, mCubeVertices);

        GLES20.glVertexAttribPointer(mTextCoordsParam, 2, GLES20.GL_FLOAT,
                false, 0, mTextCoords);

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(mCubeModelViewProjectionParam, 1, false, mModelViewProjection, 0);
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGLError("Drawing cube");
    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");
        performWebviewClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        Log.i(TAG, "onCardboardTrigger");
        performWebviewClick();
        return true;
    }

    /**
     * Find a new random position for the object.
     *
     * We'll rotate it around the Y-axis so it's out of sight, and then up or down by a little bit.
     */
    private void hideObject() {
        float[] rotationMatrix = new float[16];
        float[] posVec = new float[4];

        // First rotate in XZ plane, between 90 and 270 deg away, and scale so that we vary
        // the object's distance from the user.
        float angleXZ = (float) Math.random() * 180 + 90;
        Matrix.setRotateM(rotationMatrix, 0, angleXZ, 0f, 1f, 0f);
        float oldObjectDistance = mObjectDistance;
        mObjectDistance = (float) Math.random() * 15 + 5;
        float objectScalingFactor = mObjectDistance / oldObjectDistance;
        Matrix.scaleM(rotationMatrix, 0, objectScalingFactor, objectScalingFactor,
                objectScalingFactor);
        Matrix.multiplyMV(posVec, 0, rotationMatrix, 0, mModelCube, 12);

        // Now get the up or down angle, between -20 and 20 degrees.
        float angleY = (float) Math.random() * 80 - 40; // Angle in Y plane, between -40 and 40.
        angleY = (float) Math.toRadians(angleY);
        float newY = (float) Math.tan(angleY) * mObjectDistance;

        Matrix.setIdentityM(mModelCube, 0);
        Matrix.translateM(mModelCube, 0, posVec[0], newY, posVec[2]);
    }

    private float[]  getPointerCoordinate(float[] v){
        float s = 0.005f;
        float lx = v[0] - s;
        float rx = v[0] + s;
        float by = v[1] - s;
        float ty = v[1] + s;
        float z = v[2] + 0.05f;

        return new float[]{
                lx, by, z,
                rx, by, z,
                lx, ty, z,
                rx, ty, z
        };
    }

    private void getFocusIntersectionVertex(float[] intersectionVertex, float[] planeVertex, float[] normalVector){

        float[] cPlaneVertex = new float[4];
        float[] cNormalVector = new float[4];

        Matrix.multiplyMM(mModelView, 0, mHeadView, 0, mModelCube, 0);
        Matrix.multiplyMV(cPlaneVertex, 0, mModelView, 0, planeVertex, 0);
        Matrix.multiplyMV(cNormalVector, 0, mModelView, 0, normalVector, 0);

        float t = (cNormalVector[0] * cPlaneVertex[0] + cNormalVector[1] * cPlaneVertex[1]) / cNormalVector[2] + cPlaneVertex[2];
        float[] cIntersectionVertex = new float[]{0, 0, t, 1.0f};

        float[] invertedModelView = new float[16];
        Matrix.invertM(invertedModelView, 0, mModelView, 0);
        Matrix.multiplyMV(intersectionVertex,0, invertedModelView, 0, cIntersectionVertex, 0);
    }

    /**
     * Check if user is looking at object by calculating where the object is in eye-space.
     *
     * @return true if the user is looking at the object.
     */
    private void performWebviewClick() {
        float x = (mIntersectionPointerVertex[0] + 1.0f) * 500.0f * 3;
        float y = (-mIntersectionPointerVertex[1] + 1.0f) * 500.0f * 3;
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis() + 10;

        int metaState = 0;
        MotionEvent motionEventDown = MotionEvent.obtain(
                downTime,
                eventTime,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                metaState
        );

        MotionEvent motionEventUp = MotionEvent.obtain(
                downTime,
                eventTime,
                MotionEvent.ACTION_UP,
                x,
                y,
                metaState
        );
        mMyWebView.dispatchTouchEvent(motionEventDown);
        mMyWebView.dispatchTouchEvent(motionEventUp);
    }
}
