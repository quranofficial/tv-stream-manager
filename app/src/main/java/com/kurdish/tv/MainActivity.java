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
  TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
  TextView pill(String s){TextView t=label(s,12,ACCENT);t.setPadding(dp(10),dp(5),dp(10),dp(5));t.setBackground(shape(0xff123a42,30));return t;}
  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);prefs=getSharedPreferences("kurdish_tv",0);fav.addAll(prefs.getStringSet("favorites",new HashSet<>()));try{InputStream in=getAssets().open("channels.json");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);channels=new JSONObject(out.toString("UTF-8")).getJSONArray("data");}catch(Exception e){}showHome();}
  TextView textButton(String s,Runnable r){TextView t=label(s,14,WHITE);t.setGravity(Gravity.CENTER);t.setPadding(dp(14),dp(10),dp(14),dp(10));t.setBackground(shape(CARD,14));t.setOnClickListener(v->r.run());return t;}
  void showHome(){
    root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);setContentView(root);
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(18),dp(15),dp(18),dp(8));
    TextView logo=label("◉",30,ACCENT);top.addView(logo);
    LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(1);titleBox.setPadding(dp(10),0,0,0);
    TextView title=label("KURDISH TV",19,WHITE);title.setTypeface(null,Typeface.BOLD);titleBox.addView(title);titleBox.addView(label("LIVE • کەناڵە کوردییەکان",10,MUTED));top.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
    TextView settings=label("⚙",25,WHITE);settings.setGravity(Gravity.CENTER);top.addView(settings);settings.setOnClickListener(v->relaySettings());
    root.addView(top);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(16),dp(10),dp(16),dp(28));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);hero.setPadding(dp(18),dp(18),dp(18),dp(18));hero.setBackground(shape(0xff12313a,22));
    hero.addView(pill("●  پەخشی ڕاستەوخۆ"));
    TextView welcome=label("هەموو کەناڵەکانت،
لە یەک شوێن",25,WHITE);welcome.setTypeface(null,Typeface.BOLD);welcome.setPadding(0,dp(12),0,dp(5));hero.addView(welcome);
    hero.addView(label(channels.length()+" کەناڵ بەردەستن • بەبێ Login",13,0xffb8d5d7));page.addView(hero);
    LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);searchRow.setPadding(0,dp(16),0,dp(12));
    search=new EditText(this);search.setSingleLine(true);search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setHint("گەڕان بە ناوی کەناڵ...");search.setTextSize(14);search.setPadding(dp(14),dp(10),dp(14),dp(10));search.setBackground(shape(CARD,15));searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
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
      LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(12),dp(10),dp(10),dp(10));card.setBackground(shape(CARD,17));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(78));cp.setMargins(0,0,0,dp(9));list.addView(card,cp);
      FrameLayout logoBox=new FrameLayout(this);logoBox.setBackground(shape(0xff20344b,14));TextView initials=label(name.isEmpty()?"TV":name.substring(0,Math.min(2,name.length())).toUpperCase(Locale.ROOT),18,ACCENT);initials.setGravity(Gravity.CENTER);logoBox.addView(initials,new FrameLayout.LayoutParams(-1,-1));card.addView(logoBox,new LinearLayout.LayoutParams(dp(54),dp(54)));
      String logo=c.optString("logo","");if(!logo.equals("null")&&!logo.isEmpty())loadLogo(logo,logoBox,initials);
      LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setPadding(dp(12),0,dp(5),0);TextView nm=label(name,15,WHITE);nm.setTypeface(null,Typeface.BOLD);info.addView(nm);info.addView(label(genre+"  •  HD",11,MUTED));card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
      TextView star=label(fav.contains(id)?"★":"☆",23,fav.contains(id)?ACCENT:MUTED);star.setGravity(Gravity.CENTER);card.addView(star,new LinearLayout.LayoutParams(dp(34),dp(48)));star.setOnClickListener(v->{if(!fav.add(id))fav.remove(id);prefs.edit().putStringSet("favorites",new HashSet<>(fav)).apply();renderChannels();});
      TextView play=label("▶",16,BG);play.setGravity(Gravity.CENTER);play.setBackground(shape(ACCENT,14));card.addView(play,new LinearLayout.LayoutParams(dp(42),dp(42)));play.setOnClickListener(v->playChannel(c));
      card.setOnClickListener(v->playChannel(c));
    }
    if(shown==0){TextView empty=label("هیچ کەناڵێک نەدۆزرایەوە",14,MUTED);empty.setGravity(Gravity.CENTER);list.addView(empty);}
  }
  void loadLogo(String url,FrameLayout box,TextView fallback){new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(6000);android.graphics.Bitmap bm=android.graphics.BitmapFactory.decodeStream(c.getInputStream());c.disconnect();if(bm!=null)runOnUiThread(()->{ImageView iv=new ImageView(this);iv.setImageBitmap(bm);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);box.removeAllViews();box.addView(iv,new FrameLayout.LayoutParams(-1,-1));});}catch(Exception ignored){}}).start();}
  void relaySettings(){EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setHint("لینکی نموونەی کەناڵ لە Telegram bot");input.setText(prefs.getString("relay_sample",""));new AlertDialog.Builder(this).setTitle("بەستەری پەخش").setMessage("لینکی تەواوی یەک کەناڵ لە Telegram bot لێرە دابنێ. ئەپەکە ID ـی کەناڵ دەگۆڕێت بۆ کەناڵەکانی تر.").setView(input).setPositiveButton("پاشەکەوت", (d,w)->{String sample=input.getText().toString().trim();if(!sample.matches("https?://.+/stream/\\d+\\.ts\\?.+")){new AlertDialog.Builder(this).setMessage("لینکەکە پێویستە شێوەی /stream/196.ts?key=... هەبێت").setPositiveButton("باشە",null).show();return;}prefs.edit().putString("relay_sample",sample).apply();}).setNegativeButton("داخستن",null).show();}
  void playChannel(JSONObject c){String sample=prefs.getString("relay_sample","");if(sample.isEmpty()){relaySettings();return;}String id=c.optString("id");String url=sample.replaceFirst("/stream/\\d+\\.ts","/stream/"+id+".ts");Intent i=new Intent(this,PlayerActivity.class);i.putExtra("title",c.optString("name"));i.putExtra("url",url);startActivity(i);}
}
