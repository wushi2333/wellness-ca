/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package iss.nus.edu.sg.ca_application.live2d;

import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.rendering.android.CubismOffscreenManagerAndroid;

/**
 * サンプルアプリケーションにおいてCubismModelを管理するクラス。
 * モデル生成と破棄、タップイベントの処理、モデル切り替えを行う。
 */
public class LAppMinimumLive2DManager {
    public static LAppMinimumLive2DManager getInstance() {
        if (s_instance == null) {
            s_instance = new LAppMinimumLive2DManager();
        }
        return s_instance;
    }

    public static void releaseInstance() {
        if (s_instance != null) {
            CubismOffscreenManagerAndroid.releaseInstance();
        }
        s_instance = null;
    }

    public void loadModel(String dirName, String modelFileName) {
        String dir = dirName + "/";
        model = new LAppMinimumModel(dir);
        model.loadAssets(dir, modelFileName + ".model3.json");
    }

    // モデル更新処理及び描画処理を行う
    public void onUpdate() {
        int width = LAppMinimumDelegate.getInstance().getWindowWidth();
        int height = LAppMinimumDelegate.getInstance().getWindowHeight();
        float aspectRatio = (float) width / (float) height;
        float displayRatio = (float) height / (float) width;

        // モデルで使用するオフスクリーン管理の開始処理
        CubismOffscreenManagerAndroid.getInstance().beginFrameProcess();

        projection.loadIdentity();

        float canvasRatio = model.getModel().getCanvasHeight() / model.getModel().getCanvasWidth();

        if (canvasRatio < displayRatio) {
            model.getModelMatrix().setWidth(3.4f);
            projection.scale(1.0f, aspectRatio);
        } else {
            model.getModelMatrix().setHeight(3.4f);
            projection.scale(1.0f / aspectRatio, 1.0f);
        }
        // Shift model down (absolute, non-accumulating)
        model.getModelMatrix().translate(0f, -0.8f);

        // 必要があればここで乗算する
        if (viewMatrix != null) {
            viewMatrix.multiplyByMatrix(projection);
        }

        // 描画前コール
        LAppMinimumDelegate.getInstance().getView().preModelDraw(model);

        model.update();
        model.draw(projection);

        // Signal first frame ready
        if (!firstFrameDone) {
            firstFrameDone = true;
            if (readyCallback != null) readyCallback.onReady();
            if (staticReadyCallback != null) { staticReadyCallback.onReady(); staticReadyCallback = null; }
        }

        // 描画後コール
        LAppMinimumDelegate.getInstance().getView().postModelDraw(model);

        // モデルで使用するオフスクリーン管理の終了処理
        CubismOffscreenManagerAndroid.getInstance().endFrameProcess();
        // もし余っているオフスクリーンのリソースを解放したい場合行う処理
        CubismOffscreenManagerAndroid.getInstance().releaseStaleRenderTextures();
    }

    /**
     * 画面をドラッグした時の処理
     *
     * @param x 画面のx座標
     * @param y 画面のy座標
     */
    public void onDrag(float x, float y) {
        model.setDragging(x, y);
    }

    /**
     * 現在のシーンで保持しているモデルを返す
     *
     * @param number モデルリストのインデックス値
     * @return モデルのインスタンスを返す。インデックス値が範囲外の場合はnullを返す
     */
    public LAppMinimumModel getModel(int number) {
        return model;
    }

    /**
     * モデルのオフスクリーンのサイズを設定する。
     *
     * @param width  ウィンドウの幅
     * @param height ウィンドウの高さ
     */
    public void setRenderTargetSize(int width, int height) {
        if (model != null) {
            model.setRenderTargetSize(width, height);
        }
    }

    /**
     * シングルトンインスタンス
     */
    private static LAppMinimumLive2DManager s_instance;

    public interface ReadyCallback { void onReady(); }
    private static ReadyCallback staticReadyCallback;
    private ReadyCallback readyCallback;
    private boolean firstFrameDone;

    public static void setOnReadyStatic(ReadyCallback cb) { staticReadyCallback = cb; }
    public void setOnReady(ReadyCallback cb) { readyCallback = cb; }

    /** Reset first-frame flag so the ready callback fires again on warm starts. */
    public void resetFirstFrameFlag() {
        firstFrameDone = false;
    }

    /** Check if model data is already cached in memory (warm start). */
    public static boolean isModelCached() {
        return s_instance != null && s_instance.model != null;
    }

    private LAppMinimumLive2DManager() {
        loadModel("YouXiaoMiao", "悠小喵");
        // Model has no Expressions/Motions in model3.json — set up manually
        model.loadExpressionFromFile("phone", "看手机.exp3.json");
        model.loadExpressionFromFile("notes", "记笔记.exp3.json");
        model.loadExpressionFromFile("starEyes", "星星眼.exp3.json");
        model.loadExpressionFromFile("cry", "哭哭.exp3.json");
        model.loadExpressionFromFile("blush", "脸红.exp3.json");
        model.loadExpressionFromFile("dizzy", "晕晕眼.exp3.json");
        model.loadExpressionFromFile("leanForward", "前倾.exp3.json");
        model.loadExpressionFromFile("tears", "流泪.exp3.json");
        model.loadExpressionFromFile("watermark", "水印开关.exp3.json");
        // Register expression updater so expressions take effect
        model.setupManualExpressions();
        // Start idle breathing loop
        model.setupIdleMotion();
        // Disable watermark via expression toggle
        model.startExpression("watermark");
        // Register eye blink
        model.setupEyeBlink();
    }

    public LAppMinimumModel getCurrentModel() {
        return model;
    }

    private LAppMinimumModel model;

    private final CubismMatrix44 viewMatrix = CubismMatrix44.create();
    private final CubismMatrix44 projection = CubismMatrix44.create();
}

