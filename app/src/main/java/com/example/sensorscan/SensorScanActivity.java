package com.example.sensorscan;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;

/**
 * Created by Long on 10/21/2015.
 */
public class SensorScanActivity extends Activity {

    private static final String TAG = "SensorScan";

    private SensorManager sensorManager;
    private Sensor accSensor;
    private Sensor gyroSensor;
    private Sensor magSensor;
    private TextView tvAccData;
    private TextView tvGyroData;
    private TextView tvMagData;
    private EditText edtDelay_acc;
    private EditText edtDelay_gyro;
    private EditText edtDelay_mag;
    private Button btnScanStart;
    private Button btnScanStop;

    private float accValues[] = new float[]{0,0,0};
    private float gyroValues[] = new float[]{0,0,0};
    private float magValues[] = new float[]{0,0,0};

    private File  accFileName;
    private File  gyroFileName;
    private File  magFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d(TAG, "on Create");
        findViews();
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        btnScanStart.setOnClickListener(btnListener);
        btnScanStop.setOnClickListener(btnListener);
    }
//按键监听
    private Button.OnClickListener btnListener = new Button.OnClickListener()
    {
        public void onClick(View v){
            switch (v.getId())
            {
                case R.id.scanStart:
                    try {
                        doStartScan();
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    break;
                case R.id.scanStop:
                    try {
                        doStopScan();
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    break;
            }
        }
    };

    //get UI
    private void findViews(){
        tvAccData = (TextView)findViewById(R.id.tvAccData);
        tvGyroData = (TextView)findViewById(R.id.tvGyroData);
        tvMagData = (TextView)findViewById(R.id.tvMagData);
        edtDelay_acc = (EditText)findViewById(R.id.delay_acc);
        edtDelay_gyro = (EditText)findViewById(R.id.delay_gyro);
        edtDelay_mag = (EditText)findViewById(R.id.delay_mag);
        btnScanStart = (Button)findViewById(R.id.scanStart);
        btnScanStop = (Button)findViewById(R.id.scanStop);
    }
    long timeStart=0;// the timestamp of the first sample，因为event.timestamp格式为long，这里是为了保证相减前不丢数据，否则后面间隔可能为负
    float timestamp;// 距离first sample 的时间间隔

    float accOldtimestamp=0;//该参数为了控制界面显示的间隔  设置为500ms
    float gyroOldtimestamp=0;
    float magOldtimestamp=0;
    //Sensor Listener
    private SensorEventListener mySensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float[] values = event.values;
            float NS2MS=1.0f/1000000.0f; //event中timestamp为纳秒，纳秒转为毫秒
            if(timeStart==0){
                timeStart=event.timestamp;
                timestamp=0;
            }
            else
                timestamp=(event.timestamp-timeStart)*NS2MS;

            StringBuilder stringBuilder = new StringBuilder();
            switch(event.sensor.getType()){
                case Sensor.TYPE_ACCELEROMETER:
                    accValues[0] = values[0];
                    accValues[1] = values[1];
                    accValues[2] = values[2];
                    stringBuilder.append(timestamp);
                    for (int i=0;i < 3;i++)
                    {
                        stringBuilder.append(" "+accValues[i]);
                    }
                    stringBuilder.append("\n");
                    try {
                        doWriteToFile(accFileName,stringBuilder.toString());
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    if(accOldtimestamp==0||timestamp-accOldtimestamp>500)//第一次或者间隔大于500ms时显示一次
                    {
                        accOldtimestamp=timestamp;
                        String accData = "X:"+accValues[0]+"\n"+"Y:"+accValues[1]+"\n"+"Z:"+accValues[2]+"\n";
                        tvAccData.setText(accData);
                    }
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    gyroValues[0] = values[0];
                    gyroValues[1] = values[1];
                    gyroValues[2] = values[2];
                    stringBuilder.append(timestamp);
                    for (int i=0;i < 3;i++)
                    {
                        stringBuilder.append(" "+gyroValues[i]);
                    }
                    stringBuilder.append("\n");
                    try {
                        doWriteToFile(gyroFileName,stringBuilder.toString());
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    if(gyroOldtimestamp==0||timestamp-gyroOldtimestamp>500) {
                        gyroOldtimestamp=timestamp;
                        String gyroData = "X:"+gyroValues[0]+"\n"+"Y:"+gyroValues[1]+"\n"+"Z:"+gyroValues[2]+"\n";
                        tvGyroData.setText(gyroData);
                    }
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    magValues[0] = values[0];
                    magValues[1] = values[1];
                    magValues[2] = values[2];
                    stringBuilder.append(timestamp);
                    for (int i=0;i < 3;i++)
                    {
                        stringBuilder.append(" "+magValues[i]);
                    }
                    stringBuilder.append("\n");
                    try {
                        doWriteToFile(magFileName,stringBuilder.toString());
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    if(magOldtimestamp==0||timestamp-magOldtimestamp>500) {
                        magOldtimestamp=timestamp;
                        String magData = "X:"+magValues[0]+"\n"+"Y:"+magValues[1]+"\n"+"Z:"+magValues[2]+"\n";
                        tvMagData.setText(magData);
                    }
                    break;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private void doStartScan() throws Exception{
        Log.d(TAG, "Create File");
        //Creat txt files for saving data
        String dateTime = android.text.format.DateFormat.format("yyyy_MM_dd_hhmm", new java.util.Date()).toString();
        accFileName = new File(getDirectory(),String.format("acc%s.txt",dateTime));
        gyroFileName = new File(getDirectory(),String.format("gyro%s.txt",dateTime));
        magFileName = new File(getDirectory(),String.format("mag%s.txt",dateTime));

        Log.d(TAG, "Start Listener");
        //设置两次sample间隔时间(ms)，仅作参考，实际可能快，可能慢
        int delayAcc=Integer.parseInt(edtDelay_acc.getText().toString());
        int delayGyro=Integer.parseInt(edtDelay_gyro.getText().toString());
        int delayMag=Integer.parseInt(edtDelay_mag.getText().toString());
        //注册Sensor监听器
        sensorManager.registerListener(mySensorListener, accSensor, delayAcc);
        sensorManager.registerListener(mySensorListener, gyroSensor, delayGyro);
        sensorManager.registerListener(mySensorListener, magSensor, delayMag);
        doNotify("Scan Starting");
    }

    private void doStopScan() throws Exception{
        Log.d(TAG,"Stop Scan");
        Log.d(TAG, "Stop Listener");
        sensorManager.unregisterListener(mySensorListener);
        doNotify("Scan Stop!");
    }

    public String getDirectory() {
        File sdcardDir =Environment.getExternalStorageDirectory();
        //得到一个路径，内容是sdcard的文件夹路径和名字
        String path=sdcardDir.getPath()+"/sensorRec";
        File path1 = new File(path);
        if (!path1.exists()) {
            //若不存在，创建目录，可以在应用启动的时候创建
            path1.mkdirs();
        }
        return String.format("%s", path1.toString());
    }
    //获取SD卡目录，在该目录下新建一个sensorRec的子目录

    //写入文件//可以直接追加在文末
    private void doWriteToFile(File file, String string) throws Exception {
        FileWriter fstream = new FileWriter(file, true);//此处true表示后续写入时，会在文件结尾续写，不会覆盖
        BufferedWriter out = new BufferedWriter(fstream);
        out.write(string);
        out.close();
    }
    //简化显示的函数
    public void doNotify(String message) {
        doNotify(message, false);
    }

    public void doNotify(String message, boolean longMessage) {
        (Toast.makeText(this, message, longMessage ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT)).show();
        Log.d(TAG, "Notify: " + message);
    }

}



