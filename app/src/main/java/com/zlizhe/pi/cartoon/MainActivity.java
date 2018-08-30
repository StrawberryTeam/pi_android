package com.zlizhe.pi.cartoon;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {
    //android 平台
    public final static String PI_URL = "http://pi.zlizhe.com/";

    //已经跳过播放时间的总次数
    public static int CURRENT_PLAY_NUM = 1;

    //允许最大总播放时间 30 min
    public final static int MAX_PLAYTIME = 20 * 60;

    //当前用户的总播放时间
    public static int PLAY_TIMES = 1;

    // 当前 设置 uid
    public static final String UID = "1";
    WebView webview;
    String TAG = "PI_MAIN_LOG";
    // 当前用户的 path 接口获取
//    public static String PATH = "http://piplay.zlizhe.com/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //set up notitle
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //set up full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().hide();

        //禁止旋转 旋转后刷新 webview页面 //SCREEN_ORIENTATION_PORTRAIT
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_main);

        webview = (WebView)findViewById(R.id.webview);
        webView();
    }


//    final TextView txtview = (TextView)findViewById(R.id.tV1);
//    final ProgressBar pbar = (ProgressBar) findViewById(R.id.pB1);

    //Metodo llamar el webview
    private void webView(){
        webview.setWebChromeClient(new WebChromeClient());
        //Handling Page Navigation
        webview.setWebViewClient(new MyWebViewClient());
        //Habilitar JavaScript (Videos youtube)
        webview.getSettings().setJavaScriptEnabled(true);
        //让WebView支持DOM storage API
        webview.getSettings().setDomStorageEnabled(true);
        //设置在WebView内部是否允许访问文件
        webview.getSettings().setAllowFileAccess(true);
        //让WebView支持播放插件
        webview.getSettings().setPluginState(WebSettings.PluginState.ON);
        //设置WebView缓存模式 默认断网情况下不缓存
//        webview.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        //断网情况下加载本地缓存
//        webview.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
//        webview.getSettings().setAppCacheEnabled(true);
//        webview.getSettings().setAppCachePath(getApplicationContext().getFilesDir().getAbsolutePath() + "/cache");
//        webview.getSettings().setDatabaseEnabled(true);
//        webview.getSettings().setDatabasePath(getApplicationContext().getFilesDir().getAbsolutePath() + "/databases");
        webview.getSettings().setBuiltInZoomControls(false);
        webview.getSettings().setSupportZoom(false);
        webview.getSettings().setDisplayZoomControls(false);


        //设置WebView的访问UserAgent
//        webSettings.setUserAgentString(WebViewUtil.getUserAgent(getActivity(), webSettings));

        //Load a URL on WebView
        //android tv 平台标识 in header
        //添加 header 标识 platform = 1 与用户
        Map<String, String> headerMap = new HashMap<String, String>();
        //put all headers in this header map
        headerMap.put("CARTOON", "{\"platform\": 1, \"uid\": "+ UID +"}");
        webview.loadUrl(PI_URL, headerMap);
        //允许 JS fun app 调用
        webview.addJavascriptInterface(new WebViewJavaScriptInterface(this), "android");
    }

    /*
     * JavaScript Interface. Web code can access methods in here
     * (as long as they have the @JavascriptInterface annotation)
     */
    public class WebViewJavaScriptInterface{

        private Context context;

        /*
         * Need a reference to the context in order to sent a post message
         */
        public WebViewJavaScriptInterface(Context context){
            this.context = context;
        }

        /*
         * This method can be called from Android. @JavascriptInterface
         * required after SDK version 17.
         */
        @JavascriptInterface
        public void videoPlay(String setId, String _id, int num){
            if (null == setId || null == _id){
                Toast.makeText(MainActivity.this, "内部错误，该影片无法播放，请稍后重试", Toast.LENGTH_LONG).show();
                return;
            }
            /*
            setId 影片集 id
            _id 当前需要播放的影片 _id
             */
            Intent intent = new Intent(getApplicationContext(), VideoPlayActivity.class);
            intent.putExtra("setId", setId);
            intent.putExtra("videoId", _id);
            intent.putExtra("num", num);
            Log.d(TAG, "videoPlay: num " + num);
            startActivity(intent);
        }
    }


    // Metodo Navigating web page history
    @Override public void onBackPressed() {
        Log.d(TAG, "onBackPressed: User call back");
        if(webview.canGoBack()) {
            webview.goBack();
        } else {
            //用户确认是否退出
//            super.onBackPressed();
            new AlertDialog.Builder(this)
//                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("确认退出")
                    .setMessage("确认要退出吗")
                    .setNegativeButton("退出不看了", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }

                    })
                    .setPositiveButton("在看一会", null)
                    .show();
        }
    }


    //webview 设置
    private class MyWebViewClient extends WebViewClient{
        private int running = 0; // Could be public if you want a timer to check.

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return false;
        }

        ProgressDialog progressDialog;


        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            running = Math.max(running, 1); // First request move it to 1.
        }

        public void onLoadResource (WebView view, String url) {
            if (running > 0) {
                if (progressDialog == null) {
                    progressDialog = new ProgressDialog(MainActivity.this);
                    progressDialog.setMessage("Loading...");
                    progressDialog.show();
                }
            }

        }
        public void onPageFinished(WebView view, String url) {

            try{
                if (--running == 0){
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                }
            }catch(Exception exception){
                exception.printStackTrace();
            }
        }
    }

    private class RequestQueue {
    }

}
