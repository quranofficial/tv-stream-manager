package com.kurdish.tv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
  final int BG=0xff08111f, CARD=0xff121f32, MUTED=0xff91a4bd, ACCENT=0xff27d7c3, WHITE=0xfff4f7fb;
  JSONArray channels=new JSONArray();
  SharedPreferences prefs;
  LinearLayout root, list;
  EditText search;
  String genreFilter="هەمووی";
  boolean onlyFav=false;
  Set<String> fav=new HashSet<>();

  int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  GradientDrawable gradient(int a,int b,int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});d.setCornerRadius(dp(radius));return d;}
  TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
  TextView pill(String s){TextView t=label(s,12,ACCENT);t.setPadding(dp(10),dp(5),dp(10),dp(5));t.setBackground(shape(0xff123a42,30));return t;}
  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);prefs=getSharedPreferences("kurdish_tv",0);fav.addAll(prefs.getStringSet("favorites",new HashSet<>()));try{InputStream in=getAssets().open("channels.json");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);channels=new JSONObject(out.toString("UTF-8")).getJSONArray("data");}catch(Exception e){}showHome();showLastCrash();}
  GradientDrawable strokeHome(int color,int line,int radius){GradientDrawable d=shape(color,radius);d.setStroke(dp(1),line);return d;}
  void showLastCrash(){String report=getSharedPreferences("crash_report",0).getString("last_crash",null);if(report==null||report.isEmpty())return;new AlertDialog.Builder(this).setTitle("Crash report").setMessage("ئەمە هۆکاری داخستنی جارەکەی پێشووترە. تکایە Copy بکە و بۆم بینێرە.\\n\\n"+report).setPositiveButton("Copy", (d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("Crash report",report));Toast.makeText(this,"Crash report کۆپی کرا",Toast.LENGTH_SHORT).show();}).setNegativeButton("داخستن",null).show();}
  TextView navItem(String icon,String title,boolean active){
    TextView t=label(icon+"\n"+title,11,active?ACCENT:MUTED);t.setGravity(Gravity.CENTER);t.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);return t;
  }
  TextView textButton(String s,Runnable r){TextView t=label(s,14,WHITE);t.setGravity(Gravity.CENTER);t.setPadding(dp(14),dp(10),dp(14),dp(10));t.setBackground(shape(CARD,14));t.setOnClickListener(v->r.run());return t;}
  void showHome(){
    root=new LinearLayout(this);root.setOrientation(1);root.setBackground(gradient(0xff07101d,0xff10243a,0));setContentView(root);
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(20),dp(20),dp(20),dp(14));
    TextView logo=label("▣",28,ACCENT);logo.setGravity(Gravity.CENTER);logo.setBackground(shape(0xff123a42,14));top.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));
    LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(1);titleBox.setPadding(dp(10),0,0,0);
    TextView title=label("KURDISH TV",21,WHITE);title.setTypeface(null,Typeface.BOLD);titleBox.addView(title);titleBox.addView(label("LIVE • کەناڵە کوردییەکان",10,MUTED));top.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
    TextView settings=label("⚙",22,WHITE);settings.setGravity(Gravity.CENTER);settings.setBackground(shape(CARD,14));top.addView(settings,new LinearLayout.LayoutParams(dp(44),dp(44)));settings.setOnClickListener(v->relaySettings());
    root.addView(top);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(16),dp(10),dp(16),dp(28));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(10),dp(8),dp(10),dp(8));nav.setBackgroundColor(0xff0a1524);
    TextView navHome=navItem("⌂","سەرەکی",true);TextView navChannels=navItem("▦","کەناڵەکان",false);TextView navFav=navItem("★","دڵخوازەکان",false);
    nav.addView(navHome,new LinearLayout.LayoutParams(0,dp(54),1));nav.addView(navChannels,new LinearLayout.LayoutParams(0,dp(54),1));nav.addView(navFav,new LinearLayout.LayoutParams(0,dp(54),1));root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
    navHome.setOnClickListener(v->{onlyFav=false;genreFilter="هەمووی";if(search!=null)search.setText("");scroll.smoothScrollTo(0,0);});
    navChannels.setOnClickListener(v->{onlyFav=false;showHome();root.post(()->{});});
    navFav.setOnClickListener(v->{onlyFav=true;showHome();});
    LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);hero.setPadding(dp(20),dp(22),dp(20),dp(22));hero.setBackground(gradient(0xff087f79,0xff183451,24));
    hero.addView(pill("●  پەخشی ڕاستەوخۆ"));
    TextView welcome=label("گەشتێکی نوێ بۆ\nجیهانی تەلەڤیزیۆن",25,WHITE);welcome.setTypeface(null,Typeface.BOLD);welcome.setPadding(0,dp(12),0,dp(5));hero.addView(welcome);
    hero.addView(label(channels.length()+" کەناڵ بەردەستن • بەبێ Login",13,0xffc9e9e8));
    LinearLayout stats=new LinearLayout(this);stats.setPadding(0,dp(14),0,0);stats.setGravity(Gravity.CENTER_VERTICAL);
    TextView stat1=label("◉  "+channels.length()+" کەناڵ",12,WHITE);stat1.setPadding(dp(12),dp(9),dp(12),dp(9));stat1.setBackground(shape(0x334fffff,22));stats.addView(stat1);
    TextView stat2=label("  ★ دڵخوازەکانت",12,WHITE);stat2.setPadding(dp(12),dp(9),dp(12),dp(9));stat2.setBackground(shape(0x334fffff,22));LinearLayout.LayoutParams s2=new LinearLayout.LayoutParams(-2,-2);s2.leftMargin=dp(8);stats.addView(stat2,s2);hero.addView(stats);page.addView(hero);
    LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);searchRow.setPadding(0,dp(16),0,dp(12));
    search=new EditText(this);search.setSingleLine(true);search.setTextColor(WHITE);search.setTextDirection(View.TEXT_DIRECTION_RTL);search.setHintTextColor(MUTED);search.setHint("گەڕان بە ناوی کەناڵ...");search.setTextSize(14);search.setPadding(dp(14),dp(10),dp(14),dp(10));search.setBackground(strokeHome(CARD,0xff263b55,15));searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
    TextView go=label("⌕",27,ACCENT);go.setGravity(Gravity.CENTER);searchRow.addView(go,new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(searchRow);
    LinearLayout filters=new LinearLayout(this);filters.setOrientation(0);filters.setPadding(0,0,0,dp(12));
    HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);String[] genres={"هەمووی","News","Sports","Family","Islamic","Kids","Movies","Music","Documentary"};
    for(String g:genres){TextView chip=label(g,12,g.equals(genreFilter)?BG:WHITE);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(13),dp(9),dp(13),dp(9));chip.setBackground(shape(g.equals(genreFilter)?ACCENT:CARD,24));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(38));cp.setMargins(0,0,dp(7),0);filters.addView(chip,cp);chip.setOnClickListener(v->{genreFilter=g;showHome();});}
    hs.addView(filters);page.addView(hs);
    LinearLayout section=new LinearLayout(this);section.setGravity(Gravity.CENTER_VERTICAL);TextView sec=label(onlyFav?"دڵخوازەکان":"کەناڵەکان",18,WHITE);sec.setTypeface(null,Typeface.BOLD);section.addView(sec,new LinearLayout.LayoutParams(0,-2,1));TextView favBtn=label(onlyFav?"★ هەموو":"☆ دڵخوازەکان",12,ACCENT);section.addView(favBtn);favBtn.setOnClickListener(v->{onlyFav=!onlyFav;showHome();});page.addView(section);
    list=new LinearLayout(this);list.setOrientation(1);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);page.addView(list,lp);
    Runnable filter=()->renderChannels();search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){renderChannels();}public void afterTextChanged(android.text.Editable e){}});
    renderChannels();
    TextView footer=label("Kurdish TV  •  Live Player",11,MUTED);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(22),0,0);page.addView(footer);
  }
  void renderChannels(){if(list==null)return;list.removeAllViews();String q=search==null?"":search.getText().toString().toLowerCase(Locale.ROOT);int shown=0;
    for(int i=0;i<channels.length();i++){JSONObject c=channels.optJSONObject(i);if(c==null)continue;String id=c.optString("id"),name=c.optString("name"),genre=c.optString("genre","");if(onlyFav&&!fav.contains(id))continue;if(!genreFilter.equals("هەمووی")&&!genre.equalsIgnoreCase(genreFilter))continue;if(!name.toLowerCase(Locale.ROOT).contains(q)&&!genre.toLowerCase(Locale.ROOT).contains(q))continue;shown++;
      LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(12),dp(10),dp(10),dp(10));card.setBackground(gradient(0xff1a2b43,0xff111d30,18));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(88));cp.setMargins(0,0,0,dp(9));list.addView(card,cp);
      FrameLayout logoBox=new FrameLayout(this);logoBox.setBackground(shape(0xff29435e,16));TextView initials=label(name.isEmpty()?"TV":name.substring(0,Math.min(2,name.length())).toUpperCase(Locale.ROOT),18,ACCENT);initials.setGravity(Gravity.CENTER);logoBox.addView(initials,new FrameLayout.LayoutParams(-1,-1));card.addView(logoBox,new LinearLayout.LayoutParams(dp(58),dp(58)));
      String logo=c.optString("logo","");if(!logo.equals("null")&&!logo.isEmpty())loadLogo(logo,logoBox,initials);
      LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setPadding(dp(12),0,dp(5),0);TextView nm=label(name,15,WHITE);nm.setTypeface(null,Typeface.BOLD);info.addView(nm);info.addView(label(genre+"  •  HD",11,MUTED));card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
      TextView star=label(fav.contains(id)?"★":"☆",23,fav.contains(id)?ACCENT:MUTED);star.setGravity(Gravity.CENTER);card.addView(star,new LinearLayout.LayoutParams(dp(34),dp(48)));star.setOnClickListener(v->{if(!fav.add(id))fav.remove(id);prefs.edit().putStringSet("favorites",new HashSet<>(fav)).apply();renderChannels();});
      TextView play=label("▶",16,BG);play.setGravity(Gravity.CENTER);play.setBackground(shape(ACCENT,14));card.addView(play,new LinearLayout.LayoutParams(dp(42),dp(42)));play.setOnClickListener(v->playChannel(c));
      card.setOnClickListener(v->playChannel(c));
    }
    if(shown==0){TextView empty=label("هیچ کەناڵێک نەدۆزرایەوە",14,MUTED);empty.setGravity(Gravity.CENTER);list.addView(empty);}
  }
  void loadLogo(String url,FrameLayout box,TextView fallback){new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(6000);android.graphics.Bitmap bm=android.graphics.BitmapFactory.decodeStream(c.getInputStream());c.disconnect();if(bm!=null)runOnUiThread(()->{ImageView iv=new ImageView(this);iv.setImageBitmap(bm);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);box.removeAllViews();box.addView(iv,new FrameLayout.LayoutParams(-1,-1));});}catch(Exception ignored){}}).start();}
  void relaySettings(){
    new AlertDialog.Builder(this)
      .setTitle("پەیوەندی بە ستریم")
      .setMessage("ئەپەکە بە شێوەی ئۆتۆماتیکی نوێترین لینکی Live لە GitHub Actions وەردەگرێت. پێویست ناکات هیچ لینکێک بە دەستی دابنێیت.\n\nتکایە دڵنیابە Telegram Bot workflow چالاکە.")
      .setPositiveButton("باشە",null).show();
  }
  void playChannel(JSONObject channel){
    String id=channel.optString("id");
    String title=channel.optString("name");
    Toast.makeText(this,"وەرگرتنی لینکی Live بۆ "+title+"...",Toast.LENGTH_SHORT).show();
    new Thread(()->{
      HttpURLConnection conn=null;
      try{
        URL configUrl=new URL("https://raw.githubusercontent.com/quranofficial/tv-stream-manager/main/runtime.json?nocache="+System.currentTimeMillis());
        conn=(HttpURLConnection)configUrl.openConnection();
        conn.setConnectTimeout(12000);conn.setReadTimeout(12000);
        conn.setRequestProperty("Cache-Control","no-cache");
        int code=conn.getResponseCode();
        if(code!=200)throw new Exception("لینکی Live ئامادە نییە. سەرەتا Telegram TV Bot لە GitHub Actions Run بکە.");
        ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream in=conn.getInputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
        JSONObject config=new JSONObject(out.toString("UTF-8"));
        String base=config.optString("base_url","").replaceAll("/+$","");
        String key=config.optString("access_key","");
        if(base.isEmpty()||key.isEmpty())throw new Exception("زانیاریی ستریم تەواو نییە؛ Workflow دووبارە Run بکە.");
        String streamUrl=base+"/stream/"+id+".ts?key="+key;
        runOnUiThread(()->{
          Intent intent=new Intent(MainActivity.this,PlayerActivity.class);
          intent.putExtra("title",title);
          intent.putExtra("url",streamUrl);
          intent.putExtra("source_api_url",base+"/source/"+id+"?key="+key);
          intent.putExtra("relay_url",streamUrl);
          intent.putExtra("runtime_base",base);
          intent.putExtra("runtime_key",key);
          intent.putExtra("channel_id",id);
          intent.putExtra("channels_json",channels.toString());
          startActivity(intent);
        });
      }catch(Exception e){String message=e.getMessage()==null?"هەڵە لە پەیوەندی بە ستریم":e.getMessage();runOnUiThread(()->new AlertDialog.Builder(MainActivity.this).setTitle("پەخش ئامادە نییە").setMessage(message+"\n\nGitHub → Actions → Telegram TV Bot → Run workflow").setPositiveButton("باشە",null).show());}
      finally{if(conn!=null)conn.disconnect();}
    }).start();
  }
}
