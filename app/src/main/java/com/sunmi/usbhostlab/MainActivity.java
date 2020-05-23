package com.sunmi.usbhostlab;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.SynchronousQueue;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String USB_ACTION = "com.sunmi.usbcomm.host";
    private static final String AFTER_ACCESSORY_USB_ACTION = "com.sunmi.usb.after.accessory";

    private UsbDevice currentDevice;
    private volatile UsbDeviceConnection mUsbDeviceConnection;
    private volatile UsbEndpoint mUsbEndpointOut;
    private volatile UsbEndpoint mUsbEndpointIn;

    private ReadThread readThread;

    private Context mContext;
    private UsbManager mUsbManager;

    private Button btnOpen;
    private Button btnClose;
    private Button btnSend;
    private Button btnStartRead;
    private Button btnStopRead;

    private StringBuilder stringBuilder = new StringBuilder();
    private TextView tvLog;

    private static final int MESSAGE_LOG = 0;
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if(tvLog != null){
                tvLog.setText(stringBuilder.toString());
                tvLog.invalidate();
            }
        }
    };

    private void showLog(String content){
        android.util.Log.i("mark61", content);
        stringBuilder.insert(0, content + "\n");
        mHandler.sendEmptyMessage(MESSAGE_LOG);
    }

    private void showByAlertDialog(final String title, final String message){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                builder.setTitle(title);
                builder.setMessage(message);
                builder.show();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        initReceiver();
        initView();
        showLog("everything is good, and we are ready to roll");
    }

    private void initReceiver(){
        IntentFilter filter = new IntentFilter(USB_ACTION);
        BroadcastReceiver openDeviceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    initAccessory(usbDevice);
                } else {
                    showByAlertDialog("Failed", "usb device permission not granted");
                }
            }
        };
        mContext.registerReceiver(openDeviceReceiver, filter);

        IntentFilter afterAccessoryFilter = new IntentFilter(AFTER_ACCESSORY_USB_ACTION);
        BroadcastReceiver afterAccessoryPermissionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    OpenAOADeviceThread t = new OpenAOADeviceThread();
                    t.start();
                } else {
                    showByAlertDialog("Failed", "AOA usb device permission not granted");
                }
            }
        };
        mContext.registerReceiver(afterAccessoryPermissionReceiver, afterAccessoryFilter);
    }

    private void initView(){
        btnOpen = findViewById(R.id.open);
        btnOpen.setOnClickListener(this);
        btnClose = findViewById(R.id.close);
        btnClose.setOnClickListener(this);
        btnSend = findViewById(R.id.send);
        btnSend.setOnClickListener(this);
        btnStartRead = findViewById(R.id.start_read_thread);
        btnStartRead.setOnClickListener(this);
        btnStopRead = findViewById(R.id.stop_read_thread);
        btnStopRead.setOnClickListener(this);

        tvLog = findViewById(R.id.log_content);
    }

    @Override
    public void onClick(View v) {
        final int vID = v.getId();
        switch (vID){
            case R.id.open:
                open();
                break;
            case R.id.close:
                close();
                break;
            case R.id.send:
                send();
                break;
            case R.id.start_read_thread:
                startReadThread();
                break;
            case R.id.stop_read_thread:
                stopReadThread();
                break;
        }
    }

    private void open(){
        //S.1 find match device
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            showByAlertDialog("Failed","reason: no USB device found");
            return;
        }
        boolean isMatch = false;
        for (UsbDevice usbDevice : deviceList.values()) {
            String deviceName = getCompatibleName(usbDevice, 21, "getProductName", null);
            if (deviceName.matches("p.+") || deviceName.toLowerCase().contains("redmi") || deviceName.toLowerCase().contains("tai")) {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(USB_ACTION), 0);
                android.util.Log.i("mark61","before AOA, usb device info--->" + usbDevice.toString());
                mUsbManager.requestPermission(usbDevice, pendingIntent);
                isMatch = true;
                break;
            }
        }
        if (!isMatch) {
            showByAlertDialog("Failed","reason: no USB device matched");
            return;
        }
        //S.2 usb device permission
        //S.3 usb to accessory
        //S.4 accessory device permission
        //S.5 open final device
    }

    private void close(){
        if(currentDevice == null){
            showByAlertDialog("No device is opened...", "");
        }else {
            stopReadThread();
            if (mUsbDeviceConnection != null) {
                mUsbDeviceConnection.close();
            }
            mUsbEndpointIn = null;
            mUsbEndpointOut = null;
            mUsbDeviceConnection = null;
            currentDevice = null;
            showByAlertDialog("Success", "device is closed...");
        }
    }

    private void send(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd/ HH:mm:ss", Locale.getDefault());
        String send = sdf.format(new Date()) + " Host";
        if(mUsbEndpointOut != null && mUsbDeviceConnection!= null){
            int len = mUsbDeviceConnection.bulkTransfer(mUsbEndpointOut, send.getBytes(), send.getBytes().length, 3000);
            if(len > 0){
                showByAlertDialog("Success", "send length is " + len);
            }else {
                showByAlertDialog("", "len is" + len);
            }
        }else {
            showByAlertDialog("Failed", "usb is not open");
        }
    }

    private void startReadThread(){
        if(readThread != null){
            showByAlertDialog("ReadThread is Already running...", "");
        }else {
            readThread = new ReadThread();
            readThread.start();
            showByAlertDialog("Success", "ReadThread is running now");
        }
    }

    private void stopReadThread(){
        if(readThread == null){
            showByAlertDialog("ReadThread is Not running...", "");
        }else {
            readThread.interrupt();
            readThread = null;
            showByAlertDialog("Sucess", "ReadThread is not running now");
        }
    }

    private String getCompatibleName(Object obj, int sdkVersion, String methodName, Object[] params) {
        String result = null;
        try {
            Method target = null;
            for (Method m : obj.getClass().getDeclaredMethods()) {
                if (TextUtils.equals(m.getName(), methodName)) {
                    target = m;
                    break;
                }
            }
            if (target != null && Build.VERSION.SDK_INT >= sdkVersion) {
                result = (String) target.invoke(obj, params);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return TextUtils.isEmpty(result) ? "" : result.toLowerCase();
    }

    private void initAccessory(UsbDevice usbDevice) {
        UsbDeviceConnection usbDeviceConnection = mUsbManager.openDevice(usbDevice);
        if (usbDeviceConnection == null) {
            showByAlertDialog("Failed", "open usb device failed");
            return;
        }
        //use AOA protocol to make target device convert into AccessoryMode
        initControlTransfer(usbDeviceConnection, 0, "Sunmi"); // MANUFACTURER
        initControlTransfer(usbDeviceConnection, 1, "K1P1"); // MODEL
        initControlTransfer(usbDeviceConnection, 2, "K1P1 comm"); // DESCRIPTION
        initControlTransfer(usbDeviceConnection, 3, "1.0"); // VERSION
        initControlTransfer(usbDeviceConnection, 4, "http://www.sunmi.com"); // URI
        initControlTransfer(usbDeviceConnection, 5, "0123456789"); // SERIAL
        usbDeviceConnection.controlTransfer(0x40, 53, 0, 0, new byte[]{}, 0, 100);
        usbDeviceConnection.close();

        OpenAOADeviceThread t = new OpenAOADeviceThread();
        t.start();
    }

    private void initControlTransfer(UsbDeviceConnection deviceConnection, int index, String string) {
        deviceConnection.controlTransfer(0x40, 52, 0, index, string.getBytes(), string.length(), 100);
    }

    private class OpenAOADeviceThread extends Thread{

        public OpenAOADeviceThread(){

        }

        @Override
        public void run() {
            super.run();
            try {
                while (!Thread.interrupted()){
                    HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
                    if (deviceList.isEmpty()) {
                        continue;
                    }
                    UsbDevice targetDevice = null;
                    for (UsbDevice usbDevice : deviceList.values()) {
                        String deviceName = getCompatibleName(usbDevice, 21, "getProductName", null);
                        int productId = usbDevice.getProductId();
                        if (productId == 0x2D00 || productId == 0x2D01 || deviceName.matches("p.+")) {
                            targetDevice = usbDevice;
                            break;
                        }
                    }

                    //s.2 confirm target device permission
                    boolean targetDevicePermissionGranted = false;
                    if (null != targetDevice) {
                        if (mUsbManager.hasPermission(targetDevice)) {
                            targetDevicePermissionGranted = true;
                            android.util.Log.i("mark61","after AOA, usb device info--->" + targetDevice.toString());
                        } else {
                            mUsbManager.requestPermission(targetDevice, PendingIntent.getBroadcast(mContext, 0, new Intent(AFTER_ACCESSORY_USB_ACTION), 0));
                            android.util.Log.i("mark61","after AOA, usb device info--->" + targetDevice.toString());
                            break;
                        }
                    } else {
                        continue;
                    }

                    //s.3 with target device permission granted, config all IO stream
                    if (targetDevicePermissionGranted) {
                        currentDevice = targetDevice;
                        mUsbDeviceConnection = mUsbManager.openDevice(targetDevice);
                        UsbInterface mUsbInterface = targetDevice.getInterface(0);
                        int count = mUsbInterface.getEndpointCount();
                        for (int i = 0; i < count; i++) {
                            UsbEndpoint usbEndpoint = mUsbInterface.getEndpoint(i);
                            if (usbEndpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                                if (usbEndpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                                    mUsbEndpointOut = usbEndpoint;
                                } else if (usbEndpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                                    mUsbEndpointIn = usbEndpoint;
                                }
                            }
                        }
                        if (mUsbEndpointOut != null && mUsbEndpointIn != null) {
                            showByAlertDialog("Success","open Success");
                            break;
                        }else{
                            showByAlertDialog("Failed","open failed... in out point get failed... ");
                            break;
                        }
                    }

                }
            }catch (Exception e){
                e.printStackTrace();
                showByAlertDialog("Failed", "open AOA device failed");
            }
        }
    }

    private class ReadThread extends Thread{

        public ReadThread(){

        }

        byte[] readData = new byte[2048];
        @Override
        public void run() {
            super.run();
            while (!Thread.interrupted()){
                int len = mUsbDeviceConnection.bulkTransfer(mUsbEndpointIn, readData, readData.length, 3 * 1000);
                if (len >= 0) {
                    byte[] recv = Arrays.copyOf(readData, len);
                    showLog(new String(recv));
                } else if (len == -1) {
                    showLog("ReadThread read len -1");
                } else {
                    showLog("usb host receive data failed, code:" + len);
                }
            }
        }
    }

}
