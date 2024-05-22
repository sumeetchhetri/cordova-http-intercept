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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class VersionManager {

    private String version_;

    private final String versionCheckUrl_;

    private final String baseUrl_;

    private final List<String> resources_ = Collections.synchronizedList(new ArrayList<>());

    private final CordovaInterface crd_;

    private AtomicBoolean cbDone = new AtomicBoolean(false);

    private void update(CallbackContext cbc, WebView wv) {
        if(new File(getAppDir(), "version.txt").exists()) {
            try {
                version_ = ioToString(new FileInputStream(new File(getAppDir(), "version.txt")));
                version_ = version_.substring(0, version_.length()-1);
                Log.i(HttpIntercept.TAG, "Current version is " + version_);
            } catch(Exception e){}
        }

        crd_.getThreadPool().execute(() -> {
            try {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(versionCheckUrl_.replace("<<version>>", version_));
                    conn = (HttpURLConnection) url.openConnection();
                    String result = ioToString(conn.getInputStream());
                    JSONObject obj = new JSONObject(result);

                    List<String> resources__ = new ArrayList<>();
                    JSONArray resources = obj.optJSONArray("resources");
                    if(resources!=null) {
                        for (int i = 0; i < resources.length(); i++) {
                            try {
                                resources__.add(resources.getString(i));
                            } catch (Exception e) {
                                Log.e(HttpIntercept.TAG, Log.getStackTraceString(e));
                            }
                        }
                    }

                    if(resources_.isEmpty()) {
                        resources_.addAll(resources__);
                    } else if(!resources_.containsAll(resources__) && resources__.containsAll(resources_)) {
                        resources_.clear();
                        resources_.addAll(resources__);
                    }

                    if(!obj.getBoolean("status")) {
                        String oldVersion = version_;
                        String _version = obj.getString("version");
                        File versDir = new File(getAppDir(), _version);

                        if(!versDir.exists()) {
                            versDir.mkdir();
                        }

                        if(!_version.equals(oldVersion)) {
                            if(new File(getAppDir(), oldVersion).exists()) {
                                new File(getAppDir(), oldVersion).delete();
                            }
                            version_ = _version;
                            OutputStreamWriter ow = new OutputStreamWriter(new FileOutputStream(new File(getAppDir(), "version.txt")));
                            ow.write(version_);
                            ow.close();
                        }


                        if(!resources_.isEmpty() && !new File(versDir, "updated_.txt").exists()) {
                            if(!cbDone.get()) {
                                PluginResult pr = new PluginResult(PluginResult.Status.OK, "START");
                                pr.setKeepCallback(true);
                                wv.post(() -> cbc.sendPluginResult(pr));
                            }

                            List<Future<?>> calls = new ArrayList<>();
                            for(String res: resources_) {
                                calls.add(triggerUpdate(res));
                            }
                            for(Future<?> f: calls) {
                                f.get();
                            }

                            new File(versDir, "updated_.txt").createNewFile();

                            if(!cbDone.get()) {
                                wv.post(() -> cbc.sendPluginResult(new PluginResult(PluginResult.Status.OK, "DONE")));
                                cbDone.set(true);
                            }
                        } else {
                            if(!cbDone.get()) {
                                wv.post(() -> cbc.sendPluginResult(new PluginResult(PluginResult.Status.OK, "DONE")));
                                cbDone.set(true);
                            }
                        }
                    } else {
                        if(!cbDone.get()) {
                            wv.post(() -> cbc.sendPluginResult(new PluginResult(PluginResult.Status.OK, "DONE")));
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
        });
    }

    public VersionManager(JSONObject options, CordovaInterface crd, CallbackContext cbc, WebView wv) {
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

        update(cbc, wv);
    }

    private static final String ASSET_PATH= "/android_asset/www/";
    private static final String ASSET_REL_PATH= "/android_asset/";
    protected InputStream intercept(Uri uri) throws IOException {
        String url = uri.getPath();
        if(url.startsWith(ASSET_PATH)) {
            Context ctx = crd_.getActivity().getApplicationContext();
            AssetManager am = ctx.getResources().getAssets();
            String url_ = url.startsWith(ASSET_PATH) ? url.substring(ASSET_PATH.length()) : url;
            if (!resources_.isEmpty()) {
                for (String p : resources_) {
                    if (p.equals(url_) || getVersionedUrl(p).equals(url_)) {
                        File pf = new File(getAppDir() + File.separator + version_ + File.separator + getVersionedUrl(p));
                        if (pf.exists()) {
                            LOG.i(HttpIntercept.TAG, String.format("Rendered %s from path %s", url_, pf.getAbsolutePath()));
                            return new FileInputStream(pf);
                        } else {
                            url_ = url.startsWith(ASSET_REL_PATH) ? url.substring(ASSET_REL_PATH.length()) : url;
                            return am.open(getVersionedUrl(url_));
                        }
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
