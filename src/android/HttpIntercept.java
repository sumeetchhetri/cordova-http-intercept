package com.sum.http;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.apache.cordova.engine.SystemWebViewEngine;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;
import android.webkit.WebView;

/**
 * This class echoes a string called from JavaScript.
 */
public class HttpIntercept extends CordovaPlugin {

    public static final String TAG = "HttpIntercept";
    
    private static final String ACTION_INIT = "init";

    private CustomWebViewClient cwc;

    @Override
    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {
		JSONObject options = inputs.optJSONObject(0);
        if(options == null) return false;

        if (ACTION_INIT.equals(action)) {
            if(cwc==null) {
                WebView wv = (WebView) webView.getEngine().getView();
                try {
                    cwc = new CustomWebViewClient((SystemWebViewEngine) webView.getEngine(), options, cordova, callbackContext, wv);
                    wv.post(() -> wv.setWebViewClient(cwc));
                } catch (Exception e) {
                    Log.e(TAG, "Failed initializing HttpIntercept + [" + e.getMessage() + "]");
                    Log.e(HttpIntercept.TAG, Log.getStackTraceString(e));
                    callbackContext.sendPluginResult(new PluginResult(Status.ERROR, e.getMessage()));
                    return false;
                }
            } else {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, "DONE"));
            }

            //LOG.i(HttpIntercept.TAG, "Successfully initialized HttpIntercept");
            //callbackContext.sendPluginResult(new PluginResult(Status.OK));
        }
        return true;
    }
}
