package com.sum.http;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class VersionManager {

    private String version_;

    private final String versionCheckUrl_;

    private final String baseUrl_;

    private final List<String> resources_ = new ArrayList<>();

    private final CordovaInterface crd_;

    private AtomicBoolean cbDone = new AtomicBoolean(false);

    public VersionManager(JSONObject options, CordovaInterface crd, CallbackContext cbc, WebView wv) {
        /*JSONArray resources = options.optJSONArray("resources");
        if(resources!=null) {
            for (int i = 0; i < resources.length(); i++) {
                try {
                    resources_.add(resources.getString(i));
                } catch (Exception e) {
                    Log.e(HttpIntercept.TAG, Log.getStackTraceString(e));
                }
            }
        }*/
        version_ = options.optString("version");
        versionCheckUrl_ = options.optString("versionCheckUrl");
        baseUrl_ = options.optString("baseUrl");
        crd_ = crd;

        if(versionCheckUrl_.trim().isEmpty()) {
            throw new RuntimeException("Empty/Null version check URL specified");
        }

        if(baseUrl_.trim().isEmpty()) {
            throw new RuntimeException("Empty/Null base update URL specified");
        }

        if(version_.trim().isEmpty()) {
            throw new RuntimeException("Empty/Null version specified");
        }

        /*if(resources_==null || resources_.isEmpty()) {
            throw new RuntimeException("Empty/Null resources list specified");
        }*/

        crd_.getThreadPool().execute(() -> {
            while(true) {
                try {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL(versionCheckUrl_.replace("<<version>>", version_));
                        conn = (HttpURLConnection) url.openConnection();
                        String result = ioToString(conn.getInputStream());
                        JSONObject obj = new JSONObject(result);

                        JSONArray resources = obj.optJSONArray("resources");
                        if(resources!=null) {
                            for (int i = 0; i < resources.length(); i++) {
                                try {
                                    resources_.add(resources.getString(i));
                                } catch (Exception e) {
                                    Log.e(HttpIntercept.TAG, Log.getStackTraceString(e));
                                }
                            }
                        }

                        if(!obj.getBoolean("status")) {
                            String oldVersion = version_;
                            String _version = obj.getString("version");
                            if(!new File(getAppDir(), _version).exists()) {
                                new File(getAppDir(), _version).mkdir();
                            }
                            if(!_version.equals(oldVersion)) {
                                if(new File(getAppDir(), oldVersion).exists()) {
                                    new File(getAppDir(), oldVersion).delete();
                                }
                                version_ = _version;
                            }

                            if(!resources_.isEmpty()) {
                                List<Future<?>> calls = new ArrayList<>();
                                for(String res: resources_) {
                                    calls.add(triggerUpdate(res));
                                }
                                for(Future<?> f: calls) {
                                    f.get();
                                }
                                if(!cbDone.get()) {
                                    wv.post(() -> cbc.sendPluginResult(new PluginResult(PluginResult.Status.OK)));
                                    cbDone.set(true);
                                }
                            }
                        } else {
                            if(!cbDone.get()) {
                                wv.post(() -> cbc.sendPluginResult(new PluginResult(PluginResult.Status.OK)));
                                cbDone.set(true);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(HttpIntercept.TAG, Log.getStackTraceString(e));
                        if(!cbDone.get()) {
                            wv.post(() -> cbc.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage())));
                            cbDone.set(true);
                        }
                    } finally {
                        if(conn!=null) conn.disconnect();
                    }
                    Thread.sleep(60000);
                } catch(InterruptedException e) {
                    Log.e(HttpIntercept.TAG, Log.getStackTraceString(e));
                }
            }
        });
    }

    protected InputStream intercept(Uri uri) throws IOException {
        Context ctx = crd_.getActivity().getApplicationContext();
        AssetManager am = ctx.getResources().getAssets();

        String url = uri.getPath();
        if (url.startsWith("/")) url = url.substring(1);

        /*if(url.startsWith("android_asset/www")) {
            return am.open(url.substring("android_asset/".length()));
        } else if(url.startsWith("android_asset/")) {
            return am.open("www" + File.separator + url.substring("android_asset/".length()));
        }*/

        if (!resources_.isEmpty()) {
            for (String p : resources_) {
                if (p.equals(url)) {
                    File pf = new File(getAppDir() + File.separator + version_ + File.separator + getVersionedUrl(url));
                    if(pf.exists()) {
                        return new FileInputStream(pf);
                    } else {
                        String url_ = "www" + File.separator + getVersionedUrl(url);
                        return am.open(url_);
                    }
                }
            }
        }
        return null;
    }

    public static String ioToString(InputStream ios) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(ios));
        String temp;
        StringBuilder result = new StringBuilder();
        while((temp = reader.readLine())!=null) {
            result.append(temp).append("\n");
        }
        return result.toString();
    }

    private String getAppDir() {
        return crd_.getActivity().getApplicationContext().getApplicationInfo().dataDir;
    }

    private String getVersionedUrl(String url_) {
        String ext = url_.substring(url_.lastIndexOf(".") + 1);
        url_ = url_.substring(0, url_.lastIndexOf(".")) + "_" + version_ + "." + ext;
        return url_;
    }

    private Future<?> triggerUpdate(final String url) {
        return crd_.getThreadPool().submit(() -> {
            try {
                String url_ = url;
                String ext = url_.substring(url_.lastIndexOf(".") + 1);
                String path = url_.lastIndexOf("/")!=-1?url_.substring(0, url_.lastIndexOf("/")):"";
                url_ = url_.substring(0, url_.lastIndexOf(".")) + "_" + version_ + "." + ext;
                String file = url_.lastIndexOf("/")!=-1?url_.substring(url_.lastIndexOf("/") + 1):url_;
                URL urL = new URL(baseUrl_ + "/" + url_);
                File filePath = new File(getAppDir() + File.separator + version_ + File.separator + path);
                File f = new File(filePath, file);
                LOG.i(HttpIntercept.TAG, "Fetching latest resource " + urL);
                if(f.exists()) {
                    LOG.i(HttpIntercept.TAG, "Resource " + urL + " already exists");
                    return;
                }
                HttpURLConnection conn = (HttpURLConnection) urL.openConnection();
                try {
                    String result = ioToString(conn.getInputStream());
                    try {
                        if (!filePath.exists()) {
                            filePath.mkdirs();
                        }

                        OutputStreamWriter ow = new OutputStreamWriter(new FileOutputStream(f));
                        ow.write(result);
                        ow.close();
                        LOG.i(HttpIntercept.TAG, "Fetched and saved latest resource " + urL);
                    } catch (Exception e) {
                        Log.e(HttpIntercept.TAG, Log.getStackTraceString(e));
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                Log.e(HttpIntercept.TAG, Log.getStackTraceString(e));
            }
        });
    }
}
