package com.kurdish.tv;

import android.app.*;
import android.os.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {
  private ExoPlayer player;
  private PlayerView playerView;
  private TextView stateText, playButton, muteButton, lockButton, fitButton, liveBadge;
  private ProgressBar loading;
  private LinearLayout topBar, bottomPanel, extraPanel, channelPanel, channelList;
  private FrameLayout drawerLayer;
  private EditText drawerSearch;
  private TextView drawerFavFilter;
  private boolean drawerFavoritesOnly=false;
  private JSONArray channelItems=new JSONArray();
  private String runtimeBase="", runtimeKey="", currentChannelId="";
  private boolean drawerVisible=false;
  private SeekBar volumeSeek, brightnessSeek;
  private Handler handler=new Handler(Looper.getMainLooper());
  private Runnable hideRunnable;
  private String streamUrl, channelTitle, sourceApiUrl, relayUrl;
  private boolean directAttempted=false, relayAttempted=false, runtimeRefreshAttempted=false;
  private String activePlaybackUrl="";
  private int networkRetryCount=0;
  private Runnable networkRetryRunnable;
  private boolean muted=false, controlsLocked=false, controlsVisible=true;
  private int fitMode=AspectRatioFrameLayout.RESIZE_MODE_FIT;
  private float oldBrightness=-1f;
  private final int WHITE=0xfff7f3ff, MUTED=0xffb0a5c2, ACCENT=0xffb12cff;

  int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  GradientDrawable gradient(int top,int bottom,int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});d.setCornerRadius(dp(radius));return d;}
  GradientDrawable stroke(int color,int line,int radius){GradientDrawable d=bg(color,radius);d.setStroke(dp(1),line);return d;}
  TextView txt(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);return t;}
  TextView control(String value){TextView t=txt(value,19,WHITE);t.setBackground(stroke(0xdd172438,0x665b718d,16));t.setClickable(true);t.setFocusable(true);t.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x66ffffff),null,bg(0xffffffff,16)));return t;}
  LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    channelTitle=getIntent().getStringExtra("title");streamUrl=getIntent().getStringExtra("url");
    if(channelTitle==null)channelTitle="Live TV";if(streamUrl==null)streamUrl="";
    sourceApiUrl=getIntent().getStringExtra("source_api_url");
    relayUrl=getIntent().getStringExtra("relay_url");
    runtimeBase=getIntent().getStringExtra("runtime_base");if(runtimeBase==null)runtimeBase="";
    runtimeKey=getIntent().getStringExtra("runtime_key");if(runtimeKey==null)runtimeKey="";
    currentChannelId=getIntent().getStringExtra("channel_id");if(currentChannelId==null)currentChannelId="";
    try{channelItems=new JSONArray(getIntent().getStringExtra("channels_json"));}catch(Exception ignored){}
    if(relayUrl==null||relayUrl.isEmpty())relayUrl=streamUrl;
    getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    hideSystemBars();
    FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
    playerView=new PlayerView(this);playerView.setUseController(false);playerView.setShutterBackgroundColor(Color.BLACK);playerView.setKeepContentOnPlayerReset(true);playerView.setResizeMode(fitMode);
    root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
    FrameLayout shade=new FrameLayout(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xaa050d18,0x00000000,0x00000000,0xbb050d18}));root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout overlay=new LinearLayout(this);overlay.setOrientation(1);
    FrameLayout.LayoutParams op=new FrameLayout.LayoutParams(-1,-1);root.addView(overlay,op);
    topBar=new LinearLayout(this);topBar.setGravity(Gravity.CENTER_VERTICAL);topBar.setPadding(dp(18),dp(12),dp(18),dp(8));topBar.setBackground(gradient(0xee0b0616,0x000b0616,0));
    TextView back=control("‹");topBar.addView(back,lp(44,44));back.setOnClickListener(v->finish());
    LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(1);titleBox.setPadding(dp(12),0,0,0);
    TextView title=txt(channelTitle,17,WHITE);title.setGravity(Gravity.CENTER_VERTICAL);title.setTypeface(null,Typeface.BOLD);
    TextView sub=txt("KURDISH TV+  •  PREMIUM LIVE",9,0xffdfb5ff);sub.setGravity(Gravity.CENTER_VERTICAL);
    titleBox.addView(title);titleBox.addView(sub);topBar.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
    TextView channelsButton=control("☷");LinearLayout.LayoutParams chp=lp(48,44);chp.leftMargin=dp(8);topBar.addView(channelsButton,chp);channelsButton.setOnClickListener(v->toggleChannelDrawer());
    liveBadge=txt("●  LIVE",11,ACCENT);liveBadge.setPadding(dp(12),dp(8),dp(12),dp(8));liveBadge.setBackground(bg(0xdd38105a,24));topBar.addView(liveBadge);
    overlay.addView(topBar,lp(-1,62));
    View spacer=new View(this);overlay.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
    LinearLayout center=new LinearLayout(this);center.setGravity(Gravity.CENTER);center.setOrientation(1);
    loading=new ProgressBar(this);center.addView(loading,lp(42,42));
    stateText=txt("پەیوەندی بە کەناڵ...",13,WHITE);LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-2,-2);st.topMargin=dp(10);center.addView(stateText,st);
    overlay.addView(center,new LinearLayout.LayoutParams(-1,-2));
    View spacer2=new View(this);overlay.addView(spacer2,new LinearLayout.LayoutParams(1,0,1));
    extraPanel=new LinearLayout(this);extraPanel.setOrientation(1);extraPanel.setPadding(dp(20),dp(8),dp(20),dp(8));extraPanel.setBackground(gradient(0x000b0616,0xee0b0616,0));
    LinearLayout volRow=new LinearLayout(this);volRow.setGravity(Gravity.CENTER_VERTICAL);volRow.addView(txt("🔊",16,WHITE),lp(35,34));
    volumeSeek=new SeekBar(this);volumeSeek.setMax(100);volumeSeek.setProgress(100);volRow.addView(volumeSeek,new LinearLayout.LayoutParams(0,dp(34),1));extraPanel.addView(volRow);
    LinearLayout brightRow=new LinearLayout(this);brightRow.setGravity(Gravity.CENTER_VERTICAL);brightRow.addView(txt("☼",20,WHITE),lp(35,34));
    brightnessSeek=new SeekBar(this);brightnessSeek.setMax(100);brightnessSeek.setProgress(70);brightRow.addView(brightnessSeek,new LinearLayout.LayoutParams(0,dp(34),1));extraPanel.addView(brightRow);
    overlay.addView(extraPanel,lp(-1,-2));
    volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){if(player!=null){player.setVolume(p/100f);muted=p==0;muteButton.setText(muted?"×":"🔊");}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
    brightnessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=Math.max(.02f,p/100f);getWindow().setAttributes(a);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
    bottomPanel=new LinearLayout(this);bottomPanel.setGravity(Gravity.CENTER_VERTICAL);bottomPanel.setPadding(dp(18),dp(10),dp(18),dp(14));bottomPanel.setBackground(gradient(0xee10091d,0xff080713,0));
    playButton=control("Ⅱ");bottomPanel.addView(playButton,lp(50,46));playButton.setOnClickListener(v->{if(player==null)return;if(player.isPlaying())player.pause();else player.play();});
    TextView label=txt("KURDISH TV+  •  LIVE",11,0xffc0cbd9);label.setGravity(Gravity.CENTER_VERTICAL);label.setPadding(dp(13),0,0,0);bottomPanel.addView(label,new LinearLayout.LayoutParams(0,-2,1));
    TextView previousChannel=control("▲");bottomPanel.addView(previousChannel,lp(42,46));previousChannel.setOnClickListener(v->tuneRelative(1));
    TextView nextChannel=control("▼");LinearLayout.LayoutParams ncp=lp(42,46);ncp.leftMargin=dp(6);bottomPanel.addView(nextChannel,ncp);nextChannel.setOnClickListener(v->tuneRelative(-1));
    muteButton=control("🔊");bottomPanel.addView(muteButton,lp(48,46));muteButton.setOnClickListener(v->{muted=!muted;if(player!=null)player.setVolume(muted?0f:(volumeSeek.getProgress()/100f));muteButton.setText(muted?"×":"🔊");});
    fitButton=control("▣");LinearLayout.LayoutParams fp=lp(48,46);fp.leftMargin=dp(8);bottomPanel.addView(fitButton,fp);fitButton.setOnClickListener(v->{fitMode=fitMode==AspectRatioFrameLayout.RESIZE_MODE_FIT?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:fitMode==AspectRatioFrameLayout.RESIZE_MODE_ZOOM?AspectRatioFrameLayout.RESIZE_MODE_FILL:AspectRatioFrameLayout.RESIZE_MODE_FIT;playerView.setResizeMode(fitMode);fitButton.setText(fitMode==AspectRatioFrameLayout.RESIZE_MODE_FIT?"FIT":fitMode==AspectRatioFrameLayout.RESIZE_MODE_ZOOM?"ZOOM":"FILL");showControls();});
    TextView retry=control("↻");LinearLayout.LayoutParams rp=lp(48,46);rp.leftMargin=dp(8);bottomPanel.addView(retry,rp);retry.setOnClickListener(v->startStream());
    lockButton=control("🔓");LinearLayout.LayoutParams lk=lp(48,46);lk.leftMargin=dp(8);bottomPanel.addView(lockButton,lk);lockButton.setOnClickListener(v->{controlsLocked=!controlsLocked;lockButton.setText(controlsLocked?"🔒":"🔓");if(controlsLocked){extraPanel.setVisibility(View.GONE);topBar.setVisibility(View.GONE);bottomPanel.setVisibility(View.GONE);controlsVisible=false;}else showControls();});
    overlay.addView(bottomPanel,lp(-1,68));
    playerView.setOnClickListener(v->{if(controlsLocked){controlsLocked=false;lockButton.setText("🔓");showControls();}else toggleControls();});
    setContentView(root);buildChannelDrawer(root);if(!relayUrl.isEmpty())playUrl(relayUrl);else refreshRuntime(()->playRelay("پەخش ئامادەیە"),true);showControls();
  }
  private void buildChannelDrawer(FrameLayout root){
    drawerLayer=new FrameLayout(this);drawerLayer.setVisibility(View.GONE);
    drawerLayer.setBackgroundColor(0x99000000);
    drawerLayer.setOnClickListener(v->toggleChannelDrawer());
    channelPanel=new LinearLayout(this);channelPanel.setOrientation(1);channelPanel.setPadding(dp(14),dp(18),dp(14),dp(14));
    channelPanel.setBackground(gradient(0xff211333,0xff0c0816,22));
    FrameLayout.LayoutParams panelParams=new FrameLayout.LayoutParams(dp(330),-1,Gravity.RIGHT);
    panelParams.setMargins(0,dp(10),dp(10),dp(10));
    drawerLayer.addView(channelPanel,panelParams);
    LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
    TextView heading=txt("KURDISH TV+  /  کەناڵەکان",16,WHITE);heading.setTypeface(null,Typeface.BOLD);head.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
    TextView close=control("×");head.addView(close,lp(42,42));close.setOnClickListener(v->toggleChannelDrawer());channelPanel.addView(head);
    TextView hint=txt("گەڕان و هەڵبژاردنی کەناڵ",11,MUTED);hint.setGravity(Gravity.CENTER_VERTICAL);hint.setPadding(0,dp(7),0,dp(8));channelPanel.addView(hint);
    drawerSearch=new EditText(this);drawerSearch.setSingleLine(true);drawerSearch.setTextColor(WHITE);drawerSearch.setHintTextColor(MUTED);drawerSearch.setHint("گەڕان بە ناو یان جۆری کەناڵ...");drawerSearch.setTextSize(13);drawerSearch.setPadding(dp(13),dp(9),dp(13),dp(9));drawerSearch.setBackground(stroke(0xff1a1230,0xff6d329a,13));channelPanel.addView(drawerSearch,lp(-1,46));
    drawerFavFilter=txt("☆  تەنها دڵخوازەکان",12,ACCENT);drawerFavFilter.setGravity(Gravity.CENTER_VERTICAL);drawerFavFilter.setPadding(dp(4),dp(10),dp(4),dp(10));channelPanel.addView(drawerFavFilter);drawerFavFilter.setOnClickListener(v->{drawerFavoritesOnly=!drawerFavoritesOnly;drawerFavFilter.setText(drawerFavoritesOnly?"★  هەموو دڵخوازەکان":"☆  تەنها دڵخوازەکان");renderChannelDrawer();});
    ScrollView scroll=new ScrollView(this);channelList=new LinearLayout(this);channelList.setOrientation(1);scroll.addView(channelList);channelPanel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    drawerSearch.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){renderChannelDrawer();}public void afterTextChanged(android.text.Editable e){}});
    renderChannelDrawer();
    root.addView(drawerLayer,new FrameLayout.LayoutParams(-1,-1));
  }
  private void renderChannelDrawer(){
    if(channelList==null)return;
    channelList.removeAllViews();
    String query=drawerSearch==null?"":drawerSearch.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
    android.content.SharedPreferences prefs=getSharedPreferences("kurdish_tv",0);
    java.util.Set<String> favorites=prefs.getStringSet("favorites",new java.util.HashSet<>());
    int shown=0;
    for(int i=0;i<channelItems.length();i++){
      JSONObject item=channelItems.optJSONObject(i);if(item==null)continue;
      String id=item.optString("id"),name=item.optString("name","TV"),genre=item.optString("genre","Live");
      if(drawerFavoritesOnly&&!favorites.contains(id))continue;
      if(!name.toLowerCase(java.util.Locale.ROOT).contains(query)&&!genre.toLowerCase(java.util.Locale.ROOT).contains(query))continue;
      shown++;
      LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(5),dp(7),dp(5));
      row.setBackground(bg(id.equals(currentChannelId)?0xff42146a:0xff191329,14));
      LinearLayout.LayoutParams rowParams=lp(-1,58);rowParams.bottomMargin=dp(7);channelList.addView(row,rowParams);
      LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setGravity(Gravity.CENTER_VERTICAL);
      TextView channelName=txt((id.equals(currentChannelId)?"●  ":"")+name,14,id.equals(currentChannelId)?ACCENT:WHITE);channelName.setGravity(Gravity.CENTER_VERTICAL);channelName.setTypeface(null,Typeface.BOLD);
      TextView channelGenre=txt(genre+"  •  LIVE",10,MUTED);channelGenre.setGravity(Gravity.CENTER_VERTICAL);
      info.addView(channelName);info.addView(channelGenre);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
      TextView star=txt(favorites.contains(id)?"★":"☆",21,favorites.contains(id)?ACCENT:MUTED);row.addView(star,lp(40,44));
      star.setOnClickListener(v->{java.util.Set<String> updated=new java.util.HashSet<>(getSharedPreferences("kurdish_tv",0).getStringSet("favorites",new java.util.HashSet<>()));if(!updated.add(id))updated.remove(id);getSharedPreferences("kurdish_tv",0).edit().putStringSet("favorites",updated).apply();renderChannelDrawer();});
      info.setOnClickListener(v->selectChannel(id,name));
      row.setOnClickListener(v->selectChannel(id,name));
    }
    if(shown==0){TextView empty=txt(drawerFavoritesOnly?"هێشتا کەناڵێکی دڵخوازت نییە":"هیچ کەناڵێک نەدۆزرایەوە",12,MUTED);channelList.addView(empty,lp(-1,54));}
  }
  private void toggleChannelDrawer(){
    if(drawerLayer==null)return;
    drawerVisible=!drawerVisible;drawerLayer.setVisibility(drawerVisible?View.VISIBLE:View.GONE);
    if(drawerVisible){handler.removeCallbacks(hideRunnable);showControls();}
    else showControls();
  }
  private void selectChannel(String id,String name){
    if(id.equals(currentChannelId)){toggleChannelDrawer();return;}
    currentChannelId=id;channelTitle=name;
    if(drawerLayer!=null)drawerLayer.setVisibility(View.GONE);drawerVisible=false;
    if(topBar.getChildCount()>1&&topBar.getChildAt(1) instanceof LinearLayout){
      LinearLayout titleBox=(LinearLayout)topBar.getChildAt(1);
      if(titleBox.getChildCount()>0&&titleBox.getChildAt(0) instanceof TextView)((TextView)titleBox.getChildAt(0)).setText(name);
    }
    runtimeRefreshAttempted=false;relayAttempted=false;directAttempted=false;networkRetryCount=0;
    loading.setVisibility(View.VISIBLE);stateText.setVisibility(View.VISIBLE);
    if(!runtimeBase.isEmpty()&&!runtimeKey.isEmpty()){
      relayUrl=runtimeBase+"/stream/"+id+".ts?key="+runtimeKey;
      sourceApiUrl=runtimeBase+"/source/"+id+"?key="+runtimeKey;
      playRelay("گۆڕینی کەناڵ بۆ "+name);
    }else{
      stateText.setText("وەرگرتنی لینکی کەناڵ...");
      refreshRuntime(()->playRelay("گۆڕینی کەناڵ بۆ "+name),true);
    }
    showControls();
  }

  private void tuneRelative(int direction){
    if(channelItems==null||channelItems.length()==0)return;
    int current=-1;
    for(int i=0;i<channelItems.length();i++){
      JSONObject item=channelItems.optJSONObject(i);
      if(item!=null&&item.optString("id").equals(currentChannelId)){current=i;break;}
    }
    if(current<0)current=0;
    int next=(current+direction+channelItems.length())%channelItems.length();
    JSONObject item=channelItems.optJSONObject(next);
    if(item!=null)selectChannel(item.optString("id"),item.optString("name","Live TV"));
  }
  @Override public boolean onKeyDown(int keyCode,KeyEvent event){
    if(!drawerVisible){
      if(keyCode==KeyEvent.KEYCODE_CHANNEL_UP||keyCode==KeyEvent.KEYCODE_MEDIA_PREVIOUS||
         (keyCode==KeyEvent.KEYCODE_DPAD_UP&&!controlsVisible)){
        tuneRelative(1);showControls();return true;
      }
      if(keyCode==KeyEvent.KEYCODE_CHANNEL_DOWN||keyCode==KeyEvent.KEYCODE_MEDIA_NEXT||
         (keyCode==KeyEvent.KEYCODE_DPAD_DOWN&&!controlsVisible)){
        tuneRelative(-1);showControls();return true;
      }
      if(keyCode==KeyEvent.KEYCODE_DPAD_CENTER||keyCode==KeyEvent.KEYCODE_ENTER||keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){
        if(player!=null){if(player.isPlaying())player.pause();else player.play();showControls();}return true;
      }
    }
    return super.onKeyDown(keyCode,event);
  }

  private void hideSystemBars(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);}
  private void toggleControls(){if(controlsVisible){topBar.setVisibility(View.GONE);bottomPanel.setVisibility(View.GONE);extraPanel.setVisibility(View.GONE);controlsVisible=false;handler.removeCallbacks(hideRunnable);}else showControls();}
  private void showControls(){if(controlsLocked)return;topBar.setVisibility(View.VISIBLE);bottomPanel.setVisibility(View.VISIBLE);extraPanel.setVisibility(View.VISIBLE);controlsVisible=true;handler.removeCallbacks(hideRunnable);hideRunnable=()->{if(!controlsLocked&&!drawerVisible){topBar.setVisibility(View.GONE);bottomPanel.setVisibility(View.GONE);extraPanel.setVisibility(View.GONE);controlsVisible=false;}};handler.postDelayed(hideRunnable,6500);}
  private void startStream(){
    directAttempted=false;relayAttempted=false;runtimeRefreshAttempted=false;networkRetryCount=0;
    loading.setVisibility(View.VISIBLE);stateText.setVisibility(View.VISIBLE);
    if(relayUrl!=null&&!relayUrl.isEmpty())playRelay("دووبارە دەستپێکردنی پەخش");
    else refreshRuntime(()->playRelay("پەخش بە لینکی یەکگرتووی VLC"),true);
  }
  private void refreshRuntime(Runnable onSuccess,boolean showError){
    new Thread(()->{
      HttpURLConnection conn=null;
      try{
        URL url=new URL("https://raw.githubusercontent.com/quranofficial/tv-stream-manager/main/runtime.json?refresh="+System.currentTimeMillis());
        conn=(HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(12000);conn.setReadTimeout(12000);
        conn.setUseCaches(false);
        conn.setRequestProperty("Cache-Control","no-cache, no-store, max-age=0");
        conn.setRequestProperty("Pragma","no-cache");
        int code=conn.getResponseCode();
        if(code!=200)throw new Exception("نەتوانرا لینکی نوێی ستریم وەربگیرێت (HTTP "+code+")");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=conn.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}
        JSONObject config=new JSONObject(out.toString("UTF-8"));
        String base=config.optString("base_url","").trim().replaceAll("/+$","");
        String key=config.optString("access_key","").trim();
        URI parsed=new URI(base);
        String host=parsed.getHost();
        if(!"https".equalsIgnoreCase(parsed.getScheme())||host==null||
          !host.toLowerCase(java.util.Locale.ROOT).endsWith(".trycloudflare.com")||
          base.toLowerCase(java.util.Locale.ROOT).contains("undefined")||
          base.toLowerCase(java.util.Locale.ROOT).contains("null")||key.isEmpty())
          throw new Exception("runtime.json لینکی نادروستی تێدایە؛ تکایە بۆتەکە لە GitHub Actions دەستپێبکەرەوە.");
        runtimeBase=base;runtimeKey=key;
        getSharedPreferences("kurdish_tv",0).edit().putString("tv_runtime_base",base).putString("tv_runtime_key",key).apply();
        relayUrl=runtimeBase+"/stream/"+currentChannelId+".ts?key="+runtimeKey;
        sourceApiUrl=runtimeBase+"/source/"+currentChannelId+"?key="+runtimeKey;
        runOnUiThread(()->{if(!isFinishing()&&onSuccess!=null)onSuccess.run();});
      }catch(Exception e){
        String message=e.getMessage()==null?"هەڵە لە نوێکردنەوەی لینکی ستریم":e.getMessage();
        runOnUiThread(()->{
          if(isFinishing())return;
          loading.setVisibility(View.GONE);stateText.setVisibility(View.VISIBLE);
          stateText.setText("نەتوانرا لینکی نوێ وەربگیرێت");
          if(showError)new AlertDialog.Builder(this).setTitle("پەیوەندی بە ستریم").setMessage(message+"\\n\\nدڵنیابە Telegram TV Bot لە GitHub Actions چالاکە.").setPositiveButton("دووبارە هەوڵبدە", (d,w)->startStream()).setNegativeButton("داخستن",null).show();
        });
      }finally{if(conn!=null)conn.disconnect();}
    }).start();
  }
  private boolean isNetworkFailure(Throwable error){
    for(Throwable t=error;t!=null;t=t.getCause()){
      if(t instanceof UnknownHostException||t instanceof IOException)return true;
      String name=t.getClass().getSimpleName();
      if(name.contains("UnknownHost")||name.contains("ConnectException"))return true;
    }
    return false;
  }
  private void playDirect(String url){
    if(isFinishing())return;
    directAttempted=true;
    stateText.setText("پەخشی ڕاستەوخۆی v1a1 دەپشکنرێت...");
    playUrl(url);
  }
  private void playRelay(String reason){
    if(isFinishing()||relayAttempted)return;
    relayAttempted=true;
    stateText.setText(reason+" — VLC Relay");
    if(relayUrl==null||relayUrl.isEmpty()){
      loading.setVisibility(View.GONE);stateText.setText("هیچ لینکی پەخش بەردەست نییە");return;
    }
    playUrl(relayUrl);
  }
  private void playUrl(String url){
    activePlaybackUrl=url;
    if(networkRetryRunnable!=null){handler.removeCallbacks(networkRetryRunnable);networkRetryRunnable=null;}
    if(player!=null){player.release();player=null;}
    loading.setVisibility(View.VISIBLE);stateText.setVisibility(View.VISIBLE);playButton.setText("Ⅱ");
    DefaultHttpDataSource.Factory httpFactory=new DefaultHttpDataSource.Factory()
      .setUserAgent("Mozilla/5.0 TVStreamManager/2.0")
      .setConnectTimeoutMs(20000)
      .setReadTimeoutMs(30000)
      .setAllowCrossProtocolRedirects(true);
    LoadControl loadControl=new DefaultLoadControl.Builder()
      .setBufferDurationsMs(12000,45000,1500,5000)
      .setPrioritizeTimeOverSizeThresholds(true)
      .build();
    player=new ExoPlayer.Builder(this)
      .setLoadControl(loadControl)
      .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory))
      .build();
    playerView.setPlayer(player);
    MediaItem item=new MediaItem.Builder().setUri(url)
      .setMimeType(url.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")
        ? MimeTypes.APPLICATION_M3U8 : MimeTypes.VIDEO_MP2T)
      .build();
    if(url.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")){
      player.setMediaSource(new HlsMediaSource.Factory(httpFactory)
        .setAllowChunklessPreparation(false).createMediaSource(item));
    }else{
      player.setMediaItem(item);
    }
    player.addListener(new Player.Listener(){
      @Override public void onPlaybackStateChanged(int s){
        if(s==Player.STATE_BUFFERING){loading.setVisibility(View.VISIBLE);stateText.setVisibility(View.VISIBLE);stateText.setText("بارکردنی پەخش...");}
        else if(s==Player.STATE_READY){networkRetryCount=0;runtimeRefreshAttempted=false;loading.setVisibility(View.GONE);stateText.setVisibility(View.GONE);}
        else if(s==Player.STATE_ENDED){loading.setVisibility(View.GONE);stateText.setVisibility(View.GONE);}
      }
      @Override public void onPlayerError(PlaybackException e){
        if(directAttempted&&!relayAttempted){playRelay("پەخشی ڕاستەوخۆ کار نەکرد؛ گۆڕین بۆ Relay");}
        else if(isNetworkFailure(e)&&networkRetryCount<2){
          final String retryUrl=activePlaybackUrl;
          final int attempt=++networkRetryCount;
          long delay=1000L*(1L<<(attempt-1));
          loading.setVisibility(View.VISIBLE);stateText.setVisibility(View.VISIBLE);
          stateText.setText("ئینتەرنێت لاوازە؛ پەخش بەردەوام دەبێت... هەوڵی "+attempt+"/2");
          networkRetryRunnable=()->{if(!isFinishing()&&retryUrl.equals(activePlaybackUrl))playUrl(retryUrl);};
          handler.postDelayed(networkRetryRunnable,delay);
        }else if(!runtimeRefreshAttempted&&isNetworkFailure(e)){
          runtimeRefreshAttempted=true;relayAttempted=false;networkRetryCount=0;
          loading.setVisibility(View.VISIBLE);stateText.setVisibility(View.VISIBLE);
          stateText.setText("لینکی ستریم نوێ دەکرێتەوە...");
          refreshRuntime(()->playRelay("لینکی نوێ وەرگیرا؛ دووبارە پەخش دەکرێت"),true);
        }else{
          loading.setVisibility(View.GONE);stateText.setVisibility(View.VISIBLE);
          String detail=PlaybackException.getErrorCodeName(e.errorCode);
          Throwable cause=e.getCause();
          if(cause!=null&&cause.getMessage()!=null)detail+=": "+cause.getMessage();
          stateText.setText("پلەیەری ئەپ نەتوانی پەخش بکات. هەڵە: "+detail);
          showControls();
        }
      }
      @Override public void onIsPlayingChanged(boolean playing){playButton.setText(playing?"Ⅱ":"▶");}
    });
    player.prepare();player.play();
  }
  private void stopPlaybackCompletely(){
    handler.removeCallbacksAndMessages(null);
    networkRetryRunnable=null;
    activePlaybackUrl="";
    if(playerView!=null)playerView.setPlayer(null);
    if(player!=null){
      try{player.stop();player.clearMediaItems();}catch(Exception ignored){}
      player.release();player=null;
    }
  }
  @Override protected void onPause(){
    super.onPause();
    // Live audio must never continue after the user leaves the player/app.
    stopPlaybackCompletely();
  }
  @Override protected void onStop(){super.onStop();stopPlaybackCompletely();}
  @Override protected void onDestroy(){stopPlaybackCompletely();super.onDestroy();}
  @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)hideSystemBars();}
  @Override public void onBackPressed(){finish();}
}