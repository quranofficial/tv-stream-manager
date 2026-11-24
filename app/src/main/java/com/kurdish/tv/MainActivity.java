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
import android.util.LruCache;
import java.util.concurrent.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
  final int BG=0xff080713, CARD=0xff19142b, MUTED=0xffa99fbd, ACCENT=0xffb12cff, WHITE=0xfff7f3ff;
  private static final ExecutorService logoExecutor=Executors.newFixedThreadPool(3);
  private static final Set<String> logoLoads=java.util.Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());
  private static final LruCache<String,android.graphics.Bitmap> logoCache=new LruCache<String,android.graphics.Bitmap>(6*1024*1024){
    @Override protected int sizeOf(String key,android.graphics.Bitmap value){return value.getByteCount();}
  };
  JSONArray channels=new JSONArray();
  SharedPreferences prefs;
  LinearLayout root, list;
  EditText search;
  String genreFilter="هەمووی";
  boolean onlyFav=false;
  boolean jumpToChannels=false;
  Set<String> fav=new HashSet<>();
  String accessToken="", deviceId="";
  static final String SUPABASE_URL="https://herrkhvvfmbczbcebrci.supabase.co";
  static final String SUPABASE_KEY="sb_publishable_KS1XiyGUxrxjLJ_3aF426g_SZ-XsUQW";

  int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  GradientDrawable gradient(int a,int b,int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});d.setCornerRadius(dp(radius));return d;}
  TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
  TextView pill(String s){TextView t=label(s,12,ACCENT);t.setPadding(dp(10),dp(5),dp(10),dp(5));t.setBackground(shape(0xff35104f,30));return t;}
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);
    prefs=getSharedPreferences("kurdish_tv",0);fav.addAll(prefs.getStringSet("favorites",new HashSet<>()));
    deviceId=prefs.getString("tv_device_id","");
    if(deviceId.isEmpty()){deviceId=UUID.randomUUID().toString();prefs.edit().putString("tv_device_id",deviceId).apply();}
    showLastCrash();
    loadInitialState();
    prefetchRuntime();
  }
  void showSplash(){
    LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setGravity(Gravity.CENTER);
    page.setPadding(dp(28),dp(24),dp(28),dp(24));page.setBackground(gradient(0xff080713,0xff211039,0));
    FrameLayout mark=new FrameLayout(this);mark.setBackground(gradient(0xff40136c,0xff160d2b,30));
    ImageView logo=new ImageView(this);logo.setImageResource(com.kurdish.tv.R.drawable.ic_kurdish_tv_logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    FrameLayout.LayoutParams logoP=new FrameLayout.LayoutParams(dp(92),dp(92),Gravity.CENTER);mark.addView(logo,logoP);
    LinearLayout.LayoutParams markP=new LinearLayout.LayoutParams(dp(132),dp(132));markP.bottomMargin=dp(22);page.addView(mark,markP);
    TextView brand=label("KURDISH TV+",27,WHITE);brand.setTypeface(null,Typeface.BOLD);brand.setGravity(Gravity.CENTER);page.addView(brand);
    TextView sub=label("PREMIUM LIVE TELEVISION",10,0xffc7a1ef);sub.setGravity(Gravity.CENTER);sub.setLetterSpacing(.18f);
    LinearLayout.LayoutParams subP=new LinearLayout.LayoutParams(-1,-2);subP.topMargin=dp(7);page.addView(sub,subP);
    ProgressBar progress=new ProgressBar(this);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACCENT));
    LinearLayout.LayoutParams progressP=new LinearLayout.LayoutParams(dp(42),dp(42));progressP.topMargin=dp(30);page.addView(progress,progressP);
    TextView status=label("ئامادەکردنی کەناڵەکان و پەیوەندی...",12,MUTED);status.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams statusP=new LinearLayout.LayoutParams(-1,-2);statusP.topMargin=dp(12);page.addView(status,statusP);
    setContentView(page);
  }
  void loadInitialState(){
    showSplash();
    String saved=prefs.getString("tv_session_token","");
    new Thread(()->{
      boolean channelsOk=false,authorized=saved.isEmpty(),networkError=false;
      try(InputStream in=getAssets().open("channels.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
        channels=new JSONObject(out.toString("UTF-8")).getJSONArray("data");channelsOk=true;
      }catch(Exception ignored){}
      if(!saved.isEmpty()){
        try{accessRequest("validate",null,saved);authorized=true;}
        catch(Exception e){networkError=true;}
      }
      final boolean ready=channelsOk,allowed=authorized,offline=networkError;
      runOnUiThread(()->{
        if(isFinishing())return;
        if(!ready){showLogin("نەتوانرا زانیاریی کەناڵەکان باربکرێت. تکایە دووبارە هەوڵبدە.");return;}
        if(allowed){accessToken=saved;showHome();syncChannelCatalog();}
        else if(offline){accessToken=saved;showLogin("پەیوەندی بە سێرڤەر نەکرا. ئینتەرنێت بپشکنە و دووبارە هەوڵبدە.");}
        else{prefs.edit().remove("tv_session_token").apply();accessToken="";showLogin("کۆدی پێشووت بەسەرچووە یان ناچالاک کراوە. تکایە کۆدی نوێ بنووسە.");}
      });
    }).start();
  }
  void syncChannelCatalog(){
    new Thread(()->{
      HttpURLConnection conn=null;
      try{
        URL url=new URL("https://raw.githubusercontent.com/quranofficial/tv-stream-manager/main/channels.json?sync="+System.currentTimeMillis());
        conn=(HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(5000);conn.setReadTimeout(7000);
        conn.setRequestProperty("Cache-Control","no-cache");
        conn.setRequestProperty("User-Agent","KurdishTVPlus/2.0");
        try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
          byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
          String json=out.toString("UTF-8");
          JSONArray latest=new JSONObject(json).getJSONArray("data");
          if(latest.length()==0)return;
          HashSet<String> ids=new HashSet<>();
          for(int i=0;i<latest.length();i++){
            JSONObject item=latest.optJSONObject(i);
            if(item==null||item.optString("id").isEmpty()||item.optString("name").isEmpty())return;
            if(!ids.add(item.optString("id")))return;
          }
          String previous=channels.toString();
          if(!previous.equals(latest.toString())){
            runOnUiThread(()->{
              if(isFinishing())return;
              String oldQuery=search==null?"":search.getText().toString();
              channels=latest;
              prefs.edit().putString("tv_channels_cache",json).apply();
              showHome();
              if(search!=null&&!oldQuery.isEmpty())search.setText(oldQuery);
              Toast.makeText(this,"لیستی کەناڵەکان نوێ کرایەوە: "+channels.length(),Toast.LENGTH_SHORT).show();
            });
          }else{
            prefs.edit().putString("tv_channels_cache",json).apply();
          }
        }
      }catch(Exception e){
        android.util.Log.w("KurdishTV","Online channel sync failed; keeping cached catalog",e);
      }finally{if(conn!=null)conn.disconnect();}
    }).start();
  }
  GradientDrawable strokeHome(int color,int line,int radius){GradientDrawable d=shape(color,radius);d.setStroke(dp(1),line);return d;}
  void showLastCrash(){String report=getSharedPreferences("crash_report",0).getString("last_crash",null);if(report==null||report.isEmpty())return;new AlertDialog.Builder(this).setTitle("Crash report").setMessage("ئەمە هۆکاری داخستنی جارەکەی پێشووترە. تکایە Copy بکە و بۆم بینێرە.\\n\\n"+report).setPositiveButton("Copy", (d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("Crash report",report));Toast.makeText(this,"Crash report کۆپی کرا",Toast.LENGTH_SHORT).show();}).setNegativeButton("داخستن",null).show();}
  void addRipple(View v,int radius){
    v.setClickable(true);v.setFocusable(true);
    v.setForeground(new android.graphics.drawable.RippleDrawable(
      android.content.res.ColorStateList.valueOf(0x55ffffff),null,shape(0xffffffff,radius)));
  }
  TextView navItem(String icon,String title,boolean active){
    TextView t=label(icon+"\n"+title,11,active?ACCENT:MUTED);t.setGravity(Gravity.CENTER);t.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);addRipple(t,14);return t;
  }
  TextView textButton(String s,Runnable r){TextView t=label(s,14,WHITE);t.setGravity(Gravity.CENTER);t.setPadding(dp(14),dp(10),dp(14),dp(10));t.setBackground(shape(CARD,14));addRipple(t,14);t.setOnClickListener(v->r.run());return t;}

  JSONObject accessRequest(String action,String code,String token) throws Exception {
    HttpURLConnection c=(HttpURLConnection)new URL(SUPABASE_URL+"/functions/v1/tv-access").openConnection();
    c.setRequestMethod("POST");c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setDoOutput(true);
    c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("apikey",SUPABASE_KEY);
    c.setRequestProperty("Authorization","Bearer "+SUPABASE_KEY);
    JSONObject body=new JSONObject();body.put("action",action);body.put("device_id",deviceId);
    if(code!=null)body.put("code",code);if(token!=null)body.put("session_token",token);
    try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}
    int status=c.getResponseCode();InputStream in=status>=200&&status<300?c.getInputStream():c.getErrorStream();
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[2048];int n;
    while((n=in.read(buf))>0)bytes.write(buf,0,n);c.disconnect();
    JSONObject result=new JSONObject(bytes.toString("UTF-8"));
    if(status<200||status>=300)throw new Exception(result.optString("error","هەڵە لە چوونەژوورەوە"));
    return result;
  }

  void showLogin(String message){
    LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setGravity(Gravity.CENTER);
    page.setPadding(dp(28),dp(24),dp(28),dp(24));page.setBackground(gradient(0xff080713,0xff24103e,0));
    LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(24),dp(28),dp(24),dp(26));
    card.setBackground(strokeHome(0xff1a1230,0xff7b2bc2,24));
    TextView logo=label("KURDISH TV+",25,ACCENT);logo.setTypeface(null,Typeface.BOLD);logo.setGravity(Gravity.CENTER);card.addView(logo);
    TextView heading=label("بەخێربێیت",22,WHITE);heading.setGravity(Gravity.CENTER);heading.setTypeface(null,Typeface.BOLD);
    LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(18);card.addView(heading,hp);
    TextView desc=label("بۆ بەکارهێنانی ئەپەکە کۆدی چوونەژوورەوە بنووسە",13,MUTED);desc.setGravity(Gravity.CENTER);desc.setPadding(0,dp(8),0,dp(18));card.addView(desc);
    EditText code=new EditText(this);code.setSingleLine(true);code.setTextColor(WHITE);code.setHintTextColor(MUTED);
    code.setHint("XXXX-XXXX-XXXX");code.setTextSize(18);code.setLetterSpacing(.12f);code.setGravity(Gravity.CENTER);
    code.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    code.setPadding(dp(12),dp(14),dp(12),dp(14));code.setBackground(strokeHome(0xff100b20,0xff6930a0,14));card.addView(code,new LinearLayout.LayoutParams(-1,dp(56)));
    TextView error=label(message,12,0xffffa6a6);error.setGravity(Gravity.CENTER);error.setPadding(0,dp(10),0,dp(4));error.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);card.addView(error);
    TextView submit=label("چوونەژوورەوە  →",15,BG);submit.setTypeface(null,Typeface.BOLD);submit.setGravity(Gravity.CENTER);
    submit.setBackground(shape(ACCENT,14));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(52));bp.topMargin=dp(12);card.addView(submit,bp);
    TextView note=label("هەر کۆدێک تەنها بۆ یەک ئامێر و یەکجار بەکاردێت",11,MUTED);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(16),0,0);card.addView(note);
    page.addView(card,new LinearLayout.LayoutParams(-1,-2));setContentView(page);
    submit.setOnClickListener(v->{String value=code.getText().toString().trim().replace("-","").replace(" ","").toUpperCase(Locale.ROOT);
      if(value.length()!=12){error.setText("کۆدەکە دەبێت ١٢ پیت/ژمارە بێت.");error.setVisibility(View.VISIBLE);return;}
      submit.setEnabled(false);submit.setText("چاوەڕێ بکە...");
      new Thread(()->{try{JSONObject result=accessRequest("redeem",value,null);String token=result.getString("session_token");
        prefs.edit().putString("tv_session_token",token).apply();accessToken=token;
        runOnUiThread(()->{if(!isFinishing())showHome();});
      }catch(Exception e){String m=e.getMessage()==null?"کۆد نادروستە یان بەکارهاتووە":e.getMessage();
        runOnUiThread(()->{submit.setEnabled(true);submit.setText("چوونەژوورەوە  →");error.setText(m);error.setVisibility(View.VISIBLE);});}
      }).start();
    });
  }

  void showHome(){
    root=new LinearLayout(this);root.setOrientation(1);root.setBackground(gradient(0xff07101d,0xff10243a,0));setContentView(root);
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(20),dp(20),dp(20),dp(14));
    FrameLayout logoBox=new FrameLayout(this);logoBox.setBackground(gradient(0xff0b5960,0xff102b45,16));ImageView logo=new ImageView(this);logo.setImageResource(com.kurdish.tv.R.drawable.ic_kurdish_tv_logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logoBox.addView(logo,new FrameLayout.LayoutParams(-1,-1));top.addView(logoBox,new LinearLayout.LayoutParams(dp(54),dp(54)));
    LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(1);titleBox.setPadding(dp(10),0,0,0);
    TextView title=label("KURDISH TV+",21,WHITE);title.setTypeface(null,Typeface.BOLD);titleBox.addView(title);titleBox.addView(label("PREMIUM LIVE TELEVISION",9,MUTED));top.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
    TextView settings=label("⚙",22,WHITE);settings.setGravity(Gravity.CENTER);settings.setBackground(shape(CARD,14));top.addView(settings,new LinearLayout.LayoutParams(dp(44),dp(44)));settings.setOnClickListener(v->relaySettings());
    root.addView(top);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(16),dp(10),dp(16),dp(28));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(10),dp(8),dp(10),dp(8));nav.setBackgroundColor(0xff100b1d);
    TextView navHome=navItem("⌂","سەرەکی",true);TextView navChannels=navItem("▦","کەناڵەکان",false);TextView navFav=navItem("★","دڵخوازەکان",false);
    nav.addView(navHome,new LinearLayout.LayoutParams(0,dp(54),1));nav.addView(navChannels,new LinearLayout.LayoutParams(0,dp(54),1));nav.addView(navFav,new LinearLayout.LayoutParams(0,dp(54),1));root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
    navHome.setOnClickListener(v->{onlyFav=false;genreFilter="هەمووی";showHome();});
    navChannels.setOnClickListener(v->{onlyFav=false;jumpToChannels=true;showHome();});
    navFav.setOnClickListener(v->{onlyFav=true;showHome();});
    LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);hero.setPadding(dp(20),dp(22),dp(20),dp(22));hero.setBackground(gradient(0xff7414c9,0xff21103d,24));
    hero.addView(pill("●  پەخشی ڕاستەوخۆ"));
    TextView welcome=label("هەموو جیهانی\nتەلەڤیزیۆن لە یەک شوێن",24,WHITE);welcome.setTypeface(null,Typeface.BOLD);welcome.setPadding(0,dp(12),0,dp(5));hero.addView(welcome);
    hero.addView(label("کەناڵە دڵخوازەکانت بە کوالێتی بەرز ببینە",13,0xffe9d5ff));
    TextView heroAction=label("▶  دەستپێکردنی بینین",13,WHITE);heroAction.setGravity(Gravity.CENTER);heroAction.setTypeface(null,Typeface.BOLD);heroAction.setPadding(dp(14),dp(10),dp(14),dp(10));heroAction.setBackground(gradient(0xffb12cff,0xff7414c9,22));LinearLayout.LayoutParams hap=new LinearLayout.LayoutParams(-2,dp(42));hap.topMargin=dp(14);hero.addView(heroAction,hap);heroAction.setOnClickListener(v->{jumpToChannels=true;scroll.post(()->scroll.smoothScrollTo(0,list.getTop()));});
    LinearLayout stats=new LinearLayout(this);stats.setPadding(0,dp(14),0,0);stats.setGravity(Gravity.CENTER_VERTICAL);
    TextView stat1=label("◉  "+channels.length()+" کەناڵ",12,WHITE);stat1.setPadding(dp(12),dp(9),dp(12),dp(9));stat1.setBackground(shape(0x44b12cff,22));stats.addView(stat1);
    TextView stat2=label("  ★ دڵخوازەکانت",12,WHITE);stat2.setPadding(dp(12),dp(9),dp(12),dp(9));stat2.setBackground(shape(0x44b12cff,22));LinearLayout.LayoutParams s2=new LinearLayout.LayoutParams(-2,-2);s2.leftMargin=dp(8);stats.addView(stat2,s2);hero.addView(stats);page.addView(hero);
    LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);searchRow.setPadding(0,dp(16),0,dp(12));
    search=new EditText(this);search.setSingleLine(true);search.setTextColor(WHITE);search.setTextDirection(View.TEXT_DIRECTION_RTL);search.setHintTextColor(MUTED);search.setHint("گەڕان بە ناوی کەناڵ...");search.setTextSize(14);search.setPadding(dp(14),dp(10),dp(14),dp(10));search.setBackground(strokeHome(CARD,0xff49305f,15));searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
    TextView go=label("⌕",27,ACCENT);go.setGravity(Gravity.CENTER);searchRow.addView(go,new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(searchRow);
    LinearLayout filters=new LinearLayout(this);filters.setOrientation(0);filters.setPadding(0,0,0,dp(12));
    HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);String[] genres={"هەمووی","News","Sports","Family","Islamic","Kids","Movies","Music","Documentary"};
    for(String g:genres){TextView chip=label(g,12,g.equals(genreFilter)?BG:WHITE);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(13),dp(9),dp(13),dp(9));chip.setBackground(shape(g.equals(genreFilter)?ACCENT:CARD,24));addRipple(chip,24);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(38));cp.setMargins(0,0,dp(7),0);filters.addView(chip,cp);chip.setOnClickListener(v->{genreFilter=g;showHome();});}
    hs.addView(filters);page.addView(hs);
    LinearLayout section=new LinearLayout(this);section.setGravity(Gravity.CENTER_VERTICAL);TextView sec=label(onlyFav?"دڵخوازەکان":"کەناڵەکان",18,WHITE);sec.setTypeface(null,Typeface.BOLD);section.addView(sec,new LinearLayout.LayoutParams(0,-2,1));TextView favBtn=label(onlyFav?"★ هەموو":"☆ دڵخوازەکان",12,ACCENT);section.addView(favBtn);favBtn.setOnClickListener(v->{onlyFav=!onlyFav;showHome();});page.addView(section);
    list=new LinearLayout(this);list.setOrientation(1);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);page.addView(list,lp);
    Runnable filter=()->renderChannels();search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){renderChannels();}public void afterTextChanged(android.text.Editable e){}});
    renderChannels();
    TextView footer=label("KURDISH TV+  •  PREMIUM LIVE EXPERIENCE",10,MUTED);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(22),0,0);page.addView(footer);
    if(jumpToChannels){scroll.post(()->scroll.smoothScrollTo(0,list.getTop()));jumpToChannels=false;}
  }
  void renderChannels(){
    if(list==null)return;
    list.removeAllViews();
    String q=search==null?"":search.getText().toString().toLowerCase(Locale.ROOT);
    ArrayList<JSONObject> visible=new ArrayList<>();
    for(int i=0;i<channels.length();i++){
      JSONObject c=channels.optJSONObject(i);if(c==null)continue;
      String id=c.optString("id"),name=c.optString("name"),genre=c.optString("genre","");
      if(onlyFav&&!fav.contains(id))continue;
      if(!genreFilter.equals("هەمووی")&&!genre.equalsIgnoreCase(genreFilter))continue;
      if(!name.toLowerCase(Locale.ROOT).contains(q)&&!genre.toLowerCase(Locale.ROOT).contains(q))continue;
      visible.add(c);
    }
    if(genreFilter.equalsIgnoreCase("Sports")){
      Collections.sort(visible,(a,b)->Integer.compare(a.optInt("order",999999),b.optInt("order",999999)));
    }
    for(JSONObject c:visible){
      String id=c.optString("id"),name=c.optString("name"),genre=c.optString("genre","");
      LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(12),dp(10),dp(10),dp(10));card.setBackground(gradient(0xff211735,0xff120d20,20));addRipple(card,18);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(88));cp.setMargins(0,0,0,dp(9));list.addView(card,cp);
      FrameLayout logoBox=new FrameLayout(this);logoBox.setBackground(shape(0xff34204f,16));TextView initials=label(name.isEmpty()?"TV":name.substring(0,Math.min(2,name.length())).toUpperCase(Locale.ROOT),18,ACCENT);initials.setGravity(Gravity.CENTER);logoBox.addView(initials,new FrameLayout.LayoutParams(-1,-1));card.addView(logoBox,new LinearLayout.LayoutParams(dp(58),dp(58)));
      String logo=c.optString("logo","");if(!logo.equals("null")&&!logo.isEmpty())loadLogo(logo,logoBox,initials);
      LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setPadding(dp(12),0,dp(5),0);TextView nm=label(name,15,WHITE);nm.setTypeface(null,Typeface.BOLD);info.addView(nm);info.addView(label(genre+"  •  HD",11,MUTED));card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
      TextView star=label(fav.contains(id)?"★":"☆",23,fav.contains(id)?ACCENT:MUTED);star.setGravity(Gravity.CENTER);addRipple(star,12);card.addView(star,new LinearLayout.LayoutParams(dp(34),dp(48)));star.setOnClickListener(v->{if(!fav.add(id))fav.remove(id);prefs.edit().putStringSet("favorites",new HashSet<>(fav)).apply();renderChannels();});
      TextView play=label("▶",16,BG);play.setGravity(Gravity.CENTER);play.setBackground(shape(ACCENT,14));addRipple(play,14);card.addView(play,new LinearLayout.LayoutParams(dp(42),dp(42)));play.setOnClickListener(v->playChannel(c));
      card.setOnClickListener(v->playChannel(c));
    }
    if(visible.isEmpty()){TextView empty=label("هیچ کەناڵێک نەدۆزرایەوە",14,MUTED);empty.setGravity(Gravity.CENTER);list.addView(empty);}
  }
  void loadLogo(String url,FrameLayout box,TextView fallback){
    android.graphics.Bitmap cached=logoCache.get(url);
    if(cached!=null){showLogoBitmap(box,cached);return;}
    if(!logoLoads.add(url))return;
    logoExecutor.execute(()->{
      HttpURLConnection conn=null;android.graphics.Bitmap bitmap=null;
      try{
        conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(4000);conn.setReadTimeout(5000);
        conn.setUseCaches(true);
        try(InputStream in=conn.getInputStream()){bitmap=android.graphics.BitmapFactory.decodeStream(in);}
        if(bitmap!=null)logoCache.put(url,bitmap);
      }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();logoLoads.remove(url);}
      final android.graphics.Bitmap result=bitmap;
      if(result!=null)runOnUiThread(()->{if(!isFinishing()&&box.getParent()!=null)showLogoBitmap(box,result);});
    });
  }
  void showLogoBitmap(FrameLayout box,android.graphics.Bitmap bitmap){
    ImageView image=new ImageView(this);image.setImageBitmap(bitmap);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    box.removeAllViews();box.addView(image,new FrameLayout.LayoutParams(-1,-1));
  }
  void relaySettings(){
    new AlertDialog.Builder(this)
      .setTitle("پەیوەندی بە ستریم")
      .setMessage("ئەپەکە بە شێوەی ئۆتۆماتیکی نوێترین لینکی Live لە GitHub Actions وەردەگرێت. پێویست ناکات هیچ لینکێک بە دەستی دابنێیت.\n\nتکایە دڵنیابە Telegram Bot workflow چالاکە.")
      .setPositiveButton("باشە",null).show();
  }
  void prefetchRuntime(){
    new Thread(()->{
      HttpURLConnection conn=null;
      try{
        URL url=new URL("https://raw.githubusercontent.com/quranofficial/tv-stream-manager/main/runtime.json?prefetch="+System.currentTimeMillis());
        conn=(HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(5000);conn.setReadTimeout(5000);
        conn.setRequestProperty("Cache-Control","no-cache");
        if(conn.getResponseCode()!=200)return;
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=conn.getInputStream()){byte[] buf=new byte[2048];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}
        JSONObject config=new JSONObject(out.toString("UTF-8"));
        String base=config.optString("base_url","").trim().replaceAll("/+$","");
        String key=config.optString("access_key","").trim();
        URI parsed=new URI(base);
        String host=parsed.getHost();
        if("https".equalsIgnoreCase(parsed.getScheme())&&host!=null&&host.toLowerCase(Locale.ROOT).endsWith(".trycloudflare.com")&&!key.isEmpty()){
          prefs.edit().putString("tv_runtime_base",base).putString("tv_runtime_key",key).apply();
        }
      }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();}
    }).start();
  }
  void playChannel(JSONObject channel){
    String id=channel.optString("id");
    String title=channel.optString("name");
    // Start immediately with the last known relay; refresh the tunnel only when needed.
    String cachedBase=prefs.getString("tv_runtime_base","");
    String cachedKey=prefs.getString("tv_runtime_key","");
    if(!cachedBase.isEmpty()&&!cachedKey.isEmpty()){
      launchPlayer(id,title,cachedBase,cachedKey);
      return;
    }
    Toast.makeText(this,"وەرگرتنی لینکی Live بۆ "+title+"...",Toast.LENGTH_SHORT).show();
    new Thread(()->{
      HttpURLConnection conn=null;
      try{
        URL configUrl=new URL("https://raw.githubusercontent.com/quranofficial/tv-stream-manager/main/runtime.json?nocache="+System.currentTimeMillis());
        conn=(HttpURLConnection)configUrl.openConnection();
        conn.setConnectTimeout(8000);conn.setReadTimeout(8000);
        conn.setRequestProperty("Cache-Control","no-cache");
        int code=conn.getResponseCode();
        if(code!=200)throw new Exception("لینکی Live ئامادە نییە. سەرەتا Telegram TV Bot لە GitHub Actions Run بکە.");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=conn.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}
        JSONObject config=new JSONObject(out.toString("UTF-8"));
        String base=config.optString("base_url","").trim().replaceAll("/+$","");
        String key=config.optString("access_key","").trim();
        URI parsed=new URI(base);
        String host=parsed.getHost();
        if(!"https".equalsIgnoreCase(parsed.getScheme())||host==null||
          !host.toLowerCase(Locale.ROOT).endsWith(".trycloudflare.com")||
          base.toLowerCase(Locale.ROOT).contains("undefined")||
          base.toLowerCase(Locale.ROOT).contains("null")||key.isEmpty())
          throw new Exception("runtime.json لینکی نادروستی تێدایە؛ Telegram TV Bot لە GitHub Actions دووبارە Run بکە.");
        prefs.edit().putString("tv_runtime_base",base).putString("tv_runtime_key",key).apply();
        runOnUiThread(()->{if(!isFinishing())launchPlayer(id,title,base,key);});
      }catch(Exception e){
        String message=e.getMessage()==null?"هەڵە لە پەیوەندی بە ستریم":e.getMessage();
        runOnUiThread(()->{if(!isFinishing())new AlertDialog.Builder(MainActivity.this).setTitle("پەخش ئامادە نییە").setMessage(message+"\n\nGitHub → Actions → Telegram TV Bot → Run workflow").setPositiveButton("باشە",null).show();});
      }finally{if(conn!=null)conn.disconnect();}
    }).start();
  }
  void launchPlayer(String id,String title,String base,String key){
    String streamUrl=base+"/stream/"+id+".ts?key="+key;
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
  }
}
