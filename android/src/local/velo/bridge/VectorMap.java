package local.velo.bridge;
import android.content.Context;import android.graphics.Color;import android.net.Uri;import android.webkit.*;import android.os.*;import java.io.*;import java.net.URI;import java.util.*;import org.json.*;
/** Bundled MapLibre renderer. No JS-to-native bridge, remote HTML, or local file access. */
public final class VectorMap {
 public static final String DEFAULT="https://tiles.openfreemap.org/styles/bright";
 final WebView web;final RoutePreview owner;boolean pageReady,active=true,polling;String status="loading",error="",last="";long polled;
 public static void validate(String url)throws Exception{URI u=new URI(url);if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null)throw new IOException("地图样式必须使用 HTTPS 地址");}
 public VectorMap(Context context,RoutePreview view){owner=view;web=new WebView(context);web.setBackgroundColor(Color.rgb(235,239,229));WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setCacheMode(WebSettings.LOAD_DEFAULT);settings.setTextZoom(100);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(false);settings.setUserAgentString(settings.getUserAgentString()+" VELO-BikeDash/0.9.1");web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);web.setImportantForAccessibility(WebView.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
  web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){if(m.messageLevel()==ConsoleMessage.MessageLevel.ERROR){error=m.message();android.util.Log.d("VeloVector",error);}return true;}});
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){Uri uri=request.getUrl();if("appassets.androidplatform.net".equals(uri.getHost())){String path=uri.getPath();String name="/velo-map/index.html".equals(path)?"index.html":"/velo-map/maplibre-gl.js".equals(path)?"maplibre-gl.js":"/velo-map/maplibre-gl.css".equals(path)?"maplibre-gl.css":null;try{if(name==null)return empty();String mime=name.endsWith(".js")?"application/javascript":name.endsWith(".css")?"text/css":"text/html";return new WebResourceResponse(mime,"UTF-8",context.getAssets().open("map/"+name));}catch(IOException e){return empty();}}
    if(MapTiles.noNetworkForTests)return empty();if(!"https".equalsIgnoreCase(uri.getScheme())&&!"blob".equalsIgnoreCase(uri.getScheme())&&!"data".equalsIgnoreCase(uri.getScheme()))return empty();return null;
   }
   @Override public void onPageFinished(WebView v,String url){pageReady=true;last="";owner.invalidate();}
  });web.loadUrl("https://appassets.androidplatform.net/velo-map/index.html");
 }
 static WebResourceResponse empty(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
 public void render(double lon,double lat,double logicalZoom){
  boolean visible=!owner.suspended&&owner.isShown()&&owner.getWindowVisibility()==android.view.View.VISIBLE;if(active!=visible){active=visible;if(active)web.onResume();else web.onPause();}
  if(!pageReady||!visible)return;
  String style=MapTiles.noNetworkForTests?"":owner.getContext().getSharedPreferences("navigation",0).getString("map_vector_style",DEFAULT);
  // MapLibre uses 512 CSS pixels per world tile; native overlays use 256 dp.
  String camera=String.format(Locale.US,"%.9f,%.9f,%.5f,%s",lon,lat,logicalZoom-1,JSONObject.quote(style)),signature=camera+"|"+owner.getWidth()+"x"+owner.getHeight();if(!signature.equals(last)){last=signature;web.evaluateJavascript("window.Velo&&window.Velo.camera("+camera+")",null);}
  long now=SystemClock.elapsedRealtime();if(!polling&&now-polled>1000){polling=true;polled=now;web.evaluateJavascript("JSON.stringify(window.VeloState||{})",value->{polling=false;try{String decoded=new JSONArray("["+value+"]").getString(0);JSONObject state=new JSONObject(decoded);String next=state.optString("status","loading");error=state.optString("error","");if(!status.equals(next)){status=next;owner.invalidate();}}catch(Exception ignored){}});}
 }
 public void close(){web.stopLoading();web.loadUrl("about:blank");if(web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)web.getParent()).removeView(web);web.destroy();}
}
