package com.kurdish.tv;

import android.app.*;
import android.os.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {
  private ExoPlayer player;
  private PlayerView playerView;
  private TextView stateText, playButton, muteButton, lockButton, fitButton, liveBadge;
  private ProgressBar loading;
  private LinearLayout topBar, bottomPanel, extraPanel;
  private SeekBar volumeSeek, brightnessSeek;
  private Handler handler=new Handler(Looper.getMainLooper());
  private Runnable hideRunnable;
  private String streamUrl, channelTitle;
  private boolean muted=false, controlsLocked=false, controlsVisible=true;
  private int fitMode=AspectRatioFrameLayout.RESIZE_MODE_FIT;
  private float oldBrightness=-1f;
  private final int WHITE=0xfff5f7fb, MUTED=0xffa5b4c8, ACCENT=0xff27d7c3;

  int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  GradientDrawable stroke(int color,int line,int radius){GradientDrawable d=bg(color,radius);d.setStroke(dp(1),line);return d;}
  TextView txt(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);return t;}
  TextView control(String value){TextView t=txt(value,19,WHITE);t.setBackground(stroke(0xdd172438,0x665b718d,16));return t;}
  LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    channelTitle=getIntent().getStringExtra("title");streamUrl=getIntent().getStringExtra("url");
    if(channelTitle==null)channelTitle="Live TV";if(streamUrl==null)streamUrl="";
    getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    hideSystemBars();
    FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
    playerView=new PlayerView(this);playerView.setUseController(false);playerView.setShutterBackgroundColor(Color.BLACK);playerView.setKeepContentOnPlayerReset(true);playerView.setResizeMode(fitMode);
    root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
    FrameLayout shade=new FrameLayout(this);shade.setBackgroundColor(0x44000000);root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout overlay=new LinearLayout(this);overlay.setOrientation(1);
    FrameLayout.LayoutParams op=new FrameLayout.LayoutParams(-1,-1);root.addView(overlay,op);
    topBar=new LinearLayout(this);topBar.setGravity(Gravity.CENTER_VERTICAL);topBar.setPadding(dp(18),dp(12),dp(18),dp(8));
    TextView back=control("‹");topBar.addView(back,lp(44,44));back.setOnClickListener(v->finish());
    LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(1);titleBox.setPadding(dp(12),0,0,0);
    TextView title=txt(channelTitle,17,WHITE);title.setGravity(Gravity.CENTER_VERTICAL);title.setTypeface(null,Typeface.BOLD);
    TextView sub=txt("KURDISH TV  •  LIVE STREAM",9,0xffc0cbd9);sub.setGravity(Gravity.CENTER_VERTICAL);
    titleBox.addView(title);titleBox.addView(sub);topBar.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
    liveBadge=txt("●  LIVE",11,ACCENT);liveBadge.setPadding(dp(12),dp(8),dp(12),dp(8));liveBadge.setBackground(bg(0xdd123a37,24));topBar.addView(liveBadge);
    overlay.addView(topBar,lp(-1,62));
    View spacer=new View(this);overlay.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
    LinearLayout center=new LinearLayout(this);center.setGravity(Gravity.CENTER);center.setOrientation(1);
    loading=new ProgressBar(this);center.addView(loading,lp(42,42));
    stateText=txt("پەیوەندی بە کەناڵ...",13,WHITE);LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-2,-2);st.topMargin=dp(10);center.addView(stateText,st);
    overlay.addView(center,new LinearLayout.LayoutParams(-1,-2));
    View spacer2=new View(this);overlay.addView(spacer2,new LinearLayout.LayoutParams(1,0,1));
    extraPanel=new LinearLayout(this);extraPanel.setOrientation(1);extraPanel.setPadding(dp(20),dp(8),dp(20),dp(8));extraPanel.setBackgroundColor(0xdd091321);
    LinearLayout volRow=new LinearLayout(this);volRow.setGravity(Gravity.CENTER_VERTICAL);volRow.addView(txt("🔊",16,WHITE),lp(35,34));
    volumeSeek=new SeekBar(this);volumeSeek.setMax(100);volumeSeek.setProgress(100);volRow.addView(volumeSeek,new LinearLayout.LayoutParams(0,dp(34),1));extraPanel.addView(volRow);
    LinearLayout brightRow=new LinearLayout(this);brightRow.setGravity(Gravity.CENTER_VERTICAL);brightRow.addView(txt("☼",20,WHITE),lp(35,34));
    brightnessSeek=new SeekBar(this);brightnessSeek.setMax(100);brightnessSeek.setProgress(70);brightRow.addView(brightnessSeek,new LinearLayout.LayoutParams(0,dp(34),1));extraPanel.addView(brightRow);
    overlay.addView(extraPanel,lp(-1,-2));
    volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){if(player!=null){player.setVolume(p/100f);muted=p==0;muteButton.setText(muted?"×":"🔊");}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
    brightnessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=Math.max(.02f,p/100f);getWindow().setAttributes(a);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
    bottomPanel=new LinearLayout(this);bottomPanel.setGravity(Gravity.CENTER_VERTICAL);bottomPanel.setPadding(dp(18),dp(10),dp(18),dp(14));bottomPanel.setBackgroundColor(0xdd091321);
    playButton=control("Ⅱ");bottomPanel.addView(playButton,lp(50,46));playButton.setOnClickListener(v->{if(player==null)return;if(player.isPlaying())player.pause();else player.play();});
    TextView label=txt("پەخشی ڕاستەوخۆ",12,0xffc0cbd9);label.setGravity(Gravity.CENTER_VERTICAL);label.setPadding(dp(13),0,0,0);bottomPanel.addView(label,new LinearLayout.LayoutParams(0,-2,1));
    muteButton=control("🔊");bottomPanel.addView(muteButton,lp(48,46));muteButton.setOnClickListener(v->{muted=!muted;if(player!=null)player.setVolume(muted?0f:(volumeSeek.getProgress()/100f));muteButton.setText(muted?"×":"🔊");});
    fitButton=control("▣");LinearLayout.LayoutParams fp=lp(48,46);fp.leftMargin=dp(8);bottomPanel.addView(fitButton,fp);fitButton.setOnClickListener(v->{fitMode=fitMode==AspectRatioFrameLayout.RESIZE_MODE_FIT?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:fitMode==AspectRatioFrameLayout.RESIZE_MODE_ZOOM?AspectRatioFrameLayout.RESIZE_MODE_FILL:AspectRatioFrameLayout.RESIZE_MODE_FIT;playerView.setResizeMode(fitMode);fitButton.setText(fitMode==AspectRatioFrameLayout.RESIZE_MODE_FIT?"FIT":fitMode==AspectRatioFrameLayout.RESIZE_MODE_ZOOM?"ZOOM":"FILL");showControls();});
    TextView retry=control("↻");LinearLayout.LayoutParams rp=lp(48,46);rp.leftMargin=dp(8);bottomPanel.addView(retry,rp);retry.setOnClickListener(v->startStream());
    lockButton=control("🔓");LinearLayout.LayoutParams lk=lp(48,46);lk.leftMargin=dp(8);bottomPanel.addView(lockButton,lk);lockButton.setOnClickListener(v->{controlsLocked=!controlsLocked;lockButton.setText(controlsLocked?"🔒":"🔓");if(controlsLocked){extraPanel.setVisibility(View.GONE);topBar.setVisibility(View.GONE);bottomPanel.setVisibility(View.GONE);controlsVisible=false;}else showControls();});
    overlay.addView(bottomPanel,lp(-1,68));
    playerView.setOnClickListener(v->{if(controlsLocked){controlsLocked=false;lockButton.setText("🔓");showControls();}else toggleControls();});
    setContentView(root);startStream();showControls();
  }
  private void hideSystemBars(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);}
  private void toggleControls(){if(controlsVisible){topBar.setVisibility(View.GONE);bottomPanel.setVisibility(View.GONE);extraPanel.setVisibility(View.GONE);controlsVisible=false;handler.removeCallbacks(hideRunnable);}else showControls();}
  private void showControls(){if(controlsLocked)return;topBar.setVisibility(View.VISIBLE);bottomPanel.setVisibility(View.VISIBLE);extraPanel.setVisibility(View.VISIBLE);controlsVisible=true;handler.removeCallbacks(hideRunnable);hideRunnable=()->{if(!controlsLocked){topBar.setVisibility(View.GONE);bottomPanel.setVisibility(View.GONE);extraPanel.setVisibility(View.GONE);controlsVisible=false;}};handler.postDelayed(hideRunnable,6500);}
  private void startStream(){
    if(streamUrl.isEmpty()){stateText.setText("بەستەری پەخش بەردەست نییە");return;}
    if(player!=null){player.release();player=null;}loading.setVisibility(View.VISIBLE);stateText.setText("پەیوەندی بە کەناڵ...");playButton.setText("Ⅱ");
    player=new ExoPlayer.Builder(this).build();playerView.setPlayer(player);player.setMediaItem(MediaItem.fromUri(streamUrl));
    player.addListener(new Player.Listener(){
      @Override public void onPlaybackStateChanged(int s){if(s==Player.STATE_BUFFERING){loading.setVisibility(View.VISIBLE);stateText.setText("بارکردنی پەخش...");}else if(s==Player.STATE_READY){loading.setVisibility(View.GONE);stateText.setText("● پەخش دەکرێت");}else if(s==Player.STATE_ENDED){loading.setVisibility(View.GONE);stateText.setText("پەخش کۆتایی هات");}}
      @Override public void onPlayerError(PlaybackException e){loading.setVisibility(View.GONE);stateText.setText("پەخش نەکرا — دووبارە هەوڵ بدە");showControls();}
      @Override public void onIsPlayingChanged(boolean playing){playButton.setText(playing?"Ⅱ":"▶");}
    });player.prepare();player.play();
  }
  @Override protected void onStop(){super.onStop();handler.removeCallbacksAndMessages(null);if(player!=null){player.release();player=null;}}
  @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)hideSystemBars();}
  @Override public void onBackPressed(){finish();}
}