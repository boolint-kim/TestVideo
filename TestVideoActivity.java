package com.boolint.camlocation;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.DrawableRes;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.boolint.camlocation.bean.CctvItemVo;
import com.boolint.camlocation.helper.ADHelper;
import com.boolint.camlocation.helper.CctvApiHelper;
import com.boolint.camlocation.helper.DaeguCctvVideoOpenApiHelper;
import com.boolint.camlocation.helper.DeviceHelper;
import com.boolint.camlocation.helper.GgCctvVideoOpenApiHelper;
import com.boolint.camlocation.helper.JejuCctvVideoOpenApiHelper;
import com.boolint.camlocation.helper.SeoulCctvVideoOpenApiHelper;


public class TestVideoActivity extends AppCompatActivity {

    private static final String TAG = "ttt";

    // Player 타입 정의
    private enum PlayerType {
        EXOPLAYER,
        WEBVIEW
    }

    private PlayerType currentPlayerType = PlayerType.EXOPLAYER;
    private boolean isFirstWebViewLoad = true;

    private boolean isLandscape = false;
    private boolean isLockedLandscape = false;
    private boolean isLockedPortrait = false;

    // 고정 해제 딜레이를 위한 타임스탬프
    private long lockTimestamp = 0;
    private static final long LOCK_DELAY_MS = 1000; // 1초 딜레이

    // 센서 범위를 더 엄격하게
    private static final int LANDSCAPE_MIN = 80;
    private static final int LANDSCAPE_MAX = 100;
    private static final int LANDSCAPE_MIN_REVERSE = 260;
    private static final int LANDSCAPE_MAX_REVERSE = 280;

    private static final int PORTRAIT_MIN = 350;
    private static final int PORTRAIT_MAX = 10;
    private static final int PORTRAIT_MIN_REVERSE = 170;
    private static final int PORTRAIT_MAX_REVERSE = 190;

    private ContentObserver rotationObserver;
    private OrientationEventListener orientationListener;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private Matrix matrix = new Matrix();
    private float scaleFactor = 1.0f;
    private float focusX = 0f, focusY = 0f;
    MediaItem mediaItem;

    TextView tvTitle;
    ImageView imgCctvType;
    TextView tvCopyRight;
    ImageView imgFavor;

    ImageView imgScreen;
    private ImageView ivHome;

    LinearLayout llFavor;
    LinearLayout llScreen;
    boolean isFavor = false;
    CctvItemVo mCctvItem;
    LinearLayout llProgress;
    LinearLayout llError;  // ✅ 추가
    TextView tvErrorMessage;  // ✅ 추가
    TextView tvErrorDetail;  // ✅ 추가

    private Handler webViewTimeoutHandler;
    private Runnable webViewTimeoutRunnable;

    private Surface videoSurface;

    // 비디오 크기 저장용 변수
    private int currentVideoWidth = 0;
    private int currentVideoHeight = 0;

    Handler timeoutHandler;
    Runnable timeoutRunnable;

    private static final int MESSAGE_CCTV_EXOPLAYER = 101;
    private static final int MESSAGE_CCTV_WEBVIEW = 102;

    CctvNavigator navigator;

    CardView layoutLeft;
    ImageView btnLeftIcon;
    TextView btnLeftLabel;

    CardView layoutRight;
    ImageView btnRightIcon;
    TextView btnRightLabel;

    private TextureView textureView;
    private WebView webView;
    private ExoPlayer exoPlayer;
    private CctvApiHelper apiHelper;

    // ... 나머지 변수들 ...

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_test_video);

        apiHelper = new CctvApiHelper();

        initializeViews();
        setupExoPlayer();
        setupWebView();
        setupUI();

        updateCctvVideo();
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tvTitle);
        imgCctvType = findViewById(R.id.imgCctvType);
        tvCopyRight = findViewById(R.id.tvCopyRight);
        imgFavor = findViewById(R.id.imgFavor);
        imgScreen = findViewById(R.id.img_screen);
        llProgress = findViewById(R.id.ll_progress);

        // ✅ 에러 뷰 초기화
        llError = findViewById(R.id.ll_error);
        tvErrorMessage = findViewById(R.id.tv_error_message);
        tvErrorDetail = findViewById(R.id.tv_error_detail);

        textureView = findViewById(R.id.textureView);
        webView = findViewById(R.id.webview);
        layoutLeft = findViewById(R.id.layoutLeft);
        btnLeftIcon = findViewById(R.id.btnLeftIcon);
        btnLeftLabel = findViewById(R.id.btnLeftLabel);
        layoutRight = findViewById(R.id.layoutRight);
        btnRightIcon = findViewById(R.id.btnRightIcon);
        btnRightLabel = findViewById(R.id.btnRightLabel);
        llFavor = findViewById(R.id.llFavor);
        llScreen = findViewById(R.id.ll_screen);
        ivHome = findViewById(R.id.iv_home);

        llProgress.setVisibility(VISIBLE);

        // Navigator 초기화
        mCctvItem = MainData.mCurrentCctvItemVo;
        if (MainData.mCctvNavigator != null) {
            navigator = MainData.mCctvNavigator;
            layoutLeft.setVisibility(VISIBLE);
            layoutRight.setVisibility(VISIBLE);
        } else {
            navigator = null;
            layoutLeft.setVisibility(GONE);
            layoutRight.setVisibility(GONE);
        }
    }

    private void setupExoPlayer() {
        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (videoSurface != null) {
                    videoSurface.release();
                }
                videoSurface = new Surface(surface);
                exoPlayer.setVideoSurface(videoSurface);
                applyVideoFit();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                applyVideoFit();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                try {
                    if (exoPlayer != null) {
                        exoPlayer.clearVideoSurface();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error clearing video surface", e);
                }

                if (videoSurface != null) {
                    try {
                        videoSurface.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing surface", e);
                    } finally {
                        videoSurface = null;
                    }
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });

        if (textureView.isAvailable()) {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture != null) {
                videoSurface = new Surface(surfaceTexture);
                exoPlayer.setVideoSurface(videoSurface);
            }
        }

        setupExoPlayerListeners();
        setupGestureDetectors();
    }

    private void setupExoPlayerListeners() {
        final int TIMEOUT_MS = 10000;
        timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutRunnable = () -> {
            if (exoPlayer.getPlaybackState() == Player.STATE_BUFFERING) {
                llProgress.setVisibility(GONE);
                // ✅ Toast 대신 화면 에러 표시
                showError("재생 시간 초과", "네트워크 연결을 확인해주세요");
                exoPlayer.stop();
            }
        };

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                currentVideoWidth = videoSize.width;
                currentVideoHeight = videoSize.height;
                fitVideoToView(videoSize);
                textureView.setVisibility(VISIBLE);
                //hideProgressWithAnimation();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
                } else {
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                }

                if (state == Player.STATE_READY) {
                    textureView.setVisibility(VISIBLE);
                    // ✅ 실제 재생 준비 완료 시 숨김
                    hideProgressWithAnimation();
                    // ✅ 에러도 숨김
                    hideError();
                } else if (state == Player.STATE_ENDED) {
                    Log.d(TAG, "STATE_ENDED");
                } else {
                    textureView.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                // ✅ 추가: 실제 재생이 시작되면 확실히 숨김
                if (isPlaying) {
                    hideProgressWithAnimation();
                    // ✅ 에러도 숨김
                    hideError();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                llProgress.setVisibility(GONE);
                // ✅ Toast 대신 화면 에러 표시
                showError("영상 연결 오류", "CCTV를 불러올 수 없습니다");
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        // Handler 초기화
        webViewTimeoutHandler = new Handler(Looper.getMainLooper());

        WebSettings ws = webView.getSettings();

        // JavaScript & DOM
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);

        // 캐시 & 버퍼링 최적화
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);  // CCTV 스트림은 캐시 없이
        ws.setDatabaseEnabled(true);

        // 하드웨어 가속 (가장 중요!)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 렌더링 우선순위 (API 33 이전)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ws.setRenderPriority(WebSettings.RenderPriority.HIGH);
        }

        // 이미지 로딩
        ws.setLoadsImagesAutomatically(true);
        ws.setBlockNetworkImage(false);

        // Mixed Content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // 줌
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);

        // UI
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setBackgroundColor(0xFF000000);
        // ✅ JavaScript Bridge 추가
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public View getVideoLoadingProgressView() {
                Log.d("ttt", "setWebChromeClient: getVideoLoadingProgressView: " + String.format("%s", ""));
                return new View(TestVideoActivity.this);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                Log.d("ttt", "setWebChromeClient: onShowCustomView: " + String.format("%s", ""));
                // Fullscreen video support
            }

            @Override
            public void onHideCustomView() {
                Log.d("ttt", "setWebChromeClient: onHideCustomView: " + String.format("%s", ""));
                // Exit fullscreen
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                Log.d("ttt", "setWebChromeClient: onProgressChanged: " + String.format("%d", newProgress));
                // 초기 10%까지 계속 주입 (썸네일 완전 차단)
                if (newProgress <= 1) {
                    injectAllScripts(view);
                }
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                Log.d("ttt", "setWebChromeClient: onJsAlert: " + String.format("%s", message));
                result.confirm();   // 팝업 안 뜸
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                Log.d("ttt", "setWebChromeClient: onJsConfirm: " + String.format("%s", message));
                result.confirm();   // 자동 YES
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // 같은 페이지 리로드 차단
                if (url.equals(view.getUrl())) {
                    Log.d("ttt", "자동 새로고침 차단: " + url);
                    return true; // 리로드 방지
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // 같은 URL 리로드 차단
                if (url.equals(view.getUrl())) {
                    Log.d("ttt", "자동 새로고침 차단: " + url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.d("ttt", "setWebViewClient: onPageStarted: " + String.format("%s", "url"));
                // ✅ 이전 타임아웃 취소
                cancelWebViewTimeout();

                injectBaseCSSImmediately(view);
                injectAllScripts(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d("ttt", "setWebViewClient: onPageFinished: " + String.format("%s", "url"));
                injectAllScripts(view);

                // 한 번 더 주입 (동적 로딩되는 video 태그 대응)
                view.postDelayed(() -> injectAllScripts(view), 300);

                // ✅ 새로운 타임아웃 설정
                startWebViewTimeout();

            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Log.d("ttt", "setWebViewClient: onReceivedSslError: " + String.format("%s", error));
                handler.proceed();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.d("ttt", "setWebViewClient: onReceivedError: " + String.format("%s", error));
                if (request.isForMainFrame()) {
                    llProgress.setVisibility(GONE);
                    // ✅ Toast 대신 화면 에러 표시
                    showError("페이지 로드 실패", "WebView 로딩 오류");
                }
            }
        });
    }
    /**
     * ✅ WebView 타임아웃 시작
     */
    private void startWebViewTimeout() {
        cancelWebViewTimeout();

        webViewTimeoutRunnable = () -> {
            if (llProgress.getVisibility() == VISIBLE) {
                Log.d(TAG, "⚠️ Timeout: Hiding progress after 10 seconds");
                hideProgressWithAnimation();
                // ✅ Toast 대신 화면 에러 표시
                showError("영상 로드 시간 초과", "스트리밍 서버 응답 없음");
            }
        };

        webViewTimeoutHandler.postDelayed(webViewTimeoutRunnable, 10000);
    }

    /**
     * ✅ WebView 타임아웃 취소
     */
    private void cancelWebViewTimeout() {
        if (webViewTimeoutHandler != null && webViewTimeoutRunnable != null) {
            webViewTimeoutHandler.removeCallbacks(webViewTimeoutRunnable);
        }
    }

    // ✅ JavaScript Bridge 클래스 추가
    public class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void onVideoPlaying() {
            runOnUiThread(() -> {
                Log.d(TAG, "✅ WebView: Video actually playing - hiding progress");
                // 타임아웃 취소
                cancelWebViewTimeout();

                hideProgressWithAnimation();
                // ✅ 에러도 숨김
                hideError();
            });
        }
    }

    /**
     * 페이지 시작과 동시에 기본 CSS 주입 (깜박임 최소화)
     */
    private void injectBaseCSSImmediately(WebView view) {
        String js =
                "javascript:(function(){ " +
                        "if(!document.getElementById('cctv-base-style')){ " +
                        "var s=document.createElement('style');" +
                        "s.id='cctv-base-style';" +
                        "s.innerHTML=`" +
                        "html,body{margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important;width:100vw!important;height:100vh!important;}" +
                        "video{" +
                        "display:block!important;" +
                        "width:100vw!important;" +
                        "height:100vh!important;" +
                        "object-fit:contain!important;" +
                        "background:black!important;" +
                        "position:fixed!important;" +
                        "top:0!important;left:0!important;" +
                        "margin:0!important;padding:0!important;" +
                        "transform:none!important;" +
                        "z-index:9999!important;" +
                        "pointer-events:none!important;" +
                        "opacity:1!important;" +
                        "visibility:visible!important;" +
                        "}" +
                        "video::-webkit-media-controls-panel,video::-webkit-media-controls-play-button,video::-webkit-media-controls-start-playback-button,video::-webkit-media-controls-overlay-play-button,video::-webkit-media-controls-enclosure,video::-webkit-media-controls{display:none!important;opacity:0!important;visibility:hidden!important;pointer-events:none!important;}" +
                        "*[poster]{background:transparent!important;}" +
                        "video[poster]{background:black!important;}" +
                        "`;" +
                        "document.head.appendChild(s);" +
                        "}})();";

        view.evaluateJavascript(js, null);
    }

    /**
     * 모든 스크립트 (버퍼링 최적화 포함)
     */
    private void injectAllScripts(WebView view) {
        String js =
                "javascript:(function(){ " +
                        // CSS (기존 코드 유지)
                        "if(!document.getElementById('cctv-base-style')){ " +
                        "var s=document.createElement('style');" +
                        "s.id='cctv-base-style';" +
                        "s.innerHTML=`" +
                        "html,body{margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important;width:100vw!important;height:100vh!important;}" +
                        "video{" +
                        "display:block!important;" +
                        "width:100vw!important;" +
                        "height:100vh!important;" +
                        "object-fit:contain!important;" +
                        "background:black!important;" +
                        "position:fixed!important;" +
                        "top:0!important;left:0!important;" +
                        "margin:0!important;padding:0!important;" +
                        "transform:none!important;" +
                        "z-index:9999!important;" +
                        "pointer-events:none!important;" +
                        "opacity:1!important;" +
                        "visibility:visible!important;" +
                        "}" +
                        "video::-webkit-media-controls-panel,video::-webkit-media-controls-play-button,video::-webkit-media-controls-start-playback-button,video::-webkit-media-controls-overlay-play-button,video::-webkit-media-controls-enclosure,video::-webkit-media-controls{display:none!important;opacity:0!important;visibility:hidden!important;pointer-events:none!important;}" +
                        "*[poster]{background:transparent!important;}" +
                        "video[poster]{background:black!important;}" +
                        "`;" +
                        "document.head.appendChild(s);" +
                        "}" +

                        // Video 설정 + ✅ 실제 재생 감지 추가
                        "document.querySelectorAll('video').forEach(function(v){ " +
                        "v.controls=false;" +
                        "v.removeAttribute('controls');" +
                        "v.removeAttribute('poster');" +
                        "v.poster='';" +
                        "v.setAttribute('poster', 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7');" +
                        "v.style.cssText='display:block!important;width:100vw!important;height:100vh!important;position:fixed!important;top:0!important;left:0!important;object-fit:contain!important;background:black!important;margin:0!important;padding:0!important;z-index:9999!important;';" +
                        "v.autoplay=true;" +
                        "v.muted=true;" +
                        "v.playsInline=true;" +
                        "v.webkitPlaysInline=true;" +
                        "v.preload='auto';" +
                        "v.setAttribute('preload', 'auto');" +

                        // ✅ 실제 재생 시작 감지
                        "if(!v.hasPlayingListener){" +
                        "v.hasPlayingListener=true;" +
                        "v.addEventListener('playing', function(){" +
                        "console.log('Video actually playing!');" +
                        "window.AndroidBridge && window.AndroidBridge.onVideoPlaying();" +  // Java 콜백
                        "}, {once: true});" +  // 한 번만 실행
                        "}" +

                        // 버퍼링 최적화 이벤트 (기존 코드)
                        "if(!v.hasBufferingListeners){" +
                        "v.hasBufferingListeners=true;" +
                        "v.addEventListener('stalled', function(){" +
                        "setTimeout(function(){" +
                        "if(v.paused){v.play().catch(function(){});}" +
                        "}, 500);" +
                        "});" +
                        "v.addEventListener('suspend', function(){" +
                        "if(v.paused){v.play().catch(function(){});}" +
                        "});" +
                        "v.addEventListener('waiting', function(){" +
                        "setTimeout(function(){" +
                        "if(v.paused && v.readyState >= 2){v.play().catch(function(){});}" +
                        "}, 500);" +
                        "});" +
                        "v.addEventListener('loadstart', function(){" +
                        "v.removeAttribute('poster');v.poster='';v.controls=false;v.removeAttribute('controls');" +
                        "});" +
                        "v.addEventListener('loadedmetadata', function(){" +
                        "v.removeAttribute('poster');v.poster='';v.controls=false;v.removeAttribute('controls');" +
                        "v.style.cssText='display:block!important;width:100vw!important;height:100vh!important;position:fixed!important;top:0!important;left:0!important;object-fit:contain!important;background:black!important;margin:0!important;padding:0!important;z-index:9999!important;';" +
                        "});" +
                        "v.addEventListener('canplay', function(){" +
                        "v.controls=false;v.removeAttribute('controls');" +
                        "if(v.paused){v.play().catch(function(){});}" +
                        "});" +
                        "}" +

                        "if(v.paused && v.readyState >= 2){" +
                        "v.play().catch(function(){});" +
                        "}" +
                        "});" +

                        // MutationObserver (기존 코드 유지)
                        "if(!window.videoObserverInstalled){" +
                        "window.videoObserverInstalled=true;" +
                        "var observer=new MutationObserver(function(mutations){" +
                        "mutations.forEach(function(mutation){" +
                        "mutation.addedNodes.forEach(function(node){" +
                        "if(node.tagName==='VIDEO'){" +
                        "node.removeAttribute('poster');node.poster='';node.controls=false;node.removeAttribute('controls');" +
                        "node.muted=true;node.autoplay=true;" +
                        "node.style.cssText='display:block!important;width:100vw!important;height:100vh!important;position:fixed!important;top:0!important;left:0!important;object-fit:contain!important;background:black!important;z-index:9999!important;';" +

                        // ✅ 새로 추가된 video에도 playing 이벤트 추가
                        "node.addEventListener('playing', function(){" +
                        "window.AndroidBridge && window.AndroidBridge.onVideoPlaying();" +
                        "}, {once: true});" +

                        "node.play().catch(function(){});" +
                        "}" +
                        "if(node.querySelectorAll){" +
                        "node.querySelectorAll('video').forEach(function(v){" +
                        "v.removeAttribute('poster');v.poster='';v.controls=false;v.removeAttribute('controls');" +
                        "v.muted=true;v.autoplay=true;" +
                        "v.style.cssText='display:block!important;width:100vw!important;height:100vh!important;position:fixed!important;top:0!important;left:0!important;object-fit:contain!important;background:black!important;z-index:9999!important;';" +

                        // ✅ 동적 추가된 video에도 playing 이벤트
                        "v.addEventListener('playing', function(){" +
                        "window.AndroidBridge && window.AndroidBridge.onVideoPlaying();" +
                        "}, {once: true});" +

                        "v.play().catch(function(){});" +
                        "});" +
                        "}" +
                        "});" +
                        "});" +
                        "});" +
                        "observer.observe(document.body,{childList:true,subtree:true,attributes:true,attributeFilter:['poster','controls']});" +
                        "}" +
                        "})();";

        view.evaluateJavascript(js, null);
    }

    private void setupGestureDetectors() {
        scaleGestureDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        if (currentPlayerType != PlayerType.EXOPLAYER) return false;

                        float scaleFactorDelta = detector.getScaleFactor();
                        scaleFactor *= scaleFactorDelta;
                        scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f));

                        float focusX = detector.getFocusX();
                        float focusY = detector.getFocusY();

                        matrix.postScale(scaleFactorDelta, scaleFactorDelta, focusX, focusY);
                        textureView.setTransform(matrix);
                        return true;
                    }
                });

        gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                        if (currentPlayerType != PlayerType.EXOPLAYER) return false;

                        matrix.postTranslate(-distanceX, -distanceY);
                        textureView.setTransform(matrix);
                        return true;
                    }
                });

        textureView.setOnTouchListener((v, event) -> {
            if (currentPlayerType == PlayerType.EXOPLAYER) {
                scaleGestureDetector.onTouchEvent(event);
                gestureDetector.onTouchEvent(event);
                return true;
            }
            return false;
        });
    }

    private void setupUI() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            } else {
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            }
            return insets;
        });

        int currentOrientation = getResources().getConfiguration().orientation;
        isLandscape = (currentOrientation == Configuration.ORIENTATION_LANDSCAPE);
        applyBarsByOrientation(currentOrientation);
        updateButtonIcon();

        imgScreen.setOnClickListener(v -> toggleOrientation());
        ivHome.setOnClickListener(v -> onBackPressed());

        llFavor.setOnClickListener(v -> toggleFavorite());
        llScreen.setOnClickListener(v -> toggleOrientation());

        layoutLeft.setOnClickListener(v -> navigateLeft());
        layoutRight.setOnClickListener(v -> navigateRight());

        View layoutActionBar = findViewById(R.id.layout_actionbar);
        ViewCompat.setOnApplyWindowInsetsListener(layoutActionBar, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            int offset = (int) (systemBars.top * 1.2f);
            if (offset == 0) {
                offset = dpToPx(this, 10);
            }
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.topMargin = offset;
            v.setLayoutParams(lp);
            return insets;
        });

        setupRotationObserver();
        setupOrientationListener();

        ADHelper.updateAdVisibilityForDeviceConfiguration(this);
        ADHelper.settingAdEx(this);
        ADHelper.loadAdMobInterstitialAd(this);
    }

    void updateCctvVideo() {
        // ✅ 에러 메시지 숨김 (새 영상 로드 시)
        hideError();

        // 플레이어 타입 결정
        PlayerType newPlayerType = "utic".equals(mCctvItem.getRoadType())
                ? PlayerType.WEBVIEW
                : PlayerType.EXOPLAYER;

        // 이전 플레이어 정리
        stopCurrentPlayer();

        // 새 플레이어 타입 설정
        currentPlayerType = newPlayerType;
        switchPlayerVisibility(currentPlayerType);

        // 프로그레스 표시
        llProgress.setAlpha(1f);
        llProgress.setVisibility(VISIBLE);

        // CCTV 타입별 비디오 로드
        if (mCctvItem != null) {
            loadCctvVideoByType();
        }
    }

    private void stopCurrentPlayer() {
        // ExoPlayer 정리
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearVideoSurface();
        }

        // WebView 정리
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");

            // ✅ 타임아웃 취소
            cancelWebViewTimeout();

            // 재로드 시 WebView 초기화
            if (!isFirstWebViewLoad && currentPlayerType == PlayerType.WEBVIEW) {
                webView.clearCache(true);
                webView.clearHistory();
            }
        }
    }

    private void switchPlayerVisibility(PlayerType playerType) {
        if (playerType == PlayerType.WEBVIEW) {
            textureView.setVisibility(GONE);
            webView.setVisibility(VISIBLE);
        } else {
            textureView.setVisibility(View.INVISIBLE);
            webView.setVisibility(GONE);
        }
    }

    private void loadCctvVideoByType() {
        String roadType = mCctvItem.getRoadType();

        switch (roadType) {
            case "seoul":
                startSeoulCctvVideo();
                break;
            case "jeju":
                startJejuCctvVideo();
                break;
            case "gg":
                startGgCctvVideo();
                break;
            case "daegu":
                startDaeguCctvVideo();
                break;
            case "utic":
                startUticCctvVideoWithApi();
                break;
            default:
                startCctvVideo();
                break;
        }
    }

    // ExoPlayer용 CCTV 로드 메서드들
    private void startCctvVideo() {
        new Thread(() -> {
            try {
                Message msg = handler.obtainMessage();
                msg.what = MESSAGE_CCTV_EXOPLAYER;
                handler.sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startSeoulCctvVideo() {
        new Thread(() -> {
            try {
                String cctvUrl = SeoulCctvVideoOpenApiHelper.getSeoulCctvUrl(mCctvItem.roadSectionId);
                mCctvItem.cctvUrl = cctvUrl;

                Message msg = handler.obtainMessage();
                msg.what = MESSAGE_CCTV_EXOPLAYER;
                handler.sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startJejuCctvVideo() {
        new Thread(() -> {
            try {
                String url1 = JejuCctvVideoOpenApiHelper.getCctvInfoAndSetCookie(mCctvItem.roadSectionId);
                String cctvUrl = JejuCctvVideoOpenApiHelper.getCctvStreamUrl(url1);
                mCctvItem.cctvUrl = cctvUrl;

                Message msg = handler.obtainMessage();
                msg.what = MESSAGE_CCTV_EXOPLAYER;
                handler.sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startGgCctvVideo() {
        new Thread(() -> {
            try {
                String tempUrl = GgCctvVideoOpenApiHelper.getUrl1(mCctvItem.roadSectionId);
                Log.d(TAG, "Gg1: " + tempUrl);

                mCctvItem.cctvUrl = GgCctvVideoOpenApiHelper.getUrl2(tempUrl);
                Log.d(TAG, "Gg2: " + mCctvItem.cctvUrl);

                Message msg = handler.obtainMessage();
                msg.what = MESSAGE_CCTV_EXOPLAYER;
                handler.sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startDaeguCctvVideo() {
        new Thread(() -> {
            try {
                mCctvItem.cctvUrl = DaeguCctvVideoOpenApiHelper.getUrl(mCctvItem.roadSectionId);

                Message msg = handler.obtainMessage();
                msg.what = MESSAGE_CCTV_EXOPLAYER;
                handler.sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    /**
     * UTIC CCTV - CctvApiHelper 사용
     */
    private void startUticCctvVideoWithApi() {
        Log.d(TAG, "🚀 UTIC CCTV 로드: " + mCctvItem.roadSectionId);

        apiHelper.getCctvInfo(mCctvItem.roadSectionId, new CctvApiHelper.CctvResponseListener() {
            @Override
            public void onSuccess(CctvApiHelper.CctvInfo cctvInfo) {
                Log.d(TAG, "✅ CCTV 정보 받음: " + cctvInfo.toString());

                runOnUiThread(() -> {
                    Message msg = handler.obtainMessage();
                    msg.what = MESSAGE_CCTV_WEBVIEW;
                    msg.obj = cctvInfo.getStreamPageUrl();
                    handler.sendMessage(msg);
                });
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "❌ CCTV 정보 로드 실패: " + error);

                runOnUiThread(() -> {
                    sendErrorMessage();
                });
            }
        });
    }

    private void sendErrorMessage() {
        Message msg = handler.obtainMessage();
        msg.what = -1;
        handler.sendMessage(msg);
    }

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_CCTV_EXOPLAYER) {
                handleExoPlayerMessage(msg);
            } else if (msg.what == MESSAGE_CCTV_WEBVIEW) {
                String tmp = (String)msg.obj;
                if (tmp != null && (tmp.contains("geumriver.go.kr") || tmp.contains("hrfco.go.kr"))) {
                    openWithCustomTabs(tmp);
                } else {
                    handleWebViewMessage(msg);
                }

            } else {
                handleErrorMessage();
            }
        }
    };

    private void openWithCustomTabs(String url) {
        try {

            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

            // 🔄 애니메이션 설정 (부드러운 전환)
            builder.setStartAnimations(this,
                    android.R.anim.slide_in_left,    // 왼쪽에서 슬라이드 인
                    android.R.anim.fade_out);        // 페이드 아웃

            builder.setExitAnimations(this,
                    android.R.anim.fade_in,          // 페이드 인
                    android.R.anim.slide_out_right); // 오른쪽으로 슬라이드 아웃

            CustomTabsIntent customTabsIntent = builder.build();

            // 🚀 Custom Tabs 실행
            customTabsIntent.launchUrl(this, Uri.parse(url));

            Log.i(TAG, "✅ Custom Tabs로 열기: " + url);

            // ⚠️ finish() 호출 안 함!
            // → Custom Tabs가 닫히면 자동으로 이 액티비티로 복귀

        } catch (Exception e) {
            e.printStackTrace();
            // Custom Tabs 실패 시 일반 브라우저로 폴백
            //openWithDefaultBrowser(url);
        }
    }

    private void handleExoPlayerMessage(Message msg) {
        try {
            updateCctvInfo();

            // Surface 재연결
            if (videoSurface != null) {
                exoPlayer.setVideoSurface(videoSurface);
            }

            // 미디어 아이템 설정 및 재생
            Uri videoUri = Uri.parse(mCctvItem.getCctvUrl());
            MediaItem mediaItem;

            if (videoUri.getLastPathSegment() != null &&
                    (videoUri.getLastPathSegment().contains(".m3u") ||
                            videoUri.getLastPathSegment().contains(".m3u8"))) {
                mediaItem = new MediaItem.Builder()
                        .setUri(videoUri)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build();
            } else {
                mediaItem = MediaItem.fromUri(videoUri);
            }

            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.play();

        } catch (Exception e) {
            e.printStackTrace();
            handleErrorMessage();
        }
    }

    private void handleWebViewMessage(Message msg) {
        try {
            updateCctvInfo();

            String streamUrl = (String) msg.obj;
            if (streamUrl != null) {
                Log.d(TAG, "🌐 WebView 재생: " + streamUrl);

                // 재로드 시 WebView 초기화
                if (!isFirstWebViewLoad) {
                    webView.clearCache(true);
                    webView.clearHistory();
                }
                isFirstWebViewLoad = false;

                // WebView로 스트림 재생
                webView.loadUrl(streamUrl);
                webView.setVisibility(View.VISIBLE);

            } else {
                handleErrorMessage();
            }

        } catch (Exception e) {
            e.printStackTrace();
            handleErrorMessage();
        }
    }

    private void updateCctvInfo() {
        // 아이콘 설정
        if ("ex".equals(mCctvItem.getRoadType())) {
            imgCctvType.setImageResource(R.drawable.cctvex32);
        } else if ("its".equals(mCctvItem.getRoadType())) {
            imgCctvType.setImageResource(R.drawable.cctvits32);
        } else {
            imgCctvType.setImageResource(R.drawable.cctvits32);
        }

        // 카메라 이름
        tvTitle.setText(mCctvItem.getCctvName());

        // 즐겨찾기 상태
        isFavor = Utils.existFavor(this, mCctvItem.getRoadType(), mCctvItem.getCctvName());
        setFavorImage(isFavor);

        // 저작권 정보
        tvCopyRight.setText(getString(R.string.copyright_land));

        // Navigator UI 업데이트
        updateNavigatorUi();
    }

    private void handleErrorMessage() {
        llProgress.setVisibility(GONE);
        // ✅ Toast 대신 화면 에러 표시
        showError("영상 로드 실패", "비디오를 불러올 수 없습니다");
    }

    /**
     * ✅ 에러 메시지 표시 (애니메이션)
     */
    private void showError(String message, String detail) {
        tvErrorMessage.setText(message);
        tvErrorDetail.setText(detail);

        llError.setAlpha(0f);
        llError.setVisibility(VISIBLE);
        llError.animate()
                .alpha(1f)
                .setDuration(300)
                .start();
    }

    /**
     * ✅ 에러 메시지 숨김 (애니메이션)
     */
    private void hideError() {
        if (llError.getVisibility() == VISIBLE) {
            llError.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> llError.setVisibility(GONE))
                    .start();
        }
    }

    private void hideProgressWithAnimation() {
        llProgress.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction(() -> llProgress.setVisibility(GONE));
        llProgress.setVisibility(GONE);
    }

    public void setFavorImage(boolean favor) {
        if (favor) {
            imgFavor.setImageResource(R.drawable.favor_on);
        } else {
            imgFavor.setImageResource(R.drawable.favor_off);
        }
    }

    private void toggleFavorite() {
        String msg;
        if (isFavor) {
            Utils.removeFavor(getApplicationContext(), mCctvItem.getRoadType(), mCctvItem.getCctvName());
            setFavorImage(false);
            msg = mCctvItem.getCctvName() + getString(R.string.msg_delete_favor);
        } else {
            Utils.addFavor(getApplicationContext(), mCctvItem);
            setFavorImage(true);
            msg = mCctvItem.getCctvName() + getString(R.string.msg_add_favor);
        }
        isFavor = !isFavor;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void navigateLeft() {
        if (navigator != null && navigator.canMoveLeft()) {
            mCctvItem = navigator.moveLeft();
            isFirstWebViewLoad = true; // 네비게이션 시 초기화
            updateCctvVideo();
        }
    }

    private void navigateRight() {
        if (navigator != null && navigator.canMoveRight()) {
            mCctvItem = navigator.moveRight();
            isFirstWebViewLoad = true; // 네비게이션 시 초기화
            updateCctvVideo();
        }
    }

    // ... 나머지 메서드들 (방향 전환, UI 업데이트 등) 동일하게 유지 ...

    @OptIn(markerClass = UnstableApi.class)
    private void applyVideoFit() {
        if (currentVideoWidth > 0 && currentVideoHeight > 0) {
            VideoSize videoSize = new VideoSize(currentVideoWidth, currentVideoHeight);
            fitVideoToView(videoSize);
        }
    }

    private void fitVideoToView(VideoSize videoSize) {
        int videoWidth = videoSize.width;
        int videoHeight = videoSize.height;
        int viewWidth = textureView.getWidth();
        int viewHeight = textureView.getHeight();

        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }

        boolean currentIsLandscape = (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE);

        Matrix matrix1 = new Matrix();
        float scaleX, scaleY;
        float cx = viewWidth / 2f;
        float cy = viewHeight / 2f;

        float videoAspect = (float) videoWidth / videoHeight;
        float viewAspect = (float) viewWidth / viewHeight;

        if (videoAspect > viewAspect) {
            scaleX = 1.0f;
            scaleY = viewAspect / videoAspect;
        } else {
            scaleX = videoAspect / viewAspect;
            scaleY = 1.0f;
        }
        matrix1.setScale(scaleX, scaleY, cx, cy);

        Point point = DeviceHelper.getDisplaySize(TestVideoActivity.this);
        int deviceWidth = point.x;
        int deviceHeight = point.y;

        float paddingRatio = ADHelper.getBottomPaddingRatio(TestVideoActivity.this);
        float padding = deviceHeight * paddingRatio * 0.5f;

        RectF srcViewRectF = new RectF(0, 0, deviceWidth, deviceHeight);
        RectF targetViewRectF = new RectF(0, 0, deviceWidth, deviceHeight - padding);

        Matrix matrix2 = new Matrix();
        if (currentIsLandscape) {
            matrix2.setRectToRect(srcViewRectF, targetViewRectF, Matrix.ScaleToFit.CENTER);
        }

        matrix.set(matrix1);
        matrix.postConcat(matrix2);

        textureView.setTransform(matrix);
    }

    private void setupOrientationListener() {
        orientationListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN) return;

                long currentTime = System.currentTimeMillis();
                if (currentTime - lockTimestamp < LOCK_DELAY_MS) {
                    return;
                }

                boolean deviceIsLandscape =
                        (orientation >= LANDSCAPE_MIN && orientation <= LANDSCAPE_MAX) ||
                                (orientation >= LANDSCAPE_MIN_REVERSE && orientation <= LANDSCAPE_MAX_REVERSE);

                boolean deviceIsPortrait =
                        (orientation >= 0 && orientation <= PORTRAIT_MAX) ||
                                (orientation >= PORTRAIT_MIN && orientation <= 360) ||
                                (orientation >= PORTRAIT_MIN_REVERSE && orientation <= PORTRAIT_MAX_REVERSE);

                if (isLockedLandscape && deviceIsLandscape) {
                    runOnUiThread(() -> {
                        Log.d(TAG, "Unlocking landscape mode - returning to sensor");
                        isLockedLandscape = false;
                        if (isAutoRotationEnabled()) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        }
                    });
                } else if (isLockedPortrait && deviceIsPortrait) {
                    runOnUiThread(() -> {
                        Log.d(TAG, "Unlocking portrait mode - returning to sensor");
                        isLockedPortrait = false;
                        if (isAutoRotationEnabled()) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        }
                    });
                }
            }
        };

        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable();
        }
    }

    private void setupRotationObserver() {
        rotationObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                boolean autoRotateEnabled = isAutoRotationEnabled();
                Log.d(TAG, "Auto rotation changed: " + autoRotateEnabled);

                if (!autoRotateEnabled && !isLockedLandscape && !isLockedPortrait) {
                    int currentOrientation = getResources().getConfiguration().orientation;
                    if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                } else if (autoRotateEnabled && !isLockedLandscape && !isLockedPortrait) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                }
            }
        };

        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                true,
                rotationObserver
        );
    }

    private boolean isAutoRotationEnabled() {
        try {
            return Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION
            ) == 1;
        } catch (Settings.SettingNotFoundException e) {
            return true;
        }
    }

    private void toggleOrientation() {
        if (isLandscape) {
            goToPortrait();
        } else {
            goToLandscape();
        }
    }

    private void goToLandscape() {
        isLandscape = true;
        isLockedLandscape = true;
        isLockedPortrait = false;

        lockTimestamp = System.currentTimeMillis();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        applyBarsByOrientation(Configuration.ORIENTATION_LANDSCAPE);
        updateButtonIcon();

        Log.d(TAG, "Locked to landscape mode");
    }

    private void goToPortrait() {
        isLandscape = false;
        isLockedLandscape = false;
        isLockedPortrait = true;

        lockTimestamp = System.currentTimeMillis();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        applyBarsByOrientation(Configuration.ORIENTATION_PORTRAIT);
        updateButtonIcon();

        Log.d(TAG, "Locked to portrait mode");
    }

    private void updateButtonIcon() {
        if (isLandscape) {
            imgScreen.setImageResource(R.drawable.full_screen_off);
        } else {
            imgScreen.setImageResource(R.drawable.full_screen_on);
        }
    }

    private void applyBarsByOrientation(int orientation) {
        WindowInsetsControllerCompat controller =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());

        if (controller == null) return;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            controller.hide(WindowInsetsCompat.Type.statusBars());
            controller.show(WindowInsetsCompat.Type.navigationBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            controller.show(WindowInsetsCompat.Type.systemBars());

            int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean isLightMode = (nightMode != Configuration.UI_MODE_NIGHT_YES);
            controller.setAppearanceLightStatusBars(isLightMode);
        }
    }

    @Override
    public void onBackPressed() {

        Log.d("ttt", "onBackPressed called");

        // 리소스 해제
        releaseAllResources();


        if (BuildConfig.DEBUG) {

        } else {
            ADHelper.displayInterstitial(this);
        }

        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // WebView 사용 중일 때
        if (currentPlayerType == PlayerType.WEBVIEW && webView != null) {
            webView.onResume();
            // 화면 복귀 시 스크립트 재적용
            webView.postDelayed(() -> injectAllScripts(webView), 300);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // WebView 사용 중일 때
        if (currentPlayerType == PlayerType.WEBVIEW && webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        // 리소스 해제
        releaseAllResources();
    }

    /**
     * 모든 리소스 해제 (중복 호출 안전)
     */
    private void releaseAllResources() {
        // Handler 정리
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }

        // ✅ WebView 타임아웃 정리
        cancelWebViewTimeout();

        // ExoPlayer 해제
        releaseExoPlayer();

        // WebView 해제
        releaseWebView();

        // Surface 해제
        releaseSurface();

        // Observer 해제
        if (rotationObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(rotationObserver);
                rotationObserver = null;
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering rotation observer", e);
            }
        }

        // OrientationListener 해제
        if (orientationListener != null) {
            try {
                orientationListener.disable();
                orientationListener = null;
            } catch (Exception e) {
                Log.e(TAG, "Error disabling orientation listener", e);
            }
        }
    }

    /**
     * ExoPlayer 해제
     */
    private void releaseExoPlayer() {
        if (exoPlayer != null) {
            try {
                Log.d(TAG, "Releasing ExoPlayer");
                exoPlayer.setPlayWhenReady(false);
                exoPlayer.stop();
                exoPlayer.clearVideoSurface();
                exoPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing ExoPlayer", e);
            } finally {
                exoPlayer = null;
            }
        }
    }

    /**
     * WebView 해제
     */
    private void releaseWebView() {
        if (webView != null) {
            try {
                Log.d(TAG, "Releasing WebView");

                // 비디오 재생 중지
                webView.onPause();

                // MediaCodec 즉시 해제
                webView.loadUrl("about:blank");
                webView.stopLoading();

                // 캐시 및 히스토리 정리
                webView.clearHistory();
                webView.clearCache(true);

                // WebView 완전 제거
                webView.removeAllViews();
                webView.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing WebView", e);
            } finally {
                webView = null;
            }
        }
    }

    /**
     * Surface 해제
     */
    private void releaseSurface() {
        if (videoSurface != null) {
            try {
                Log.d(TAG, "Releasing Surface");
                videoSurface.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing surface", e);
            } finally {
                videoSurface = null;
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        ADHelper.updateAdVisibilityForDeviceConfiguration(this);

        if (!isLockedLandscape && !isLockedPortrait) {
            isLandscape = (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
        }

        applyBarsByOrientation(newConfig.orientation);
        updateButtonIcon();

        // ExoPlayer 사용 중일 때만 비디오 핏 적용
        if (currentPlayerType == PlayerType.EXOPLAYER) {
            textureView.post(this::applyVideoFit);
        }
    }

    private void updateNavigatorUi() {
        if (navigator == null) return;

        CctvItemVo current = navigator.getCurrent();
        if (current == null) return;

        CctvItemVo left = navigator.getLeft();
        if (left != null) {
            String leftCctvName = Utils.getStringWithoutBigBrackets(left.cctvName);
            btnLeftLabel.setText(leftCctvName);
            double angle = calculateAngle(current, left);
            btnLeftIcon.setImageResource(getDirectionIcon(angle));
            layoutLeft.setEnabled(true);
        } else {
            btnLeftLabel.setText("없음");
            btnLeftIcon.setImageResource(R.drawable.ic_arrow_disabled);
            layoutLeft.setEnabled(false);
        }

        CctvItemVo right = navigator.getRight();
        if (right != null) {
            String rightCctvName = Utils.getStringWithoutBigBrackets(right.cctvName);
            btnRightLabel.setText(rightCctvName);
            double angle = calculateAngle(current, right);
            btnRightIcon.setImageResource(getDirectionIcon(angle));
            layoutRight.setEnabled(true);
        } else {
            btnRightLabel.setText("없음");
            btnRightIcon.setImageResource(R.drawable.ic_arrow_disabled);
            layoutRight.setEnabled(false);
        }
    }

    public double calculateAngle(CctvItemVo from, CctvItemVo to) {
        double dx = to.coordX - from.coordX;
        double dy = to.coordY - from.coordY;
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        return (angle + 360) % 360;
    }

    public @DrawableRes int getDirectionIcon(double angle) {
        if (angle >= 337.5 || angle < 22.5) return R.drawable.ic_arrow_right;
        else if (angle < 67.5) return R.drawable.ic_arrow_up_right;
        else if (angle < 112.5) return R.drawable.ic_arrow_up;
        else if (angle < 157.5) return R.drawable.ic_arrow_up_left;
        else if (angle < 202.5) return R.drawable.ic_arrow_left;
        else if (angle < 247.5) return R.drawable.ic_arrow_down_left;
        else if (angle < 292.5) return R.drawable.ic_arrow_down;
        else return R.drawable.ic_arrow_down_right;
    }

    private static int dpToPx(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }


}
