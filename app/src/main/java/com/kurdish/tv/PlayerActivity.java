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
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {
  private ExoPlayer player;
  private PlayerView playerView;
  private TextView stateText, playButton, muteButton;
  private ProgressBar loading;
  private String streamUrl, channelTitle;
  private boolean muted=false;
  private final int WHITE=0xfff5f7fb, MUTED=0xffa5b4c8, ACCENT=0xff27d7c3, PANEL=0xff101c2e;

  int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  TextView txt(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);return t;}
  TextView control(String value){TextView t=txt(value,19,WHITE);t.setBackground(bg(0xff203149,16));return t;}
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    channelTitle=getIntent().getStringExtra("title");
    streamUrl=getIntent().getStringExtra("url");
    if(channelTitle==null)channelTitle="Live TV";
    if(streamUrl==null)streamUrl="";
    getWindow().setStatusBarColor(Color.BLACK);
    getWindow().setNavigationBarColor(Color.BLACK);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xff05080e);

    LinearLayout header=new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(18),dp(10),dp(18),dp(10));
    header.setBackgroundColor(0xff0c1524);
    TextView back=control("‹");
    header.addView(back,new LinearLayout.LayoutParams(dp(42),dp(42)));
    back.setOnClickListener(v->finish());
    LinearLayout titleBox=new LinearLayout(this);
    titleBox.setOrientation(LinearLayout.VERTICAL);
    titleBox.setPadding(dp(13),0,0,0);
    TextView title=txt(channelTitle,16,WHITE);title.setGravity(Gravity.CENTER_VERTICAL);title.setTypeface(null,Typeface.BOLD);
    TextView subtitle=txt("KURDISH TV  •  LIVE BROADCAST",9,MUTED);subtitle.setGravity(Gravity.CENTER_VERTICAL);
    titleBox.addView(title);titleBox.addView(subtitle);
    header.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
    TextView live=txt("●  LIVE",11,ACCENT);live.setPadding(dp(12),dp(8),dp(12),dp(8));live.setBackground(bg(0xff123a37,20));
    header.addView(live);
    root.addView(header,new LinearLayout.LayoutParams(-1,dp(62)));

    FrameLayout stage=new FrameLayout(this);
    stage.setBackgroundColor(Color.BLACK);
    playerView=new PlayerView(this);
    playerView.setUseController(false);
    playerView.setShutterBackgroundColor(Color.BLACK);
    playerView.setKeepContentOnPlayerReset(true);
    stage.addView(playerView,new FrameLayout.LayoutParams(-1,-1));

    LinearLayout overlay=new LinearLayout(this);
    overlay.setOrientation(LinearLayout.VERTICAL);
    overlay.setGravity(Gravity.CENTER);
    overlay.setBackgroundColor(0x55000000);
    loading=new ProgressBar(this);
    overlay.addView(loading,new LinearLayout.LayoutParams(dp(42),dp(42)));
    stateText=txt("پەیوەندی بە کەناڵ...",13,WHITE);
    LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-2,-2);stp.topMargin=dp(10);overlay.addView(stateText,stp);
    stage.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
    root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));

    LinearLayout controls=new LinearLayout(this);
    controls.setGravity(Gravity.CENTER_VERTICAL);
    controls.setPadding(dp(18),dp(10),dp(18),dp(10));
    controls.setBackgroundColor(0xff0c1524);
    playButton=control("Ⅱ");
    controls.addView(playButton,new LinearLayout.LayoutParams(dp(50),dp(46)));
    playButton.setOnClickListener(v->{if(player==null)return;if(player.isPlaying()){player.pause();playButton.setText("▶");}else{player.play();playButton.setText("Ⅱ");}});
    TextView channel=txt("پەخشی ڕاستەوخۆ",12,MUTED);channel.setGravity(Gravity.CENTER_VERTICAL);channel.setPadding(dp(14),0,0,0);
    controls.addView(channel,new LinearLayout.LayoutParams(0,-2,1));
    muteButton=control("◖");
    controls.addView(muteButton,new LinearLayout.LayoutParams(dp(48),dp(46)));
    muteButton.setOnClickListener(v->{muted=!muted;if(player!=null)player.setVolume(muted?0f:1f);muteButton.setText(muted?"×":"◖");});
    TextView retry=control("↻");
    LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(48),dp(46));rp.leftMargin=dp(9);controls.addView(retry,rp);
    retry.setOnClickListener(v->startStream());
    root.addView(controls,new LinearLayout.LayoutParams(-1,dp(68)));
    setContentView(root);
    startStream();
  }

  private void startStream(){
    if(streamUrl.isEmpty()){stateText.setText("بەستەری پەخش بەردەست نییە");return;}
    if(player!=null){player.release();player=null;}
    loading.setVisibility(View.VISIBLE);
    stateText.setText("پەیوەندی بە کەناڵ...");
    playButton.setText("Ⅱ");
    player=new ExoPlayer.Builder(this).build();
    playerView.setPlayer(player);
    player.setMediaItem(MediaItem.fromUri(streamUrl));
    player.addListener(new Player.Listener(){
      @Override public void onPlaybackStateChanged(int state){
        if(state==Player.STATE_BUFFERING){loading.setVisibility(View.VISIBLE);stateText.setText("بارکردنی پەخش...");}
        else if(state==Player.STATE_READY){loading.setVisibility(View.GONE);stateText.setText("● پەخش دەکرێت");}
        else if(state==Player.STATE_ENDED){loading.setVisibility(View.GONE);stateText.setText("پەخش کۆتایی هات");}
      }
      @Override public void onPlayerError(PlaybackException e){loading.setVisibility(View.GONE);stateText.setText("پەخش نەکرا — دوگمەی دووبارە هەوڵدان دابگرە");playButton.setText("▶");}
      @Override public void onIsPlayingChanged(boolean playing){playButton.setText(playing?"Ⅱ":"▶");}
    });
    player.prepare();
    player.play();
  }
  @Override protected void onStop(){super.onStop();if(player!=null){player.release();player=null;}}
  @Override public void onBackPressed(){finish();}
}
