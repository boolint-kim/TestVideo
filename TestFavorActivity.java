public class TestFavorActivity extends AppCompatActivity {

    private static final String TAG = "TestFavorActivity";
    private static final int MESSAGE_CCTV_EXOPLAYER = 101;
    private static final int MESSAGE_CCTV_WEBVIEW = 102;

    // Player 타입 정의
    private enum PlayerType {
        EXOPLAYER,
        WEBVIEW
    }

    private PlayerType currentPlayerType = PlayerType.EXOPLAYER;
    private boolean isFirstWebViewLoad = true;

    // Views
    private FrameLayout frameVideoContainer;
    private LinearLayout llVideo;
    private PlayerView playerView;
    private WebView webView;
    private ExoPlayer exoPlayer;

    private ListView lvFavor;
    private LinearLayout llRegFavor;
    private TextView tvCopyRight;
    private LinearLayout llProgress;
    private TextView tvSelectItem;
    private ImageView ivHome;
    private TextView tvTitle;

    // Data
    private ArrayList<FavorItemVo> favorList;
    private CctvApiHelper apiHelper;
    private MediaItem mediaItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_test_favor);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        DeviceHelper.setOrientationPhoneToPortrait(this);

        ADHelper.settingAdEx(this);
        ADHelper.loadAdMobInterstitialAd(this);

        apiHelper = new CctvApiHelper();

        initializeViews();
        setupExoPlayer();
        setupWebView();
        setupListView();
    }

    private void initializeViews() {
        llVideo = findViewById(R.id.llVideo);
        llRegFavor = findViewById(R.id.llRegFavor);
        tvCopyRight = findViewById(R.id.tvCopyRight);
//        frameVideoContainer = findViewById(R.id.frame_video_container); // FrameLayout
        playerView = findViewById(R.id.player_view);
        webView = findViewById(R.id.webview);
        lvFavor = findViewById(R.id.lvFavor);
        llProgress = findViewById(R.id.ll_progress);
        tvSelectItem = findViewById(R.id.tv_select_item);
        tvTitle = findViewById(R.id.tv_title);
        ivHome = findViewById(R.id.iv_home);

        tvTitle.setText(getString(R.string.title_favorite));
        ivHome.setOnClickListener(v -> onBackPressed());

        lvFavor.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        // 초기 상태 설정
        playerView.setVisibility(View.VISIBLE);
        webView.setVisibility(GONE);
    }

    private void setupExoPlayer() {
        exoPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    hideProgress();
                }
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    hideProgress();
                } else if (state == Player.STATE_BUFFERING) {
                    showProgress();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                hideProgress();
                Toast.makeText(TestFavorActivity.this, "CCTV connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings ws = webView.getSettings();

        // JavaScript & DOM
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);

        // 캐시 & 버퍼링 최적화
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setDatabaseEnabled(true);

        // 하드웨어 가속
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 렌더링 우선순위
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

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public View getVideoLoadingProgressView() {
                return new View(TestFavorActivity.this);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                // Fullscreen support
            }

            @Override
            public void onHideCustomView() {
                // Exit fullscreen
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress > 10) {
                    injectAllScripts(view);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                injectBaseCSSImmediately(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectAllScripts(view);
                view.postDelayed(() -> injectAllScripts(view), 500);
                view.postDelayed(() -> {
                    injectAllScripts(view);
                    hideProgress();
                }, 1000);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    hideProgress();
                    Toast.makeText(TestFavorActivity.this, "WebView loading error", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * 페이지 시작과 동시에 기본 CSS 주입
     */
    private void injectBaseCSSImmediately(WebView view) {
        String js =
                "javascript:(function(){ " +
                        "if(!document.getElementById('cctv-base-style')){ " +
                        "var s=document.createElement('style');" +
                        "s.id='cctv-base-style';" +
                        "s.innerHTML=`" +
                        "body{margin:0;padding:0;background:#000!important;overflow:hidden!important;}" +
                        "video{" +
                        "width:100vw!important;" +
                        "height:100vh!important;" +
                        "object-fit:contain!important;" +
                        "background:black!important;" +
                        "position:fixed!important;" +
                        "top:50%!important;left:50%!important;" +
                        "transform:translate(-50%,-50%)!important;" +
                        "pointer-events:none!important;" +
                        "}" +
                        "video::-webkit-media-controls{display:none!important;}" +
                        "video::-webkit-media-controls-panel{display:none!important;}" +
                        "video::-webkit-media-controls-play-button{display:none!important;}" +
                        "video::-webkit-media-controls-start-playback-button{display:none!important;}" +
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
                        // CSS
                        "if(!document.getElementById('cctv-base-style')){ " +
                        "var s=document.createElement('style');" +
                        "s.id='cctv-base-style';" +
                        "s.innerHTML=`" +
                        "body{margin:0;padding:0;background:#000!important;overflow:hidden!important;}" +
                        "video{" +
                        "width:100vw!important;" +
                        "height:100vh!important;" +
                        "object-fit:contain!important;" +
                        "background:black!important;" +
                        "position:fixed!important;" +
                        "top:50%!important;left:50%!important;" +
                        "transform:translate(-50%,-50%)!important;" +
                        "pointer-events:none!important;" +
                        "}" +
                        "video::-webkit-media-controls{display:none!important;}" +
                        "video::-webkit-media-controls-panel{display:none!important;}" +
                        "video::-webkit-media-controls-play-button{display:none!important;}" +
                        "video::-webkit-media-controls-start-playback-button{display:none!important;}" +
                        "`;" +
                        "document.head.appendChild(s);" +
                        "}" +

                        // Video 설정
                        "document.querySelectorAll('video').forEach(function(v){ " +
                        "v.removeAttribute('controls');" +
                        "v.removeAttribute('poster');" +
                        "v.poster='';" +
                        "v.autoplay=true;" +
                        "v.muted=true;" +
                        "v.playsInline=true;" +
                        "v.webkitPlaysInline=true;" +
                        "v.style.pointerEvents='none';" +

                        // 버퍼링 최적화
                        "v.preload='auto';" +
                        "v.setAttribute('preload', 'auto');" +

                        // 끊김 대응 이벤트
                        "if(!v.hasBufferingListeners){" +
                        "v.hasBufferingListeners=true;" +

                        "v.addEventListener('stalled', function(){" +
                        "setTimeout(function(){if(v.paused)v.play().catch(function(){});}, 1000);" +
                        "});" +

                        "v.addEventListener('suspend', function(){" +
                        "if(v.paused)v.play().catch(function(){});" +
                        "});" +

                        "}" +

                        // 재생
                        "if(v.paused && v.readyState >= 2){" +
                        "v.play().catch(function(){});" +
                        "}" +
                        "});" +
                        "})();";

        view.evaluateJavascript(js, null);
    }

    private void setupListView() {
        favorList = Utils.readItemsFromFile(this);

        for (FavorItemVo vo : favorList) {
            Log.d(TAG, "FavorList: " + vo.getName() + " / " + vo.getType() + " / " + vo.getAddress());
        }

        if (favorList.size() == 0) {
            llVideo.setVisibility(GONE);
            lvFavor.setVisibility(GONE);
            llRegFavor.setVisibility(View.VISIBLE);
        } else {
            llRegFavor.setVisibility(GONE);
        }

        lvFavor.setAdapter(new FavorAdapter(TestFavorActivity.this, 0, favorList));

        lvFavor.setOnItemClickListener((parent, view, position, id) -> {
            FavorItemVo favorItemVo = favorList.get(position);
            if (favorItemVo == null || MainData.mCctvList == null) {
                Toast.makeText(getApplicationContext(), getString(R.string.msg_error_null), Toast.LENGTH_SHORT).show();
                return;
            }

            tvSelectItem.setVisibility(GONE);

            for (CctvItemVo vo : MainData.mCctvList) {
                if (favorItemVo.getType().equals(vo.roadType) &&
                        favorItemVo.getName().equals(vo.cctvName)) {
                    MainData.mCurrentCctvItemVo = vo;
                    loadCctvVideo(vo);
                    break;
                }
            }
        });

        // 첫 번째 아이템 자동 선택 및 재생
        if (favorList.size() > 0) {
            lvFavor.post(() -> {
                lvFavor.setItemChecked(0, true);
                View firstItem = lvFavor.getChildAt(0);
                if (firstItem != null) {
                    lvFavor.performItemClick(firstItem, 0, lvFavor.getAdapter().getItemId(0));
                }
            });
        }
    }

    private void loadCctvVideo(CctvItemVo cctvItem) {
        // 플레이어 타입 결정
        PlayerType newPlayerType = "utic".equals(cctvItem.getRoadType())
                ? PlayerType.WEBVIEW
                : PlayerType.EXOPLAYER;

        // 이전 플레이어 정리
        stopCurrentPlayer();

        // 새 플레이어 타입 설정
        currentPlayerType = newPlayerType;
        switchPlayerVisibility(currentPlayerType);

        // 프로그레스 표시
        showProgress();

        // CCTV 타입별 비디오 로드
        loadCctvVideoByType(cctvItem);
    }

    private void stopCurrentPlayer() {
        // ExoPlayer 정리
        if (exoPlayer != null) {
            exoPlayer.stop();
        }

        // WebView 정리
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");

            // 재로드 시 WebView 초기화
            if (!isFirstWebViewLoad && currentPlayerType == PlayerType.WEBVIEW) {
                webView.clearCache(true);
                webView.clearHistory();
            }
        }
    }

    private void switchPlayerVisibility(PlayerType playerType) {
        if (playerType == PlayerType.WEBVIEW) {
            playerView.setVisibility(GONE);
            webView.setVisibility(View.VISIBLE);
        } else {
            playerView.setVisibility(View.VISIBLE);
            webView.setVisibility(GONE);
        }
    }

    private void loadCctvVideoByType(CctvItemVo cctvItem) {
        String roadType = cctvItem.getRoadType();

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
                MainData.mCurrentCctvItemVo.cctvUrl = SeoulCctvVideoOpenApiHelper.getSeoulCctvUrl(
                        MainData.mCurrentCctvItemVo.roadSectionId);

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
                Log.d(TAG, "roadSectionId: " + MainData.mCurrentCctvItemVo.roadSectionId);
                String url1 = JejuCctvVideoOpenApiHelper.getCctvInfoAndSetCookie(
                        MainData.mCurrentCctvItemVo.roadSectionId);
                MainData.mCurrentCctvItemVo.cctvUrl = JejuCctvVideoOpenApiHelper.getCctvStreamUrl(url1);

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
                String tempUrl = GgCctvVideoOpenApiHelper.getUrl1(
                        MainData.mCurrentCctvItemVo.roadSectionId);
                MainData.mCurrentCctvItemVo.cctvUrl = GgCctvVideoOpenApiHelper.getUrl2(tempUrl);

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
                MainData.mCurrentCctvItemVo.cctvUrl = DaeguCctvVideoOpenApiHelper.getUrl(
                        MainData.mCurrentCctvItemVo.roadSectionId);

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
        Log.d(TAG, "🚀 UTIC CCTV 로드: " + MainData.mCurrentCctvItemVo.roadSectionId);

        apiHelper.getCctvInfo(MainData.mCurrentCctvItemVo.roadSectionId,
                new CctvApiHelper.CctvResponseListener() {
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
                        runOnUiThread(() -> sendErrorMessage());
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
                handleExoPlayerMessage();
            } else if (msg.what == MESSAGE_CCTV_WEBVIEW) {
                handleWebViewMessage(msg);
            } else {
                handleErrorMessage();
            }
        }
    };

    private void handleExoPlayerMessage() {
        try {
            tvCopyRight.setText(getString(R.string.copyright_land));

            // URI 파싱 및 재생
            Uri videoUri = Uri.parse(MainData.mCurrentCctvItemVo.cctvUrl);

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
            tvCopyRight.setText(getString(R.string.copyright_land));

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

    private void handleErrorMessage() {
        hideProgress();
        Toast.makeText(this, "Failed to load video", Toast.LENGTH_SHORT).show();
    }

    private void showProgress() {
        if (llProgress != null) {
            llProgress.setVisibility(View.VISIBLE);
        }
    }

    private void hideProgress() {
        if (llProgress != null) {
            llProgress.setVisibility(GONE);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        DeviceHelper.setOrientationPhoneToPortrait(this);
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
    public void onBackPressed() {
        Log.d("ttt", "onBackPressed called");

        Utils.writeItemToFile(getApplicationContext(), favorList);

        // 리소스 해제
        releaseAllResources();

        if (BuildConfig.DEBUG) {
            // Debug mode
        } else {
            ADHelper.displayInterstitial(this);
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

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

        // ExoPlayer 해제
        releaseExoPlayer();

        // WebView 해제
        releaseWebView();
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
                exoPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing ExoPlayer", e);
            } finally {
                exoPlayer = null;
                playerView = null;
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

    // ============================================================================
    // FavorAdapter
    // ============================================================================

    private class FavorAdapter extends ArrayAdapter<FavorItemVo> {

        private ArrayList<FavorItemVo> items;

        public FavorAdapter(@NonNull Context context, int resource, ArrayList<FavorItemVo> objects) {
            super(context, resource, objects);
            items = objects;
        }

        @NonNull
        @Override
        public View getView(final int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.items_favor, null);
            }
            final FavorItemVo vo = items.get(position);

            // 이미지
            ImageView imgType = v.findViewById(R.id.imgType);

            // 위치 이름
            TextView tvName = v.findViewById(R.id.tv_name);
            tvName.setText(vo.getName());

            // 타입 고속도로, 국도, 도로
            TextView tvType = v.findViewById(R.id.tv_Type);
            if ("ex".equals(vo.getType())) {
                imgType.setImageResource(R.drawable.cctvex32);
                tvType.setText("고속도로");
            } else if ("its".equals(vo.getType())) {
                imgType.setImageResource(R.drawable.cctvits32);
                tvType.setText("국도");
            } else {
                imgType.setImageResource(R.drawable.cctvits32);
                tvType.setText("도로");
            }

            // 삭제 버튼
            LinearLayout llDel = v.findViewById(R.id.llDel);
            llDel.setOnClickListener(view -> {
                String msg = favorList.get(position).getName() + getString(R.string.msg_delete_favor);
                Toast.makeText(TestFavorActivity.this, msg, Toast.LENGTH_SHORT).show();
                favorList.remove(position);

                FavorAdapter adapter = (FavorAdapter) lvFavor.getAdapter();
                adapter.notifyDataSetChanged();

                if (favorList.size() == 0) {
                    // 플레이어 정지
                    stopCurrentPlayer();

                    llVideo.setVisibility(GONE);
                    lvFavor.setVisibility(GONE);
                    llRegFavor.setVisibility(View.VISIBLE);
                }
            });

            // 전체화면 버튼
            LinearLayout llFullScreen = v.findViewById(R.id.ll_full_screen);
            llFullScreen.setOnClickListener(view -> {
                // 전체화면 기능 구현 (필요시)
                if (MainData.mCurrentCctvItemVo != null) {
                    Intent intent = new Intent(TestFavorActivity.this, TestVideoActivity.class);
                    startActivity(intent);
                }
            });

            return v;
        }
    }
}
