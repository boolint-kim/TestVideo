public class TestSearchGridActivity extends AppCompatActivity {

    private static final String TAG = "TestSearchGridActivity";

    private LinearLayout llProgress;
    private ZoomGridView grGridView;

    private ArrayList<CctvItemVo> mList = new ArrayList<>();
    private ArrayList<CctvItemVo> searchList = new ArrayList<>();

    private int mThreadCount = -1;
    private int videoQty = 6;

    private ImageView ivHome;
    private TextView tvTitle;
    private CctvApiHelper apiHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search_grid);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        DeviceHelper.setOrientationPhoneToPortrait(this);

        apiHelper = new CctvApiHelper();

        initializeViews();
        setupGridView();
        loadNearbyCctvList();
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tv_title);
        ivHome = findViewById(R.id.iv_home);
        llProgress = findViewById(R.id.ll_progress);
        grGridView = findViewById(R.id.gr_grid_view);

        tvTitle.setText(getString(R.string.ab_cctv_nearby_title));
        ivHome.setOnClickListener(v -> onBackPressed());

        llProgress.setVisibility(View.VISIBLE);

        ADHelper.settingAdEx(this);
        ADHelper.loadAdMobInterstitialAd(this);
    }

    private void setupGridView() {
        int numColumns = getGridColumnCount();
        grGridView.setNumColumns(numColumns);
    }

    private void loadNearbyCctvList() {
        // Busan 제외하고 리스트 생성
        for (CctvItemVo vo : MainData.mCctvList) {
            if ("busan".equals(vo.getRoadType())) continue;
            mList.add(vo);
        }

        // 거리 계산
        double x = MainData.mX;
        double y = MainData.mY;

        for (CctvItemVo vo : mList) {
            vo.distance = Math.sqrt((x - vo.coordX) * (x - vo.coordX) +
                    (y - vo.coordY) * (y - vo.coordY));
        }

        // 거리순 정렬
        Collections.sort(mList, (s, t1) -> {
            if (s.distance > t1.distance) return 1;
            else if (s.distance < t1.distance) return -1;
            else return 0;
        });

        // 가까운 순서로 선택 (중복 거리 제외)
        boolean stopPlaying = false;
        int loopCount = Math.min(videoQty, mList.size());
        Double lastDistance = null;

        for (int i = 0; i < mList.size(); i++) {
            if (searchList.size() >= loopCount) {
                break;
            }

            String roadType = mList.get(i).getRoadType();
            if ("jeju".equals(roadType)) {
                stopPlaying = true;
                break;
            }

            double currentDistance = mList.get(i).distance;
            Log.d(TAG, "Distance: " + currentDistance + " - " + mList.get(i).getCctvName());

            // 마지막 distance와 거의 같으면 skip
            if (lastDistance != null && Math.abs(lastDistance - currentDistance) < 0.00001) {
                Log.d(TAG, "Skip duplicate distance: " + mList.get(i).getCctvName());
                continue;
            }

            searchList.add(mList.get(i));
            lastDistance = currentDistance;
        }

        // 제주를 제외하고 실행
        if (stopPlaying) {
            llProgress.setVisibility(View.GONE);
            showJejuWarningDialog();
        } else {
            loadAllCctvVideos();
        }
    }

    private void showJejuWarningDialog() {
        androidx.appcompat.app.AlertDialog.Builder dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this);
        dialog.setTitle("알림");
        dialog.setMessage(getString(R.string.msg_can_not_play_video));
        dialog.setNeutralButton(getString(R.string.msg_close), (dialogInterface, which) -> finish());
        dialog.show();
    }

    private void loadAllCctvVideos() {
        mThreadCount = searchList.size();

        for (CctvItemVo vo : searchList) {
            if (vo != null) {
                loadCctvVideoByType(vo);
            }
        }
    }

    private void loadCctvVideoByType(CctvItemVo vo) {
        String roadType = vo.getRoadType();

        switch (roadType) {
            case "seoul":
                startSeoulCctvVideo(vo);
                break;
            case "jeju":
                startJejuCctvVideo(vo);
                break;
            case "gg":
                startGgCctvVideo(vo);
                break;
            case "daegu":
                startDaeguCctvVideo(vo);
                break;
            case "utic":
                startUticCctvVideoWithApi(vo);
                break;
            default:
                startCctvVideo(vo);
                break;
        }
    }

    private void startCctvVideo(CctvItemVo vo) {
        new Thread(() -> {
            try {
                sendSuccessMessage();
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startSeoulCctvVideo(CctvItemVo vo) {
        new Thread(() -> {
            try {
                vo.cctvUrl = SeoulCctvVideoOpenApiHelper.getSeoulCctvUrl(vo.roadSectionId);
                sendSuccessMessage();
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startJejuCctvVideo(CctvItemVo vo) {
        new Thread(() -> {
            try {
                // 여러개를 동시에 요청하면 쿠키가 엉키므로 SearchGrid에서는 사용하지 않음
                sendSuccessMessage();
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startGgCctvVideo(CctvItemVo vo) {
        new Thread(() -> {
            try {
                String tempUrl = GgCctvVideoOpenApiHelper.getUrl1(vo.roadSectionId);
                vo.cctvUrl = GgCctvVideoOpenApiHelper.getUrl2(tempUrl);
                sendSuccessMessage();
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startDaeguCctvVideo(CctvItemVo vo) {
        new Thread(() -> {
            try {
                vo.cctvUrl = DaeguCctvVideoOpenApiHelper.getUrl(vo.roadSectionId);
                sendSuccessMessage();
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startUticCctvVideoWithApi(CctvItemVo vo) {
        Log.d(TAG, "🚀 UTIC CCTV 로드: " + vo.roadSectionId);

        apiHelper.getCctvInfo(vo.roadSectionId, new CctvApiHelper.CctvResponseListener() {
            @Override
            public void onSuccess(CctvApiHelper.CctvInfo cctvInfo) {
                Log.d(TAG, "✅ CCTV 정보 받음: " + cctvInfo.toString());
                vo.cctvUrl = cctvInfo.getStreamPageUrl();
                vo.isWebViewPlayer = true; // WebView 플레이어 플래그
                sendSuccessMessage();
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "❌ CCTV 정보 로드 실패: " + error);
                sendErrorMessage();
            }
        });
    }

    private void sendSuccessMessage() {
        Message msg = handler.obtainMessage();
        msg.what = 100;
        handler.sendMessage(msg);
    }

    private void sendErrorMessage() {
        Message msg = handler.obtainMessage();
        msg.what = -100;
        handler.sendMessage(msg);
    }

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 100 || msg.what == -100) {
                mThreadCount--;
            }

            if (mThreadCount == 0) {
                grGridView.setAdapter(new SearchAdapter(
                        TestSearchGridActivity.this, 0, searchList));
                llProgress.setVisibility(View.GONE);
            }
        }
    };

    private int getGridColumnCount() {
        boolean isTablet = DeviceHelper.isTabletDevice(this);
        boolean isLandscape = DeviceHelper.isLandscapeOrientation(this);

        if (isTablet && isLandscape) {
            return 3;  // 태블릿 가로모드
        } else {
            return 2;  // 휴대폰 또는 태블릿 세로모드
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        DeviceHelper.setOrientationPhoneToPortrait(this);

        int numColumns = getGridColumnCount();
        grGridView.setNumColumns(numColumns);
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed called");

        // 리소스 해제
        releaseAllResources();

        // 리스트 정리
        searchList.clear();
        mList.clear();

        // 광고 표시
        if (BuildConfig.DEBUG) {

        } else {
            ADHelper.displayInterstitial(this);
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

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

        // GridView의 모든 WebView/VideoView 해제
        releaseGridViewResources();
    }

    /**
     * GridView의 모든 비디오 리소스 해제
     */
    private void releaseGridViewResources() {
        if (grGridView == null) return;

        try {
            Log.d(TAG, "Releasing GridView resources");

            int childCount = grGridView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View itemView = grGridView.getChildAt(i);
                if (itemView == null) continue;

                // VideoView 해제
                VideoView videoView = itemView.findViewById(R.id.vv_cctv);
                if (videoView != null) {
                    videoView.stopPlayback();
                    videoView.suspend();
                }

                // WebView 해제
                WebView webView = itemView.findViewById(R.id.webview);
                if (webView != null) {
                    webView.onPause();
                    webView.loadUrl("about:blank");
                    webView.stopLoading();
                    webView.clearHistory();
                    webView.clearCache(true);
                    webView.removeAllViews();
                    webView.destroy();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing GridView resources", e);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
        }

        return super.onOptionsItemSelected(item);
    }

    // ============================================================================
    // SearchAdapter
    // ============================================================================

    private class SearchAdapter extends ArrayAdapter<CctvItemVo> {

        public ArrayList<CctvItemVo> items;

        public SearchAdapter(Context context, int textViewResourceId, ArrayList<CctvItemVo> objects) {
            super(context, textViewResourceId, objects);
            this.items = objects;
        }

        @Override
        public CctvItemVo getItem(int position) {
            try {
                return items.get(position);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.items_search_grid, parent, false);

                holder = new ViewHolder();
                holder.llBase = convertView.findViewById(R.id.ll_base);
                holder.frameVideoContainer = convertView.findViewById(R.id.frame_video_container);
                holder.vvCctv = convertView.findViewById(R.id.vv_cctv);
                holder.webView = convertView.findViewById(R.id.webview);
                holder.tvTitle = convertView.findViewById(R.id.tvTitle);
                holder.imgCctvType = convertView.findViewById(R.id.imgCctvType);
                holder.llProgress = convertView.findViewById(R.id.re_progress);
                holder.llVideoError = convertView.findViewById(R.id.re_video_error);
//                holder.llFullScreen = convertView.findViewById(R.id.llFullScreen);
//                holder.imgFullScreen = convertView.findViewById(R.id.imgFullScreen);
//                holder.llMap = convertView.findViewById(R.id.llMap);
//                holder.imgMap = convertView.findViewById(R.id.imgMap);
                holder.llFavor = convertView.findViewById(R.id.llFavor);
                holder.imgFavor = convertView.findViewById(R.id.imgFavor);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
                cleanupViewHolder(holder);
            }

            CctvItemVo vo = items.get(position);

            // ✅ 비디오 컨테이너는 터치를 소비하지 않음
            holder.frameVideoContainer.setClickable(false);
            holder.frameVideoContainer.setFocusable(false);
            holder.frameVideoContainer.setLongClickable(false);
            holder.frameVideoContainer.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return false; // 부모로 전달
                }
            });

            holder.llProgress.setVisibility(View.VISIBLE);
            holder.llVideoError.setVisibility(View.GONE);

            // CCTV 타입 아이콘
            if ("ex".equals(vo.getRoadType())) {
                holder.imgCctvType.setImageResource(R.drawable.cctvex32);
            } else if ("its".equals(vo.getRoadType())) {
                holder.imgCctvType.setImageResource(R.drawable.cctvits32);
            } else {
                holder.imgCctvType.setImageResource(R.drawable.cctvits32);
            }

            holder.tvTitle.setText(vo.cctvName);

            // 즐겨찾기 상태
            boolean isFavor = Utils.existFavor(TestSearchGridActivity.this, vo.getRoadType(), vo.getCctvName());
            holder.imgFavor.setImageResource(isFavor ? R.drawable.favor_on : R.drawable.favor_off);

            // ============================================================================
            // 버튼 클릭 이벤트
            // ============================================================================

            holder.llFavor.setOnClickListener(v -> {
                boolean currentFavor = Utils.existFavor(TestSearchGridActivity.this, vo.getRoadType(), vo.getCctvName());
                String msg;

                if (currentFavor) {
                    Utils.removeFavor(getApplicationContext(), vo.getRoadType(), vo.getCctvName());
                    holder.imgFavor.setImageResource(R.drawable.favor_off);
                    msg = vo.getCctvName() + getString(R.string.msg_delete_favor);
                } else {
                    Utils.addFavor(getApplicationContext(), vo);
                    holder.imgFavor.setImageResource(R.drawable.favor_on);
                    msg = vo.getCctvName() + getString(R.string.msg_add_favor);
                }

                Toast.makeText(TestSearchGridActivity.this, msg, Toast.LENGTH_SHORT).show();
            });

//            holder.llFullScreen.setOnClickListener(v -> {
//                MainData.mCurrentCctvItemVo = vo;
//                Intent intent = new Intent(TestSearchGridActivity.this, TestVideoActivity.class);
//                startActivity(intent);
//            });
//
//            holder.llMap.setOnClickListener(v -> {
//                MainData.mCurrentCctvItemVo = vo;
//                Intent intent = new Intent(TestSearchGridActivity.this, MapActivity.class);
//                intent.putExtra("latitude", vo.coordY);
//                intent.putExtra("longitude", vo.coordX);
//                intent.putExtra("cctvName", vo.cctvName);
//                startActivity(intent);
//            });

            // 플레이어 설정
            boolean useWebView = "utic".equals(vo.getRoadType()) || vo.isWebViewPlayer;
            if (useWebView) {
                setupWebViewPlayer(holder, vo);
            } else {
                setupVideoViewPlayer(holder, vo);
            }

            return convertView;
        }

        /**
         * ViewHolder의 이전 비디오 리소스 정리
         */
        private void cleanupViewHolder(ViewHolder holder) {
            try {
                // VideoView 정리
                if (holder.vvCctv != null) {
                    holder.vvCctv.stopPlayback();
                    holder.vvCctv.suspend();
                }

                // WebView 정리
                if (holder.webView != null) {
                    holder.webView.onPause();
                    holder.webView.loadUrl("about:blank");
                    holder.webView.stopLoading();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up ViewHolder", e);
            }
        }

        private void setupVideoViewPlayer(ViewHolder holder, CctvItemVo vo) {
            holder.vvCctv.setVisibility(View.VISIBLE);
            holder.webView.setVisibility(View.GONE);

            // ✅ VideoView도 터치를 소비하지 않도록
            holder.vvCctv.setClickable(false);
            holder.vvCctv.setFocusable(false);
            holder.vvCctv.setFocusableInTouchMode(false);
            holder.vvCctv.setLongClickable(false);

            holder.vvCctv.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return false; // 부모로 터치 전달
                }
            });

            holder.vvCctv.setOnPreparedListener(mp -> {
                holder.llProgress.setVisibility(View.GONE);
            });

            holder.vvCctv.setOnErrorListener((mp, what, extra) -> {
                holder.llProgress.setVisibility(View.GONE);
                holder.llVideoError.setVisibility(View.VISIBLE);
                return true;
            });

            holder.vvCctv.setMediaController(null);
            holder.vvCctv.setVideoURI(Uri.parse(vo.getCctvUrl()));
            holder.vvCctv.requestFocus();
            holder.vvCctv.start();
        }

        @SuppressLint("SetJavaScriptEnabled")
        private void setupWebViewPlayer(ViewHolder holder, CctvItemVo vo) {
            holder.vvCctv.setVisibility(View.GONE);
            holder.webView.setVisibility(View.VISIBLE);

            WebSettings ws = holder.webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setMediaPlaybackRequiresUserGesture(false);
            ws.setCacheMode(WebSettings.LOAD_DEFAULT);
            ws.setDatabaseEnabled(true);

            holder.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                ws.setRenderPriority(WebSettings.RenderPriority.HIGH);
            }

            ws.setLoadsImagesAutomatically(true);
            ws.setBlockNetworkImage(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }

            // ✅ WebView 줌/스크롤 완전 비활성화
            ws.setSupportZoom(false);
            ws.setBuiltInZoomControls(false);
            ws.setDisplayZoomControls(false);
            ws.setUseWideViewPort(true);
            ws.setLoadWithOverviewMode(true);

            holder.webView.setVerticalScrollBarEnabled(false);
            holder.webView.setHorizontalScrollBarEnabled(false);

            // ✅ 핵심: WebView가 터치를 전혀 소비하지 않도록
            holder.webView.setClickable(false);
            holder.webView.setFocusable(false);
            holder.webView.setFocusableInTouchMode(false);
            holder.webView.setLongClickable(false);

            // ✅ 터치 리스너로도 확실히 차단
            holder.webView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    // false를 반환하여 부모(GridView)로 터치 이벤트 전달
                    return false;
                }
            });

            holder.webView.setBackgroundColor(0xFF000000);

            holder.webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public View getVideoLoadingProgressView() {
                    return new View(TestSearchGridActivity.this);
                }

                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    if (newProgress > 10) {
                        injectAllScripts(view);
                    }
                }
            });

            holder.webView.setWebViewClient(new WebViewClient() {
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
                        holder.llProgress.setVisibility(View.GONE);
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
                        holder.llProgress.setVisibility(View.GONE);
                        holder.llVideoError.setVisibility(View.VISIBLE);
                    }
                }
            });

            holder.webView.loadUrl(vo.getCctvUrl());
        }


        /**
         * 페이지 시작과 동시에 기본 CSS 주입
         * 그리드용: 비디오 전체가 영역 안에 들어오도록 (잘림 없음, 터치 비활성화)
         */
        private void injectBaseCSSImmediately(WebView view) {
            String js =
                    "javascript:(function(){ " +
                            "if(!document.getElementById('cctv-base-style')){ " +
                            "var s=document.createElement('style');" +
                            "s.id='cctv-base-style';" +
                            "s.innerHTML=`" +
                            "* { margin:0; padding:0; box-sizing:border-box; }" +
                            "html, body { " +
                            "  width:100%; height:100%; " +
                            "  margin:0; padding:0; " +
                            "  background:#000!important; " +
                            "  overflow:hidden!important; " +
                            "  touch-action:none!important; " +  // ✅ 모든 터치 제스처 비활성화
                            "}" +
                            "video{" +
                            "  position:absolute!important;" +
                            "  top:50%!important;" +
                            "  left:50%!important;" +
                            "  transform:translate(-50%,-50%)!important;" +
                            "  max-width:100%!important;" +
                            "  max-height:100%!important;" +
                            "  width:100%!important;" +
                            "  height:100%!important;" +
                            "  object-fit:contain!important;" +
                            "  background:black!important;" +
                            "  pointer-events:none!important;" +  // ✅ 비디오 터치 차단
                            "  touch-action:none!important;" +
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
         * 그리드용: 비디오 전체가 영역 안에 들어오도록 (잘림 없음, 터치 비활성화)
         */
        private void injectAllScripts(WebView view) {
            String js =
                    "javascript:(function(){ " +
                            // CSS
                            "if(!document.getElementById('cctv-base-style')){ " +
                            "var s=document.createElement('style');" +
                            "s.id='cctv-base-style';" +
                            "s.innerHTML=`" +
                            "* { margin:0; padding:0; box-sizing:border-box; }" +
                            "html, body { " +
                            "  width:100%; height:100%; " +
                            "  margin:0; padding:0; " +
                            "  background:#000!important; " +
                            "  overflow:hidden!important; " +
                            "  touch-action:none!important; " +  // ✅ 모든 터치 제스처 비활성화
                            "}" +
                            "video{" +
                            "  position:absolute!important;" +
                            "  top:50%!important;" +
                            "  left:50%!important;" +
                            "  transform:translate(-50%,-50%)!important;" +
                            "  max-width:100%!important;" +
                            "  max-height:100%!important;" +
                            "  width:100%!important;" +
                            "  height:100%!important;" +
                            "  object-fit:contain!important;" +
                            "  background:black!important;" +
                            "  pointer-events:none!important;" +  // ✅ 비디오 터치 차단
                            "  touch-action:none!important;" +
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
                            "v.style.touchAction='none';" +  // ✅ 비디오 터치 비활성화

                            // 비디오 크기 설정 (contain 효과 - 전체 영상 표시)
                            "v.style.position='absolute';" +
                            "v.style.top='50%';" +
                            "v.style.left='50%';" +
                            "v.style.transform='translate(-50%,-50%)';" +
                            "v.style.maxWidth='100%';" +
                            "v.style.maxHeight='100%';" +
                            "v.style.width='100%';" +
                            "v.style.height='100%';" +
                            "v.style.objectFit='contain';" +

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

        // ViewHolder 패턴
        class ViewHolder {
            LinearLayout llBase;
            FrameLayout frameVideoContainer;
            VideoView vvCctv;
            WebView webView;
            TextView tvTitle;
            ImageView imgCctvType;
            LinearLayout llProgress;
            LinearLayout llVideoError;
            LinearLayout llFavor;
            ImageView imgFavor;
        }
    }
}
