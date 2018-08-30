package com.zlizhe.pi.cartoon;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public class VideoPlayActivity extends AppCompatActivity implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {
    String title = "";
    String url = ""; // your URL here
    String TAG = "PI_VIDEO_PLAY_LOG";
    String PART_URL = "index/part#!/play:episode?setid=%s&num=%d"; //选集菜单
    ProgressDialog progressDialog;

    //进度条
    private SeekBar seekBar;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;
    private String videoTimeString;
    private TextView videoTextView;
    private TextView mTitle;
    private Timer timer;
    private TimerTask seekRun;
    private TextView videoStatusView;
    private ImageView imageStatus;
    private LinearLayout partView;
    private JSONObject playSetting; //播放设置 {'cycle': 'RAND'}
    WebView webview;
    //当前 影片集 id
    private String setId = "";
    //当前 需要播放的影片 id
    private String videoId = "";
    //当前播放到哪集了
    private int num = 1;
    //当前设备的播放 host
    private JSONObject piSetting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //set up notitle
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //set up full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_video_play);

        //loading
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Loading...");
            progressDialog.show();
        }

        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        // 设置surfaceHolder
        surfaceHolder = surfaceView.getHolder();

        // 设置surface回调
        surfaceHolder.addCallback(new SurfaceCallback());
        Log.d(TAG, "playSurface: surface loaded");

        //选集面板
        partView = (LinearLayout) findViewById(R.id.partView);
        webview = (WebView)findViewById(R.id.webviewPart);
    }


    //当前播放 用于取一集
    Integer nowPlayI = -1;

    //播放当前指定
    public final static String PLAY_CURRENT = "PLAY_CURRENT";

    //播放上一集
    public final static String PLAY_PRE = "PLAY_PRE";

    //播放下一集
    public final static String PLAY_NEXT = "PLAY_NEXT";

    //播放第一集
    public final static String PLAY_FIRST = "PLAY_FIRST";

    //获取需要播放的影片信息
    private void getVideoInfo(String play) throws JSONException {
        Log.d(TAG, "getVideoInfo: Now " + videoId);
        JSONObject videoItem = null;
        switch (play){
            case PLAY_CURRENT: //播放当前
                videoItem = resList.get(play);
                break;
            case PLAY_PRE: //上一集
            case PLAY_NEXT: //播放当前集的下一集
                videoItem = resList.get(play);
//                Toast.makeText(VideoPlayActivity.this, "正在切换影片", Toast.LENGTH_LONG).show();
                Log.d(TAG, "getVideoInfo: next videoItem " + videoItem);
                //创建新视频播放
                if (videoItem != null && videoItem.has("_id")){
                    createNewPlayer(setId, videoItem.getString("_id"), num + 1);
                }else{
                    doNextPlay(play);
                }
                return;
            case PLAY_FIRST: //从第一集开始播
                videoItem = resList.get(play);
//                Toast.makeText(VideoPlayActivity.this, "正在切换影片", Toast.LENGTH_LONG).show();
                Log.d(TAG, "getVideoInfo: first videoItem " + videoItem);
                //创建新视频播放
                if (videoItem != null && videoItem.has("_id")){
                    createNewPlayer(setId, videoItem.getString("_id"),  1);
                }else{
                    Toast.makeText(VideoPlayActivity.this, "没有可播放的影片了", Toast.LENGTH_LONG).show();
                    finish();
                }
                return;
        }

        //拉取需要播放的视频信息
        if (videoItem != null && videoItem.has("_id")){
            title = videoItem.getString("name");
            //plays array plays[1][url]
            JSONObject plays = videoItem.getJSONObject("plays");
            url = piSetting.getString("host") + plays.getString(MainActivity.UID);
            Log.d(TAG, "getVideoInfo: " + title + url);
            // 准备完成 开始播放
            playVideo();
            webView();
        }else{
            //这个视频没有了，下一步怎么做
            doNextPlay(play);
        }
    }

    private final static String URL_VIDEO_INFO = "videolist/get_info";

    //api 成功返回的 code
    private final static Integer API_SUCCESS = 0;
    // 空
    private final static Integer API_EMPTY = 2;
    //失败
    private final static Integer API_FAILD = 1;


    //播放列表数据
    private Map<String, JSONObject> resList = new HashMap<String, JSONObject>();

    //获取影片集信息 与 影片集列表
    private void getVideoList(){
        Log.d(TAG, "getVideoList: getVideoList videoId " + videoId);
        // Instantiate the RequestQueue.
        com.android.volley.RequestQueue queue = Volley.newRequestQueue(this);
        //当前平台 platform = 1 android tv
        String vurl = MainActivity.PI_URL + URL_VIDEO_INFO + "?videoId=" + videoId + "&uid=" + MainActivity.UID + "&platform=1";

        Log.d(TAG, "getVideoList: get url " + vurl);
        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, vurl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        JSONObject resobj = null;
                        try {
                            resobj = new JSONObject(response);
                            /**
                             * {
                             "setting": {
                             "_id": "5a29077797964295ed9c0119",
                             "name": "1102 客",
                             "uid": "1",
                             "host": "http://192.168.0.105/",
                             "cycle": "ONCE"
                             },
                             "item": {
                             "_id": "5a3ca6c61234873a1069ac91",
                             "img": "https://vthumb.ykimg.com/054102015A1B7D731896757D830A0BDB",
                             "name": "01 导游光头强",
                             "duration": "13:30",
                             "setId": {
                             "$oid": "5a3ca6c41234873a1069ac60"
                             },
                             "link": "http://v.youku.com/v_show/id_XMzE4NjQwNzIxNg==.html",
                             "summary": "光头强再就业成了一名导游，带着一个刁蛮游客游览狗熊岭，为了让游客满意给自己五星好评，光头强请动物帮忙并答应大家不再砍树。动物们各展所长向游客展示才艺，却屡遭不满。为了好评，光头强和动物们合力帮游客做森林美容，却被吉吉捣乱，游客生气离去声称要投诉光头强。吉吉逃跑时受伤被路过的赵琳救了，赵琳是老赵头的侄女。此时被游客投诉的光头强接到老赵头电话不情愿的前往车站接赵琳。",
                             "plays": {
                             "1": "1222/ZtEfmEvFCI.mp4"
                             }
                             },
                             "nextItem": {
                             "_id": "5a3ca6c61234873a1069ac92",
                             "img": "https://vthumb.ykimg.com/054104085A1B8F1019C2FE5ADA0DC35D",
                             "name": "02 冤家路窄",
                             "duration": "13:30",
                             "setId": {
                             "$oid": "5a3ca6c41234873a1069ac60"
                             },
                             "link": "http://v.youku.com/v_show/id_XMzE4NzY5NjMxMg==.html",
                             "summary": "赵琳终于回到狗熊岭，兴奋的在小镇跑来跑去，却多次巧遇光头强，两人像欢喜冤家似的多次发生矛盾，各种看不顺眼。原来光头强是受老赵头之托来接赵琳的，可是因为互相不认识多次错过，一天的寻找光头强累的够呛，接到老赵头电话，才知道赵琳已经回家了。光头强郁闷的赶到老赵头家发现真是冤家路窄，两人在饭桌上一番明争暗斗。为了不让老赵头担心，赵琳说她是来旅游的，老赵头拜托光头强给赵琳当导游，光头强无奈答应。",
                             "plays": {
                             "1": "1222/ZtEfmEvFCI.mp4"
                             }
                             },
                             "code": 0,
                             "msg": "",
                             "ref": null
                             }
                             */
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        try {
                            Log.d(TAG, "onResponse: code" + resobj.getString("code"));
                            //看 code 是否是成功
                            if (API_SUCCESS == resobj.getInt("code")){
                                //当前需要播放的视频
                                resList.put(PLAY_CURRENT, resobj.getJSONObject("item"));
                                //下一集需要播放的视频
                                resList.put(PLAY_NEXT, resobj.getJSONObject("nextItem"));
                                //上一集
                                resList.put(PLAY_PRE, resobj.getJSONObject("preItem"));
                                //第一集
                                resList.put(PLAY_FIRST, resobj.getJSONObject("firstItem"));
                                piSetting = resobj.getJSONObject("setting");
                                Log.d(TAG, "onResponse: reslist " + resList);
                                //播放当前选择的视频
                                getVideoInfo(PLAY_CURRENT);
                            }else if (API_EMPTY == resobj.getInt("code")) {
                                //API 空
                                Toast.makeText(VideoPlayActivity.this, "该影片未正确加载，请重试选择影片进行播放", Toast.LENGTH_LONG).show();
                                Log.d(TAG, "onResponse: API Error " + resobj.getString("msg"));
                                finish();
                            }else{
                                //API 其他错误
                                Toast.makeText(VideoPlayActivity.this, resobj.getString("msg"), Toast.LENGTH_LONG).show();
                                Log.d(TAG, "onResponse: API Error " + resobj.getString("msg"));
                                finish();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(VideoPlayActivity.this, "网络请求失败，请返回重试", Toast.LENGTH_LONG).show();
                Log.d(TAG, "onErrorResponse: error" + error);
            }
        });
        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }


    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    //当前视频无法播放 下面如何播放
    public void doNextPlay(String type){
        Log.d(TAG, "doNextPlay: ");
        try {
            String cycle = playSetting.getString("cycle"); // this is cycle setting
            //如果是单次播放 结束播放 并且是 next 播放的
            if (type.equals(PLAY_NEXT)){

                if (cycle.equals("ONCE")) {
                    Toast.makeText(VideoPlayActivity.this, "单次播放结束", Toast.LENGTH_LONG).show();
                    finish();
                }else{
                    //全部循环从头开始播 ALL
                    getVideoInfo(PLAY_FIRST);
                }
            }else{
                //播放本集 无内容的
                Toast.makeText(VideoPlayActivity.this, "本集无法播放，正在切换下一集", Toast.LENGTH_LONG).show();
                //尝试播放下一集
                getVideoInfo(PLAY_NEXT);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    //播放完后播放下一个
    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        Log.d(TAG, "onCompletion: Video Play complete");
        try {
            String cycle = playSetting.getString("cycle"); // this is cycle setting
            Log.d(TAG, "onCompletion: cycle " + cycle);

            //全部循环 | 单次播放
            if (cycle.equals("ALL") || cycle.equals("ONCE")){
                try {
                    getVideoInfo(PLAY_NEXT);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            //单集循环
            if (cycle.equals("SINGER")){
                Log.d(TAG, "onCompletion: SINGER Revideo " + videoId);
                createNewPlayer(setId, videoId, num);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private JSONObject setInfo;

    //surface 显示
    private class SurfaceCallback implements SurfaceHolder.Callback {
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // SurfaceView的大小改变
        }

        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "surfaceCreated: ");
            // surfaceView被创建
            //获取信息
            Intent intent = getIntent();
            String nowSetId = intent.getStringExtra("setId").trim(); //剧集 id
            videoId = intent.getStringExtra("videoId").trim(); //视频 id
            num = intent.getIntExtra("num", 1); //当前播放的视频 在剧集中的 排序
//            try {
//                //影片集信息
//                setInfo = new JSONObject(intent.getStringExtra("setInfo").trim());
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
            //无需重新获取影片集信息
            if (nowSetId.equals(setId) && resList != null){

                //使用已有信息 查找 不同的 videoId 信息
                Log.d(TAG, "onCreate: resList has one");
                try {
                    getVideoInfo(PLAY_CURRENT);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }else{
                setId = nowSetId;
                //获取影片集信息
                getVideoList();
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // surfaceView销毁
            // 如果MediaPlayer没被销毁，则销毁mediaPlayer
            if (null != mediaPlayer) {
                Log.d(TAG, "surfaceDestroyed: Destroy mediaplayer");
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
    }

    /**
     * 播放视频
     */
    public void playVideo() {

        Log.d(TAG, "playVideo: Init MediaPlayer");
        if (null == mediaPlayer){
            // 初始化MediaPlayer
            mediaPlayer = new MediaPlayer();
            // 重置mediaPaly,建议在初始滑mediaplay立即调用。
            mediaPlayer.reset();
            // 设置声音效果
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            // 设置播放完成监听
            mediaPlayer.setOnCompletionListener(this);
            // 设置媒体加载完成以后回调函数。
            mediaPlayer.setOnPreparedListener(this);
//        // 错误监听回调函数
//        mediaPlayer.setOnErrorListener(this);
//        // 设置缓存变化监听
//        mediaPlayer.setOnBufferingUpdateListener(this);
        }
        try {
//            mediaPlayer.reset();
//            mediaPlayer.setDataSource("assets/playv.mp4");
            Log.d(TAG, Uri.parse(url).toString());
            //@todo test
//            url = "http://cms-test.cdn.xwg.cc/%E7%B2%BE%E7%81%B5%E5%AE%9D%E5%8F%AF%E6%A2%A6%E5%A4%AA%E9%98%B3%E6%9C%88%E4%BA%AE%2048.mp4";
            mediaPlayer.setDataSource(VideoPlayActivity.this, Uri.parse(url));
            // 设置异步加载视频，包括两种方式 prepare()同步，prepareAsync()异步
            mediaPlayer.prepareAsync();
//            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 视频加载完毕监听
     *
     * @param mp
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
        // 当视频加载完毕以后，隐藏加载进度条
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
        // 播放视频
        mediaPlayer.start();
        // 设置显示到屏幕
        mediaPlayer.setDisplay(surfaceHolder);
        // 设置surfaceView保持在屏幕上
        mediaPlayer.setScreenOnWhilePlaying(true);
        surfaceHolder.setKeepScreenOn(true);
        //视频总长度
        maxPosition = mediaPlayer.getDuration();

        //标题
        mTitle = (TextView) findViewById(R.id.textView_showTitle);
        mTitle.setText(title);
        // 设置控制条,放在加载完成以后设置，防止获取getDuration()错误
        seekBar = (SeekBar) findViewById(R.id.seekbar);
        seekBar.setProgress(0);
        seekBar.setMax(maxPosition);
        //设置播放状态
        videoStatusView = (TextView) findViewById(R.id.textView_status);
//        videoStatusView.setText("播放中");
        // 设置播放时间
        videoTextView = (TextView) findViewById(R.id.textView_showTime);
        videoTimeString = getShowTime(maxPosition);
        videoTextView.setText("00:00:00/" + videoTimeString);
        // 设置拖动监听事件
        seekBar.setOnSeekBarChangeListener(new SeekBarChangeListener());
        seekBarAutoFlag = true;

        //image_status
        imageStatus = (ImageView) findViewById(R.id.image_status);
        Toast.makeText(VideoPlayActivity.this, "正在播放: " + title, Toast.LENGTH_LONG).show();
//        setStatus("正在播放：" + title);
//        setImageStatus("play");
//        hideStatus();
        // 开启线程 刷新进度条
//        thread.start();

        //计时器 重新计算进度
        seekRun = new TimerTask(){
            @Override
            public void run() {

                // 增加对异常的捕获，防止在判断mediaPlayer.isPlaying的时候，报IllegalStateException异常
                try {
                    if (seekBarAutoFlag){
//                    while (seekBarAutoFlag) {
                   /*
                    * mediaPlayer不为空且处于正在播放状态时，使进度条滚动。
                    * 通过指定类名的方式判断mediaPlayer防止状态发生不一致
                    */
                        if (null != VideoPlayActivity.this.mediaPlayer
                                && VideoPlayActivity.this.mediaPlayer.isPlaying()) {
                            seekBar.setProgress(mediaPlayer.getCurrentPosition());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        timer = new Timer();
        timer.schedule(seekRun, 0, 1000);
    }
    /**
     * seekBar拖动监听类
     *
     * @author shenxiaolei
     */
    @SuppressWarnings("unused")
    private class SeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {

        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (progress >= 0) {
                // 如果是用户手动拖动控件，则设置视频跳转。
                if (fromUser) {
                    mediaPlayer.seekTo(progress);
                }
                // 设置当前播放时间
                videoTextView.setText(getShowTime(progress) + "/" + videoTimeString);
                //每次观看 超过30分钟后 提示
                MainActivity.PLAY_TIMES++;

                double onceplayTime;
                //超过1次
                if (MainActivity.CURRENT_PLAY_NUM > 1){
                    onceplayTime = Math.ceil(((MainActivity.CURRENT_PLAY_NUM - 1) * MainActivity.MAX_PLAYTIME + MainActivity.PLAY_TIMES) / 60);
                }else{
                    onceplayTime = Math.ceil(MainActivity.PLAY_TIMES / 60);
                }

                //超过时间了
                if (MainActivity.PLAY_TIMES > MainActivity.MAX_PLAYTIME){
                    //暂停
                    parsePlay();
                    new AlertDialog.Builder(VideoPlayActivity.this)
//                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setMessage("今天的节目已经全部结束了, 本次已经累计观看 " + onceplayTime + " 分钟了")
                            .setNegativeButton("好的，退出", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .setPositiveButton("在看一会", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    MainActivity.CURRENT_PLAY_NUM++;
                                    MainActivity.PLAY_TIMES = 1; //从头计时
                                    restorePlay();
                                }
                            })
                            .show();
                }

                //@todo 增加设置中需要允许 自动跳过片头片尾 这里要跳过片尾
            }
        }

        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        public void onStopTrackingTouch(SeekBar seekBar) {

        }

    }

    /**
     * 转换播放时间
     *
     * @param milliseconds 传入毫秒值
     * @return 返回 hh:mm:ss或mm:ss格式的数据
     */
    public String getShowTime(long milliseconds) {
        // 获取日历函数
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(milliseconds);
        SimpleDateFormat dateFormat = null;
        // 判断是否大于60分钟，如果大于就显示小时。设置日期格式
        if (milliseconds / 60000 > 60) {
            dateFormat = new SimpleDateFormat("hh:mm:ss");
        } else {
            dateFormat = new SimpleDateFormat("mm:ss");
        }
        return dateFormat.format(calendar.getTime());
    }


    //滚动条自动滚动
    private boolean seekBarAutoFlag;

    //前进 或 后退 position 10秒
    private Integer timeInterval = 5 * 1000;

    //影片总长度
    private Integer maxPosition = 0;

    //暂时在哪里
    private Integer intPositionWhenPause = 0;

    //当前需要前往的长度位置
    private Integer nowPosition;

    //显示当前状态
    private void setStatus(String txt){

        videoStatusView.setText(txt);
        videoStatusView.setVisibility(View.VISIBLE);
    }

    //设置状态图
    private void setImageStatus(String status){

        int resId = getResources().getIdentifier("com.zlizhe.pi.cartoon:drawable/" + status, null, null);
        imageStatus.setImageResource(resId);
        imageStatus.setVisibility(View.VISIBLE);
    }

    //自动隐藏状态显示
    private void hideStatus(){
        Timer stimer = new Timer();
        stimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        videoStatusView.setVisibility(View.INVISIBLE);
                        imageStatus.setVisibility(View.INVISIBLE);
                    }
                });
            }
        }, 1000);
    }

    //关闭选集菜单
    public void closePartView(){
        if (View.VISIBLE == partView.getVisibility()){
            partView.setVisibility(View.GONE);
        }
    }

    //暂停播放
    public void parsePlay(){
        seekBarAutoFlag = false;
        setStatus("暂停");
        setImageStatus("pause");
        mediaPlayer.pause();
        //如果当前页面暂停则保存当前播放位置，全局变量保存
        intPositionWhenPause = mediaPlayer.getCurrentPosition();
    }

    //继续播放
    public void restorePlay(){
        seekBarAutoFlag = true;
        setStatus("播放");
        setImageStatus("play");
        hideStatus();
        mediaPlayer.start();
        //跳转到暂停时保存的位置
        if(intPositionWhenPause >= 0){
            mediaPlayer.seekTo(intPositionWhenPause);
            //初始播放位置
            intPositionWhenPause = 0;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        switch (keyCode) {

            case KeyEvent.KEYCODE_ENTER:     //确定键enter
            case KeyEvent.KEYCODE_INFO:    //info键
            case KeyEvent.KEYCODE_DPAD_CENTER: //中间
                Log.d(TAG,"enter--->");
                //如果打开了选集面板 优先操作
                if (View.VISIBLE == partView.getVisibility()){
                    return super.onKeyDown(keyCode, event);
                }

                if (mediaPlayer.isPlaying()){
                    parsePlay();
                }
                webView();

                //显示选集面板
                partView.setVisibility(View.VISIBLE);
//                else{
//                    restorePlay();
//                }
                break;

            case KeyEvent.KEYCODE_BACK:    //返回键
                Log.d(TAG,"back--->");
                //如果打开了选集面板 优先关闭
                if (View.VISIBLE == partView.getVisibility()){
                    closePartView();
                    return true;
                }

                //暂停
                parsePlay();
                //退出该影片播放
                new AlertDialog.Builder(this)
//                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("确认退出")
                        .setMessage("确认要退出吗")
                        .setNegativeButton("退出不看了", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }

                        })
                        .setPositiveButton("在看一会", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                restorePlay(); //恢复播放
                            }
                        })
                        .show();

                return true;   //这里由于break会退出，所以我们自己要处理掉 不返回上一层

            // 打开剧集选单
//            case KeyEvent.KEYCODE_SETTINGS: //设置键

            // 返回首页 ?
            case KeyEvent.KEYCODE_0:   //数字键0
                Log.d(TAG,"0--->");

                break;

            case KeyEvent.KEYCODE_DPAD_LEFT: //向左键
                if (View.VISIBLE == partView.getVisibility()){
                    return super.onKeyDown(keyCode, event);
                }
                setImageStatus("previous");
                hideStatus();
                Log.d(TAG,"left--->");
//                mediaController.show();
                nowPosition = mediaPlayer.getCurrentPosition() - timeInterval;
                Log.d(TAG, "onKey: nowPosition" + mediaPlayer.getCurrentPosition());
                seekBar.setProgress(nowPosition < 1 ? 1 : nowPosition); //不超过总长度则快进
                mediaPlayer.seekTo(nowPosition < 1 ? 1 : nowPosition); //不超过总长度则快进
                Log.d(TAG, "onKey: seekTo" + nowPosition);
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:  //向右键
                if (View.VISIBLE == partView.getVisibility()){
                    return super.onKeyDown(keyCode, event);
                }
                setImageStatus("next");
                hideStatus();
                Log.d(TAG,"right--->");
//                mediaController.show();
                nowPosition = mediaPlayer.getCurrentPosition() + timeInterval;
                Log.d(TAG, "onKey: nowPosition" + mediaPlayer.getCurrentPosition());
                Log.d(TAG, "onKey: maxPosition" + maxPosition);
                //直接到下一集
                if (nowPosition >= maxPosition){
                    try {
                        getVideoInfo(PLAY_NEXT);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }else{
                    seekBar.setProgress(nowPosition); //不超过总长度则快进
                    mediaPlayer.seekTo(nowPosition); //不超过总长度则快进
                    Log.d(TAG, "onKey: seekTo" + nowPosition);
                }
                break;


            case KeyEvent.KEYCODE_DPAD_DOWN:   //向下键
            case KeyEvent.KEYCODE_1:
            case KeyEvent.KEYCODE_PAGE_DOWN:     //向上翻页键
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                closePartView();
                Log.d(TAG,"page down--->");

                try {
                    getVideoInfo(PLAY_PRE);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;


            case KeyEvent.KEYCODE_DPAD_UP:   //向上键
            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_PAGE_UP:     //向下翻页键
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                closePartView();
                Log.d(TAG,"page up--->");

                try {
                    getVideoInfo(PLAY_NEXT);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;

            case KeyEvent.KEYCODE_2: //2 重放
                closePartView();
                Log.d(TAG, "onKeyDown: key2 replay");
                seekBar.setProgress(1); //不超过总长度则快进
                mediaPlayer.seekTo(1); //不超过总长度则快进
                break;

            case KeyEvent.KEYCODE_5: //无图像只听声音
                closePartView();
                break;

            case KeyEvent.KEYCODE_VOLUME_UP:   //调大声音键
                Log.d(TAG,"voice up--->");

                break;

            case KeyEvent.KEYCODE_VOLUME_DOWN: //降低声音键
                Log.d(TAG,"voice down--->");

                break;
            default:
                break;
        }

        return super.onKeyDown(keyCode, event);

    }
//
//    public void next(View view){
//        setImageStatus("next");
//        hideStatus();
//        Log.d(TAG,"right--->");
////                mediaController.show();
//        nowPosition = mediaPlayer.getCurrentPosition() + timeInterval;
//        Log.d(TAG, "onKey: nowPosition" + mediaPlayer.getCurrentPosition());
//        Log.d(TAG, "onKey: maxPosition" + maxPosition);
//        //直接到下一集
//        if (nowPosition > maxPosition){
//            try {
//                getVideoInfo(playNext);
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
//        }else{
//            seekBar.setProgress(nowPosition); //不超过总长度则快进
//            mediaPlayer.seekTo(nowPosition); //不超过总长度则快进
//            Log.d(TAG, "onKey: seekTo" + nowPosition);
//        }
//    }

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
        headerMap.put("CARTOON", "{\"platform\": 1, \"uid\": "+ MainActivity.UID +"}");

        webview.loadUrl(MainActivity.PI_URL + String.format(PART_URL, setId, num), headerMap);
        Log.d(TAG, "webView: load play:episode " + MainActivity.PI_URL + String.format(PART_URL, setId, num));
        //允许 JS fun app 调用
        webview.addJavascriptInterface(new VideoPlayActivity.WebViewJavaScriptInterface(this), "android");
    }

    //创建一个新视频
    public void createNewPlayer(String setId, String videoId, int num){
        /*
        setId 影片集 id
        _id 当前需要播放的影片 _id
         */
        Intent intent = new Intent(getApplicationContext(), VideoPlayActivity.class);
        intent.putExtra("setId", setId);
        intent.putExtra("videoId", videoId);
        intent.putExtra("num", num);
        Log.d(TAG, String.format("createNewPlayer: setId %s videoId %s num %d", setId, videoId, num));
        // 开一新的播放窗
        startActivity(intent);
        finish(); //关闭当前播放窗口
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

        //获取需要播放的影片
        @JavascriptInterface
        public void videoPlay(String setId, String _id, int num){
            if (null == setId || null == _id){
                Toast.makeText(VideoPlayActivity.this, "内部错误，该影片无法播放，请稍后重试", Toast.LENGTH_LONG).show();
                return;
            }

            //创建一个新视频
            createNewPlayer(setId, _id, num);
        }

        //获取播放设置
        @JavascriptInterface
        public void getsetting(String setting){

            if (null == setting){
                Toast.makeText(VideoPlayActivity.this, "设置未保存成功，请稍后重试", Toast.LENGTH_LONG).show();
                return;
            }else{
                try {
                    playSetting = new JSONObject(setting);
//                    closePartView();
                    //不提示保存成功，获取设置也是通过此方法
//                    Toast.makeText(VideoPlayActivity.this, "设置保存成功", Toast.LENGTH_LONG).show();
//                    String cycle = playSetting.getString("cycle"); // this is cycle setting
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    //webview 设置
    private class MyWebViewClient extends WebViewClient {
        private int running = 0; // Could be public if you want a timer to check.

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return false;
        }

        ProgressDialog progressDialog;


        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Log.d(TAG, "onLoadResource: webview page start");
            super.onPageStarted(view, url, favicon);
            running = Math.max(running, 1); // First request move it to 1.
        }

        public void onLoadResource (WebView view, String url) {
            Log.d(TAG, "onLoadResource: webview loadres");
//            if (running > 0) {
//                if (progressDialog == null) {
//                    progressDialog = new ProgressDialog(VideoPlayActivity.this);
//                    progressDialog.setMessage("Loading...");
//                    progressDialog.show();
//                }
//            }

        }
        public void onPageFinished(WebView view, String url) {
//            try{
//                if (--running == 0){
//                    if (progressDialog.isShowing()) {
//                        progressDialog.dismiss();
//                        progressDialog = null;
//                    }
//                }
//            }catch(Exception exception){
//                exception.printStackTrace();
//            }
        }
        public void onScaleChanged(WebView view, float oldScale, float newScale){
            Log.d(TAG, "onScaleChanged: webview scale changed");
            
        }
    }

}
