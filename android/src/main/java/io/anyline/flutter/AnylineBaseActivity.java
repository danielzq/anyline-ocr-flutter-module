package io.anyline.flutter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import at.nineyards.anyline.core.LicenseException;
import io.anyline.AnylineSDK;
import io.anyline.camera.CameraConfig;
import io.anyline.camera.CameraController;
import io.anyline.camera.CameraFeatures;
import io.anyline.camera.CameraOpenListener;
import io.anyline.plugin.barcode.BarcodeScanViewPlugin;
import io.anyline.plugin.id.IdScanViewPlugin;
import io.anyline.plugin.licenseplate.LicensePlateScanViewPlugin;
import io.anyline.plugin.meter.MeterScanViewPlugin;
import io.anyline.plugin.ocr.OcrScanViewPlugin;
import io.anyline.view.AbstractBaseScanViewPlugin;
import io.anyline.view.ParallelScanViewComposite;
import io.anyline.view.SerialScanViewComposite;

public abstract class AnylineBaseActivity extends AppCompatActivity
        implements CameraOpenListener, Thread.UncaughtExceptionHandler {

    private static final String TAG = AnylineBaseActivity.class.getSimpleName();

    protected String licenseKey;
    protected String configJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null){
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        licenseKey = getIntent().getStringExtra(Constants.EXTRA_LICENSE_KEY);
        configJson = getIntent().getStringExtra(Constants.EXTRA_CONFIG_JSON);

        try {
            AnylineSDK.init(licenseKey, this);
        } catch (LicenseException e) {
            String errorCode = Constants.EXCEPTION_LICENSE;
            finishWithError(errorCode);
        }
    }

    /**
     * Always set this like this after the initAnyline: <br/>
     * scanView.getAnylineController().setWorkerThreadUncaughtExceptionHandler(this);<br/>
     * <br/>
     * This will forward background errors back to the plugin (and back to flutter from there)
     */
    @Override
    public void uncaughtException(Thread thread, Throwable e) {
        String msg = e.getMessage();
        Log.e(TAG, "Catched uncaught exception", e);

        String errorCode;
        if (msg.contains("license") || msg.contains("License")) {
            errorCode = Constants.EXCEPTION_LICENSE;
        } else {
            errorCode = Constants.EXCEPTION_CORE;
        }

        finishWithError(errorCode);
    }

    protected void finishWithError(String errorCode) {

        Intent data = new Intent();
        data.putExtra(Constants.EXTRA_ERROR_CODE, errorCode);
        setResult(Constants.RESULT_ERROR, data);
        ResultReporter.onError(errorCode);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home){
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCameraOpened(CameraController cameraController, int width, int height) {
        Log.d(TAG, "Camera opened. Frame size " + width + " x " + height + ".");
    }

    @Override
    public void onCameraError(Exception e) {
        finishWithError(Constants.EXCEPTION_NO_CAMERA_PERMISSION);
    }


    @Override
    public void onBackPressed() {
        ResultReporter.onCancel();
        super.onBackPressed();
    }

    protected String jsonForOutline(List<PointF> pointList) {

        JSONObject upLeft = new JSONObject();
        JSONObject upRight = new JSONObject();
        JSONObject downRight = new JSONObject();
        JSONObject downLeft = new JSONObject();
        JSONObject outline = new JSONObject();

        try {
            upLeft.put("x", pointList.get(0).x);
            upLeft.put("y", pointList.get(0).y);

            upRight.put("x", pointList.get(1).x);
            upRight.put("y", pointList.get(1).y);

            downRight.put("x", pointList.get(2).x);
            downRight.put("y", pointList.get(2).y);

            downLeft.put("x", pointList.get(3).x);
            downLeft.put("y", pointList.get(3).y);

            outline.put("upLeft", upLeft);
            outline.put("upRight", upRight);
            outline.put("downRight", downRight);
            outline.put("downLeft", downLeft);


        } catch (JSONException e) {
            Log.d(TAG, e.toString());
            e.printStackTrace();
        }

        return outline.toString();
    }


    protected TextView getLabelView(Context context) {

        TextView labelView = new TextView(context);

        try {
            JSONObject jsonObject = new JSONObject(configJson);
            JSONObject labelObject = jsonObject.getJSONObject("label");
            labelView.setText(labelObject.getString("text"));
            labelView.setTextColor(Color.parseColor("#" + labelObject.getString("color")));
            labelView.setTextSize(Float.parseFloat(labelObject.getString("size")));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return labelView;
    }

    protected ArrayList<Double> getArrayListFromJsonArray(JSONArray jsonObject) {
        ArrayList<Double> listdata = new ArrayList<>();
        JSONArray jArray;
        jArray = jsonObject;
        try {
            for (int i = 0; i < jArray.length(); i++) {
                listdata.add(jArray.getDouble(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return listdata;
    }

    protected RelativeLayout.LayoutParams getTextLayoutParams() {
        // Defining the RelativeLayout layout parameters.
        // In this case I want to fill its parent
        return new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
    }

    protected RelativeLayout.LayoutParams getWrapContentLayoutParams() {
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        return lp;
    }

    protected void setFocusConfig(JSONObject json, CameraConfig camConfig) throws JSONException {

        if (json.has("focus")) {
            JSONObject focusConfig = json.getJSONObject("focus");

            // change default focus mode to auto (works better if cutout is not in the center)
            switch (focusConfig.getString("mode")) {
                case ("AUTO"):
                default:
                    camConfig.setFocusMode(CameraFeatures.FocusMode.AUTO);
                    break;
                case ("MACRO"):
                    camConfig.setFocusMode(CameraFeatures.FocusMode.MACRO);
                    break;
                case ("CONTINUOUS_PICTURE"):
                    camConfig.setFocusMode(CameraFeatures.FocusMode.CONTINUOUS_PICTURE);
                    break;
                case ("CONTINUOUS_VIDEO"):
                    camConfig.setFocusMode(CameraFeatures.FocusMode.CONTINUOUS_VIDEO);
                    break;
                case ("EDOF"):
                    camConfig.setFocusMode(CameraFeatures.FocusMode.EDOF);
                    break;
                case ("FIXED"):
                    camConfig.setFocusMode(CameraFeatures.FocusMode.FIXED);
                    break;
                case ("INFINITY"):
                    camConfig.setFocusMode(CameraFeatures.FocusMode.INFINITY);
                    break;
                case ("OFF"):
                    camConfig.setFocusMode(CameraFeatures.FocusMode.OFF);
                    break;
            }
            // autofocus is called in this interval (8000 is default)
            if (focusConfig.has("interval")) {
                camConfig.setAutoFocusInterval(focusConfig.getInt("interval"));
            }
            // call autofocus if view is touched (true is default)
            if (focusConfig.has("touchEnabled")) {
                camConfig.setFocusOnTouchEnabled(focusConfig.getBoolean("touchEnabled"));
            }
            // focus where the cutout is (true is default)
            if (focusConfig.has("regionEnabled")) {
                camConfig.setFocusRegionEnabled(focusConfig.getBoolean("regionEnabled"));
            }
            // automatic exposure calculation based on where the cutout is (true is default)
            if (focusConfig.has("autoExposureRegionEnabled")) {
                camConfig.setAutoExposureRegionEnabled(focusConfig.getBoolean("autoExposureRegionEnabled"));
            }
        }
    }

    protected void setResult(AbstractBaseScanViewPlugin scanViewPlugin, JSONObject jsonResult) {
        Boolean isCancelOnResult = true;
        if (scanViewPlugin instanceof MeterScanViewPlugin) {
            isCancelOnResult = ((MeterScanViewPlugin) scanViewPlugin).getScanViewPluginConfig().isCancelOnResult();
        } else if (scanViewPlugin instanceof BarcodeScanViewPlugin) {
            isCancelOnResult = ((BarcodeScanViewPlugin) scanViewPlugin).getScanViewPluginConfig().isCancelOnResult();
        } else if (scanViewPlugin instanceof IdScanViewPlugin) {
            isCancelOnResult = ((IdScanViewPlugin) scanViewPlugin).getScanViewPluginConfig().isCancelOnResult();
        } else if (scanViewPlugin instanceof LicensePlateScanViewPlugin) {
            isCancelOnResult = ((LicensePlateScanViewPlugin) scanViewPlugin).getScanViewPluginConfig().isCancelOnResult();
        } else if (scanViewPlugin instanceof OcrScanViewPlugin) {
            isCancelOnResult = ((OcrScanViewPlugin) scanViewPlugin).getScanViewPluginConfig().isCancelOnResult();
        } else if (scanViewPlugin instanceof SerialScanViewComposite) {
            isCancelOnResult = ((SerialScanViewComposite) scanViewPlugin).getScanViewPluginConfig().isCancelOnResult();
        } else if (scanViewPlugin instanceof ParallelScanViewComposite) {
            isCancelOnResult = ((ParallelScanViewComposite) scanViewPlugin).getScanViewPluginConfig().isCancelOnResult();
        }

        if (scanViewPlugin != null && isCancelOnResult) {
//          if(scanViewPlugin != null && scanViewPlugin.getScanViewPluginConfig().isCancelOnResult()){
            ResultReporter.onResult(jsonResult, true);
            setResult(Constants.RESULT_OK);
            finish();
        } else {
            ResultReporter.onResult(jsonResult, false);
        }

    }

}