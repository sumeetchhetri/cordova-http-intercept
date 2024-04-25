package com.sum.http;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.engine.SystemWebViewClient;
import org.apache.cordova.engine.SystemWebViewEngine;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.StringTokenizer;

public class CustomWebViewClient extends SystemWebViewClient {

    private final VersionManager versMgr;

    private final Context ctx;

    public CustomWebViewClient(SystemWebViewEngine systemWebViewEngine, JSONObject options, CordovaInterface crd, CallbackContext cbc, WebView wv) {
        super(systemWebViewEngine);
        ctx = crd.getActivity().getApplicationContext();
        versMgr = new VersionManager(options, crd, cbc, wv);
    }

    public static String getMime(String url) {
        String ext = url.substring(url.lastIndexOf(".") + 1);
        return mimeTypes.get(ext);
    }

    @Override
    public boolean onRenderProcessGone(final WebView view, RenderProcessGoneDetail detail) {
        System.out.println(detail);
        return true;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // Check if the request is for a specific resource
        try {
            InputStream fis = versMgr.intercept(request.getUrl());
            if (fis!=null) {
                return new WebResourceResponse(getMimeType(request.getUrl()), "UTF-8", fis);
            }
        } catch(Exception e) {
            Log.e(HttpIntercept.TAG, Log.getStackTraceString(e));
        }
        return super.shouldInterceptRequest(view, request);
    }

    public String getMimeType(Uri uri) {
        String mimeType = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            ContentResolver cr = ctx.getContentResolver();
            mimeType = cr.getType(uri);
        } else {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
        }
        return mimeType;
    }

    private static final Hashtable<String, String> mimeTypes = new Hashtable<>();

    static {
        StringTokenizer st = new StringTokenizer(
            "css		text/css " +
            "htm		text/html " +
            "html		text/html " +
            "xml		text/xml " +
            "java		java=text/x-java-source " +
            "md			text/plain " +
            "txt		text/plain " +
            "asc		text/plain " +
            "gif		image/gif " +
            "jpg		image/jpeg " +
            "jpeg		image/jpeg " +
            "png		image/png " +
            "svg		image/svg+xml " +
            "mp3		audio/mpeg " +
            "m3u		audio/mpeg-url " +
            "mp4		video/mp4 " +
            "ogv		video/ogg " +
            "flv		video/x-flv " +
            "mov		video/quicktime " +
            "glb		model/gltf-binary " +
            "swf		application/x-shockwave-flash " +
            "js			application/javascript " +
            "pdf		application/pdf " +
            "doc		application/msword " +
            "ogg		application/x-ogg " +
            "zip		application/octet-stream " +
            "exe		application/octet-stream " +
            "class		application/octet-stream " +
            "m3u8		application/vnd.apple.mpegurl " +
            "ts			video/mp2t ");

        while (st.hasMoreTokens())
            mimeTypes.put(st.nextToken(), st.nextToken());
    }
}
