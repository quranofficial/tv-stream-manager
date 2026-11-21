package com.kurdish.tv;

import android.app.*;
import android.os.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {
  ExoPlayer player; PlayerView view; TextView status;
  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    String title=getIntent().getStringExtra("title"),url=getIntent().getStringExtra("url");
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.BLACK);
    TextView heading=new TextView(this);heading.setText("● LIVE   "+title+"                       ✕");heading.setTextColor(Color.WHITE);heading.setTextSize(16);heading.setPadding(20,12,20,12);heading.setOnClickListener(v->finish());root.addView(heading);
    view=new PlayerView(this);view.setUseController(true);view.setControllerAutoShow(true);root.addView(view,new LinearLayout.LayoutParams(-1,0,1));
    status=new TextView(this);status.setText("پەخشکردن دەست پێدەکات...");status.setTextColor(0xff9caec4);status.setGravity(Gravity.CENTER);status.setPadding(8,6,8,8);root.addView(status);setContentView(root);
    player=new ExoPlayer.Builder(this).build();view.setPlayer(player);player.setMediaItem(MediaItem.fromUri(url));player.prepare();player.play();
    player.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_BUFFERING)status.setText("چاوەڕێی پەخش...");else if(state==Player.STATE_READY)status.setText("● پەخش دەکرێت");else if(state==Player.STATE_ENDED)status.setText("پەخش کۆتایی هات");}@Override public void onPlayerError(PlaybackException e){status.setText("هەڵەی پەخش: "+e.getErrorCodeName());}});
  }
  @Override protected void onStop(){super.onStop();if(player!=null){player.release();player=null;}}
}
