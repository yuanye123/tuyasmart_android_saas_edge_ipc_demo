package com.tuya.ai.ipcsdkdemo;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;

import com.alibaba.fastjson.JSON;
import com.tuya.ai.ipcsdkdemo.audio.FileAudioCapture;
import com.tuya.ai.ipcsdkdemo.video.VideoCapture;
import com.tuya.edge.atop.AtopFacade;
import com.tuya.edge.client.api.user.TenementReceiveEvent;
import com.tuya.edge.client.model.EventContext;
import com.tuya.edge.client.model.user.TenementRequest;
import com.tuya.edge.enums.QrcodeEnum;
import com.tuya.edge.init.EdgeEventManager;
import com.tuya.edge.init.EdgeNetConfigManager;
import com.tuya.edge.init.MediaParamConfigCallback;
import com.tuya.edge.model.request.ThirdEventRequest;
import com.tuya.edge.model.vo.DeviceVO;
import com.tuya.edge.mqtt.MqttEventSupport;
import com.tuya.edge.utils.AESUtils;
import com.tuya.smart.aiipc.base.permission.PermissionUtil;

import com.tuya.smart.aiipc.ipc_sdk.api.Common;
import com.tuya.smart.aiipc.ipc_sdk.api.IMediaTransManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IParamConfigManager;
import com.tuya.smart.aiipc.ipc_sdk.service.IPCServiceManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "IPC_DEMO";

    private  SurfaceView surfaceView;

    private VideoCapture videoCapture;

    private FileAudioCapture fileAudioCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.call).setOnClickListener(v -> {
            IMediaTransManager mediaTransManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);

            try {
                InputStream fileStream = getAssets().open("leijun.jpeg");

                byte[] buffer = new byte[2048];
                int bytesRead;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                while ((bytesRead = fileStream.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                byte[] file = output.toByteArray();

                mediaTransManager.sendDoorBellCallForPress(file, Common.NOTIFICATION_CONTENT_TYPE_E.NOTIFICATION_CONTENT_JPEG);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        //三方设备id
        String cid = "device164114432";
        String basePath = "/sdcard/";
        String recordPath = "/sdcard/";

        //二维码信息Map
        String secret = "1157ae641781c000";

        Map<String,String> qrcodeMap = new HashMap<>();
        qrcodeMap.put(QrcodeEnum.TOKEN.getCode(),"");
        qrcodeMap.put(QrcodeEnum.UUID.getCode(),"");
        qrcodeMap.put(QrcodeEnum.AUTH_KEY.getCode(), "");

        qrcodeMap.put(QrcodeEnum.PROJECT_ID.getCode(),"linlin_area");
        qrcodeMap.put(QrcodeEnum.PID.getCode(),"toftl4za2qhgf8uc");
        qrcodeMap.put(QrcodeEnum.VENDOR.getCode(),"lilin");
        qrcodeMap.put(QrcodeEnum.INSTALL_LOCATION.getCode(),"华策中心A栋");

        //实现类的配置
        Properties properties = new  Properties();
        properties.put("dc_userInfo","com.tuya.ai.ipcsdkdemo.edge.TenementReceiveEventImpl");
        properties.put("dc_door","com.tuya.ai.ipcsdkdemo.edge.DoorReceiveEventImpl");
        //人脸数据同步
       //  properties.put("dc_faceInfo","com.tuya.ai.ipcsdkdemo.edge.FaceImageReceiveEventImpl");
        // 卡数据同步
       //   properties.put("dn_cardInfo","com.tuya.ai.ipcsdkdemo.edge.CardReceiveEventImpl");
        // 二维码数据同步
        //  properties.put("dc_qrCodeInfo","com.tuya.ai.ipcsdkdemo.edge.QcCodeReceiveEventImpl");

        PermissionUtil.check(this, new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.RECORD_AUDIO
        }, ()-> initSDK(this, cid, qrcodeMap, basePath, recordPath, properties, secret, new MediaParamConfigCallback(){
            public void initMediaParamConfig(){
                LoadParamConfig();
            }
        }));

    }

    /**
     * 扫二维码配网及设备重启时调用
     *
     * @param  ctx          上下文
     * @param  cid          三方设备id
     * @param  qrcodeMap    二维码信息Map
     * @param  basePath     可写的一个路径，用于存储SDK相关的配置
     * @param  recordPath   可写的一个路径，用于存储录像
     * @param  properties   实现类的配置
     * @param  secret
     * @param  paramConfigCallBack
     */
    private void initSDK(Context ctx, String cid, Map<String,String> qrcodeMap, String basePath, String recordPath,
                         Properties properties,String secret, MediaParamConfigCallback paramConfigCallBack) {

        EdgeNetConfigManager.getInstance().initSDK(ctx,cid,qrcodeMap,basePath,recordPath,properties,secret, paramConfigCallBack);

        runOnUiThread(() -> findViewById(R.id.call).setEnabled(true));

        //视频流（相机）
        videoCapture = new VideoCapture(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN);
        videoCapture.startVideoCapture();

        //音频流（本地文件）
        fileAudioCapture = new FileAudioCapture(ctx);
        fileAudioCapture.startFileCapture();

        IMediaTransManager mediaTransManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);
        mediaTransManager.setDoorBellCallStatusCallback(status -> {
            /**
             * 门铃呼叫报警接听状态
             * status = -1 未知状态
             * status = 0 接听
             * status = 1 挂断
             * status = 2 通话中心跳
             * {@link Common.DoorBellCallStatus}
             * */
            Log.d(TAG, "doorbell back: " + status);

        });
    }

    /**
     * 音视频参数配置
     */
    private void LoadParamConfig() {
        IParamConfigManager configManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_PARAM_SERVICE);

        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_WIDTH, 1280);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_HEIGHT, 720);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_FRAME_RATE, 24);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_I_FRAME_INTERVAL, 2);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_BIT_RATE, 1024000);

        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_CHANNEL_NUM, 1);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_SAMPLE_RATE, 8000);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_SAMPLE_BIT, 16);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_FRAME_RATE, 25);
    }
}
