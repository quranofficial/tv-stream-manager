package com.kurdish.tv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    static final String BASE="https://herrkhvvfmbczbcebrci.supabase.co";
    static final String KEY="sb_publishable_KS1XiyGUxrxjLJ_3aF426g_SZ-XsUQW";
    LinearLayout root,body;
    EditText field,search;
    String mode="code",adminToken="",deviceId="",sessionToken="";
    JSONArray channels=new JSONArray();
    final Set<String> favorites=new HashSet<>();
    SharedPreferences prefs;

    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle b){super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(12,20,35));
        prefs=getSharedPreferences("tv",0);
        deviceId=prefs.getString("device",""); if(deviceId.isEmpty()){deviceId=UUID.randomUUID().toString();prefs.edit().putString("device",deviceId).apply();}
        sessionToken=prefs.getString("session","");
        try { InputStream in=getAssets().open("channels.json"); ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=in.read(buf))>0)out.write(buf,0,n); channels=new JSONObject(out.toString("UTF-8")).getJSONArray("data"); } catch(Exception ignored){}
        home();
    }
    TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(dp(12),dp(10),dp(12),dp(10));return t;}
    EditText edit(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setTextColor(Color.WHITE);e.setHintTextColor(0xff9aa8bd);e.setPadding(dp(14),dp(10),dp(14),dp(10));e.setBackgroundColor(0xff26364d);return e;}
    Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->r.run());return b;}
    void shell(String title){root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff0c1423);setContentView(root);TextView h=text("📺  "+title,23);h.setTypeface(null,1);root.addView(h);ScrollView sc=new ScrollView(this);body=new LinearLayout(this);body.setPadding(dp(14),dp(8),dp(14),dp(24));body.setOrientation(1);sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));}
    void msg(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("باشە",null).show();}
    void busy(boolean b){if(b){ProgressBar p=new ProgressBar(this);body.addView(p);} }
    interface Done{void ok(JSONObject o);void fail(String e);}
    void request(String path,JSONObject data,String bearer,Done done){new Thread(()->{try{
        URL u=new URL(BASE+path); HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("apikey",KEY);if(bearer!=null&&!bearer.isEmpty())c.setRequestProperty("Authorization","Bearer "+bearer);
        c.getOutputStream().write(data.toString().getBytes(StandardCharsets.UTF_8));int code=c.getResponseCode();InputStream is=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=is.read(b))>0)out.write(b,0,n);JSONObject o=new JSONObject(out.toString("UTF-8"));runOnUiThread(()->{if(code>=200&&code<300)done.ok(o);else done.fail(o.optString("msg",o.optString("error","HTTP "+code)));});c.disconnect();
    }catch(Exception e){runOnUiThread(()->done.fail(e.getMessage()==null?"Network error":e.getMessage()));}}).start();}
    JSONObject obj(String... kv){JSONObject o=new JSONObject();try{for(int i=0;i<kv.length;i+=2){String k=kv[i],v=kv[i+1];if(k.equals("create_user"))o.put(k,Boolean.parseBoolean(v));else if(k.equals("valid_days"))o.put(k,Integer.parseInt(v));else o.put(k,v);}}catch(Exception ignored){}return o;}
    void home(){shell("Kurdish TV");body.addView(text("بەخێربێیت — ٢٢٥ کەناڵ",18));
        body.addView(button("چوونەژوورەوە بە کۆدی Login",()->codeLogin()));
        body.addView(button("چوونەژوورەوەی Admin",()->adminEmail()));
        if(!sessionToken.isEmpty())body.addView(button("بینینی کەناڵەکان",()->channelPage("")));
        body.addView(text("هەموو کۆدەکان لە سێرڤەر پشتڕاست دەکرێنەوە.",13));
    }
    void codeLogin(){shell("Login");body.addView(text("کۆدی تایبەتی خۆت بنووسە",16));field=edit("12 پیت/ژمارە");body.addView(field);body.addView(button("چوونەژوورەوە",()->{String code=field.getText().toString().trim().toUpperCase();request("/functions/v1/tv-access",obj("action","redeem","code",code,"device_id",deviceId),KEY,new Done(){public void ok(JSONObject o){sessionToken=o.optString("session_token");prefs.edit().putString("session",sessionToken).apply();channelPage("");}public void fail(String e){msg(e);}});}));body.addView(button("گەڕانەوە",()->home()));}
    void adminEmail(){
        shell("Admin Login");
        body.addView(text("چوونەژوورەوە بە ئیمەیڵ و وشەی نهێنی Supabase",16));
        field=edit("ئیمەیڵ");
        field.setInputType(33);
        body.addView(field);
        EditText password=edit("وشەی نهێنی");
        password.setInputType(129);
        body.addView(password);
        body.addView(button("چوونەژوورەوە",()->{
            String email=field.getText().toString().trim();
            String pass=password.getText().toString();
            if(email.isEmpty()||pass.isEmpty()){msg("ئیمەیڵ و وشەی نهێنی پڕ بکەرەوە");return;}
            request("/auth/v1/token?grant_type=password",obj("email",email,"password",pass),"",new Done(){
                public void ok(JSONObject o){
                    adminToken=o.optString("access_token");
                    if(adminToken.isEmpty()){msg("Access token وەرنەگیرا");return;}
                    request("/functions/v1/claim-tv-admin",new JSONObject(),adminToken,new Done(){
                        public void ok(JSONObject x){adminPanel();}
                        public void fail(String e){msg(e);}
                    });
                }
                public void fail(String e){msg(e);}
            });
        }));
        body.addView(button("گەڕانەوە",()->home()));
    }
    void adminPanel(){shell("Admin Panel");body.addView(button("دروستکردنی کۆدی نوێ",()->createCode()));body.addView(button("بینینی کۆدەکان",()->listCodes()));body.addView(button("کەناڵەکان",()->channelPage("")));body.addView(button("چوونەدەرەوە",()->home()));}
    void createCode(){shell("دروستکردنی کۆد");EditText label=edit("ناوی بەکارهێنەر / تێبینی");body.addView(label);EditText days=edit("ماوە بە ڕۆژ (30)");days.setInputType(2);body.addView(days);body.addView(button("دروستکردن",()->{JSONObject o=obj("action","create","label",label.getText().toString(),"valid_days",days.getText().toString().isEmpty()?"30":days.getText().toString());request("/functions/v1/tv-access",o,adminToken,new Done(){public void ok(JSONObject r){String code=r.optString("code");new AlertDialog.Builder(MainActivity.this).setTitle("کۆدی Login").setMessage(code+"\n\nئەم کۆدە تەنها یەکجار و بۆ یەک ئامێرە.").setPositiveButton("کۆپی", (d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("TV code",code));}).setNegativeButton("داخستن",null).show();}public void fail(String e){msg(e);}});}));}
    void listCodes(){request("/functions/v1/tv-access",obj("action","list"),adminToken,new Done(){public void ok(JSONObject o){JSONArray a=o.optJSONArray("codes");shell("کۆدەکان");if(a==null)return;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);String id=x.optString("id");String line=x.optString("label","بێ ناو")+" • "+(x.optBoolean("is_active")?"چالاک":"ناچالاک")+" • "+x.optString("expires_at");body.addView(text(line,13));body.addView(button("ناچالاککردن",()->request("/functions/v1/tv-access",obj("action","revoke","code_id",id),adminToken,new Done(){public void ok(JSONObject z){listCodes();}public void fail(String e){msg(e);}})));}}public void fail(String e){msg(e);}});}
    void channelPage(String query){shell("کەناڵەکان");search=edit("گەڕان بە ناو یان جۆر");body.addView(search);body.addView(button("گەڕان",()->channelPage(search.getText().toString().trim().toLowerCase(Locale.ROOT))));int count=0;for(int i=0;i<channels.length();i++){JSONObject c=channels.optJSONObject(i);String name=c.optString("name"),genre=c.optString("genre"),id=c.optString("id");if(!name.toLowerCase(Locale.ROOT).contains(query)&&!genre.toLowerCase(Locale.ROOT).contains(query))continue;count++;LinearLayout row=new LinearLayout(this);row.setOrientation(0);TextView t=text(name+"\n"+genre,15);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));row.addView(button(favorites.contains(id)?"★":"☆",()->{if(!favorites.add(id))favorites.remove(id);channelPage(query);}));row.addView(button("▶",()->msg("لیستی ستریم لەوەشدا بە تونێڵی GitHub Actions دەبێت بگاتە ئەپ. ئەم بەستەرەی runtime هێشتا نەبەستراوەتەوە؛ پەخشکردن تا ئەو کاتە چالاک نییە.")));body.addView(row);View d=new View(this);d.setBackgroundColor(0xff26364d);body.addView(d,new LinearLayout.LayoutParams(-1,dp(1)));}body.addView(text("ئەنجام: "+count,13));if(!adminToken.isEmpty())body.addView(button("Admin Panel",()->adminPanel()));}
}
