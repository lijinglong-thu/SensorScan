package com.example.sensorscan;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.example.sensorscan.filter.MeanFilter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

/**
 * Created by Long on 10/21/2015.
 */
public class SensorScanActivity extends Activity {

    private static final String TAG = "SensorScan";

    private SensorManager sensorManager;
    private LocationManager locationManager;

    private Sensor accSensor;
    private Sensor linerAccSensor;
    private Sensor gravitySensor;
    private Sensor gyroSensor;
    private Sensor gyroUnCalSensor;
    private Sensor magSensor;
    private Sensor magUnCalSensor;
    private Sensor rotVecSensor;

    private TextView tvAccData;
    private TextView tvGyroData;
    private TextView tvMagData;
    private TextView tvGPSLoc;
    private TextView tvOrientation;
    private Button btnScanStart;
    private Button btnScanStop;

    private CheckBox accSelected;
    private CheckBox linerAccSelected;
    private CheckBox gravitySelected;
    private CheckBox gyroSelected;
    private CheckBox gyroUnCalSelected;
    private CheckBox magSelected;
    private CheckBox magUnCalSelected;
    private CheckBox rotVecSelected;
    private CheckBox GPSSelected;

    private MeanFilter gravityFilter;
    private MeanFilter magneticFilter;

    private float[] accValues = new float[3];
    private float[] linerAccValues=new float[3];
    private float[] gravityValues=new float[3];
    private float[] gyroValues = new float[3];
    private float[] gyroUnCalValues=new float[3];
    private float[] magValues = new float[3];
    private float[] magUnCalValues=new float[3];
    private float[] orientationValues=new float[3];//Sensor计算的航向值
    private float[] initialRotationMatrix=new float[9];//加速度计和磁力计计算出的初始旋转矩阵
    private float[] currentRotationMatrixRaw=new float[9];
    private float[] deltaRotationVectorRaw=new float[4];
    private float[] deltaRotationMatrixRaw=new float[9];
    private float[] gyroscopeOrientationRaw=new float[3];
    private float[] accmag2OrientationMatrix=new float[9];
    private float[] accmagOrientation=new float[3];
    private float timestampOldRaw=0;
    private static final float EPSILON=0.000000001f;
    private boolean stateInitializedRaw=false;
    //为了初始校准所用
    private float[] gravityCal=new float[3];
    private float[] magCal=new float[3];

    private int gravitySampleCount=0;
    private int magneticSampleCount=0;
    private static final int MEAN_FILTER_WINDOW=10;
    private static final int MIN_SAMPLE_COUNT=200;
    private boolean hasInitialOrientation=false;
    private File  accFileName;
    private File  linerAccFileName;
    private File  gravityFileName;
    private File  gyroFileName;
    private File  gyroUnCalFileName;
    private File  magFileName;
    private File  magUnCalFileName;
    private File vec2oriFileName;
    private File gyro2oriFileName;
    private File accMag2oriFileName;
    private File GPSLocFileName;

    //采样频率
    private int sampleRate = 40;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d(TAG, "on Create");
        initUI();
        initFilters();
        initSensors();
        currentRotationMatrixRaw[0]=1.0f;
        currentRotationMatrixRaw[4]=1.0f;
        currentRotationMatrixRaw[8]=1.0f;
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
    private void initUI(){
        tvAccData = (TextView)findViewById(R.id.tvAccData);
        tvGyroData = (TextView)findViewById(R.id.tvGyroData);
        tvMagData = (TextView)findViewById(R.id.tvMagData);
        tvGPSLoc=(TextView)findViewById(R.id.tvGPSLoc);
        tvOrientation=(TextView)findViewById(R.id.tvOrientation);
        btnScanStart = (Button)findViewById(R.id.scanStart);
        btnScanStop = (Button)findViewById(R.id.scanStop);
        accSelected=(CheckBox)findViewById(R.id.accSelected);
        linerAccSelected =(CheckBox)findViewById(R.id.linerAccSelected);
        gyroSelected=(CheckBox)findViewById(R.id.gyroSelected);
        gyroUnCalSelected=(CheckBox)findViewById(R.id.gyroUnCalSelected);
        magSelected=(CheckBox)findViewById(R.id.magSelected);
        magUnCalSelected=(CheckBox)findViewById(R.id.magUnCalSelected);
        rotVecSelected=(CheckBox)findViewById(R.id.rotVecSelected);
        gravitySelected=(CheckBox)findViewById(R.id.gravitySelected);
        GPSSelected=(CheckBox)findViewById(R.id.GPSSelected);
    }
    private void initSensors(){
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        linerAccSensor= sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gyroUnCalSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
        magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magUnCalSensor=sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);
        rotVecSensor=sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        gravitySensor=sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
    }
    private void initGPS(){
        locationManager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria=new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setBearingRequired(true);
        criteria.setAltitudeRequired(false);
        criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
        String provider =locationManager.getBestProvider(criteria,true);
        Location location =locationManager.getLastKnownLocation(provider);
        updateWithNewLocation(location);
        locationManager.requestLocationUpdates(provider,1000,0,locationListener);
    }
    long timeStart=0;// the timestamp of the first sample，因为event.timestamp格式为long，这里是为了保证相减前不丢数据，否则后面间隔可能为负
    float timestamp;// 距离first sample 的时间间隔

    float accOldtimestamp=0;//该参数为了控制界面显示的间隔  设置为500ms
    float gyroOldtimestamp=0;
    float magOldtimestamp=0;
    float oriOldtimestamp=0;
    //Sensor Listener
    private SensorEventListener mySensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float NS2MS=1.0f/1000000.0f; //event中timestamp为纳秒，纳秒转为毫秒
            if(timeStart==0){
                timeStart=event.timestamp;
                timestamp=0;
            }
            else
                timestamp=(event.timestamp-timeStart)*NS2MS;
            switch(event.sensor.getType()){
                case Sensor.TYPE_ACCELEROMETER:
                    onAccSensorChanged(event.values);
                    break;
                case Sensor.TYPE_LINEAR_ACCELERATION:
                    onLinerAccSensorChanged(event.values);
                    break;
                case Sensor.TYPE_GRAVITY:
                    gravityValues=event.values.clone();
                    onGravitySensorChanged(event.values);
                    break;
                case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                    onGyroUnCalSensorChanged(event.values);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    onGyroSensorChanged(event.values);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    onMagSensorChanged(event.values);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                    onMagUnCalSensorChanged(event.values);
                    break;
                case Sensor.TYPE_ROTATION_VECTOR:
                    onRotVecSensorChanged(event.values);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };
    //*Locarion 变化时调用
    private final LocationListener locationListener=new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            updateWithNewLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };
    private void updateWithNewLocation(Location location){
        String locData;
        if(location!=null){
            double lat=location.getLatitude();
            double lng=location.getLongitude();
            double bear=location.getBearing();
            locData=timestamp+" "+lat+" "+lng+" "+bear+"\n";
        }
        else {
            locData=timestamp+" -1 -1 -1\n";
        }
        try {
            doWriteToFile(GPSLocFileName,locData);
        }catch (Exception e){
            e.printStackTrace();
        }
        tvGPSLoc.setText(locData);
    }
    private void onAccSensorChanged(float[] acc){

        //显示存储
        StringBuilder stringBuilder=new StringBuilder();
        accValues[0] = acc[0];
        accValues[1] = acc[1];
        accValues[2] = acc[2];
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
            String accData = "X:"+accValues[0]+"Y:"+accValues[1]+"Z:"+accValues[2];
            tvAccData.setText(accData);
        }
    }
    private void onLinerAccSensorChanged(float[] acc){

        //显示存储
        StringBuilder stringBuilder=new StringBuilder();
        linerAccValues[0] = acc[0];
        linerAccValues[1] = acc[1];
        linerAccValues[2] = acc[2];
        stringBuilder.append(timestamp);
        for (int i=0;i < 3;i++)
        {
            stringBuilder.append(" "+linerAccValues[i]);
        }
        stringBuilder.append("\n");
        try {
            doWriteToFile(linerAccFileName,stringBuilder.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private void onGravitySensorChanged(float[] gravity){
        if(!hasInitialOrientation) {
            //Calibrate
            // Get a local copy of the raw magnetic values from the device sensor.
            System.arraycopy(gravity, 0, this.gravityCal, 0, gravity.length);

            // Use a mean filter to smooth the sensor inputs
            this.gravityCal = gravityFilter.filterFloat(this.gravityCal);

            // Count the number of samples received.
            gravitySampleCount++;

            // Only determine the initial orientation after the acceleration sensor
            // and magnetic sensor have had enough time to be smoothed by the mean
            // filters. Also, only do this if the orientation hasn't already been
            // determined since we only need it once.
            if (gravitySampleCount > MIN_SAMPLE_COUNT
                    && magneticSampleCount > MIN_SAMPLE_COUNT
                    && !hasInitialOrientation) {
                calculateOrientation();
            }
        }
        //显示存储
        StringBuilder stringBuilder=new StringBuilder();
        gravityValues[0] = gravity[0];
        gravityValues[1] = gravity[1];
        gravityValues[2] = gravity[2];
        stringBuilder.append(timestamp);
        for (int i=0;i < 3;i++)
        {
            stringBuilder.append(" "+gravityValues[i]);
        }
        stringBuilder.append("\n");
        try {
            doWriteToFile(gravityFileName,stringBuilder.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private void onMagSensorChanged(float[] mag){
        //Calibrate
        if(!hasInitialOrientation){
        // Get a local copy of the raw magnetic values from the device sensor.
        System.arraycopy(mag, 0, this.magCal, 0, mag.length);

        // Use a mean filter to smooth the sensor inputs
        this.magCal= magneticFilter.filterFloat(this.magCal);

        // Count the number of samples received.
        magneticSampleCount++;
        }
        accmag2Orientation();
        StringBuilder stringBuilder=new StringBuilder();
        magValues[0] = mag[0];
        magValues[1] = mag[1];
        magValues[2] = mag[2];
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
            String magData = "X:"+magValues[0]+"Y:"+magValues[1]+"Z:"+magValues[2];
            tvMagData.setText(magData);
        }
    }
    private void onMagUnCalSensorChanged(float[] magUnCal){
        StringBuilder stringBuilder=new StringBuilder();
        magUnCalValues[0] = magUnCal[0];
        magUnCalValues[1] = magUnCal[1];
        magUnCalValues[2] = magUnCal[2];
        stringBuilder.append(timestamp);
        for (int i=0;i < 3;i++)
        {
            stringBuilder.append(" "+magUnCalValues[i]);
        }
        stringBuilder.append("\n");
        try {
            doWriteToFile(magUnCalFileName,stringBuilder.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private void onGyroSensorChanged(float[] gyro){
        StringBuilder stringBuilder=new StringBuilder();
        gyroValues[0] = gyro[0];
        gyroValues[1] = gyro[1];
        gyroValues[2] = gyro[2];
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
    }
    private void onGyroUnCalSensorChanged(float[] gyroUnCal){
        onGyro2orientation(gyroUnCal);
        StringBuilder stringBuilder=new StringBuilder();
        gyroUnCalValues[0] = gyroUnCal[0];
        gyroUnCalValues[1] = gyroUnCal[1];
        gyroUnCalValues[2] = gyroUnCal[2];
        stringBuilder.append(timestamp);
        for (int i=0;i < 3;i++)
        {
            stringBuilder.append(" "+gyroUnCalValues[i]);
        }
        stringBuilder.append("\n");
        try {
            doWriteToFile(gyroUnCalFileName,stringBuilder.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
        if(gyroOldtimestamp==0||timestamp-gyroOldtimestamp>500) {
            gyroOldtimestamp=timestamp;
            String gyroData = "X:"+gyroValues[0]+"Y:"+gyroValues[1]+"Z:"+gyroValues[2];
            tvGyroData.setText(gyroData);
        }
    }
    private void onRotVecSensorChanged(float[] rotVec){
        StringBuilder stringBuilder=new StringBuilder();
        float[] vec2RotationMatrix=new float[9];
        SensorManager.getRotationMatrixFromVector(vec2RotationMatrix,rotVec);
        SensorManager.getOrientation(vec2RotationMatrix, orientationValues);
        stringBuilder.append(timestamp);
        for (int i=0;i < 3;i++)
        {
            stringBuilder.append(" "+orientationValues[i]);
        }
        stringBuilder.append("\n");
        try {
            doWriteToFile(vec2oriFileName,stringBuilder.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
        if(oriOldtimestamp==0||timestamp-oriOldtimestamp>500) {
            oriOldtimestamp=timestamp;
            tvOrientation.setText(stringBuilder.toString());
        }

    }
    /**
     * Calculates orientation angles from accelerometer and magnetometer output.
     * Note that we only use this *once* at the beginning to orient the
     * gyroscope to earth frame. If you do not call this, the gyroscope will
     * orient itself to whatever the relative orientation the device is in at
     * the time of initialization.
     */
    private void calculateOrientation(){
        hasInitialOrientation = SensorManager.getRotationMatrix(
                initialRotationMatrix, null,gravityCal,magCal);
        doNotify("Cal done!");
    }
    private void accmag2Orientation(){
        if(gravityValues!=null&&magValues!=null){
            SensorManager.getRotationMatrix(accmag2OrientationMatrix, null,gravityValues,magValues);
            SensorManager.getOrientation(accmag2OrientationMatrix,accmagOrientation);
            StringBuilder stringBuilder=new StringBuilder();
            stringBuilder.append(timestamp);
            for (int i=0;i < 3;i++)
            {
                stringBuilder.append(" "+accmagOrientation[i]);
            }
            stringBuilder.append("\n");
            try {
                doWriteToFile(accMag2oriFileName,stringBuilder.toString());
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    /**
     * Initialize the mean filters.
     */
    private void initFilters()
    {
        gravityFilter = new MeanFilter();
        gravityFilter.setWindowSize(MEAN_FILTER_WINDOW);

        magneticFilter = new MeanFilter();
        magneticFilter.setWindowSize(MEAN_FILTER_WINDOW);
    }
    public void onGyro2orientation(float[] gyroscope)
    {
        // don't start until first accelerometer/magnetometer orientation has
        // been acquired
        if (!hasInitialOrientation)
        {
            return;
        }

        // Initialization of the gyroscope based rotation matrix
        if (!stateInitializedRaw)
        {
            currentRotationMatrixRaw = matrixMultiplication(
                    currentRotationMatrixRaw, initialRotationMatrix);
            stateInitializedRaw = true;
        }
        // This timestep's delta rotation to be multiplied by the current
        // rotation after computing it from the gyro sample data.
        if (timestampOldRaw != 0 && stateInitializedRaw)
        {
            final float dT = (timestamp - timestampOldRaw) * 0.001f;

            // Axis of the rotation sample, not normalized yet.
            float axisX = gyroscope[0];
            float axisY = gyroscope[1];
            float axisZ = gyroscope[2];

            // Calculate the angular speed of the sample
            float omegaMagnitude = (float) Math.sqrt(axisX * axisX + axisY
                    * axisY + axisZ * axisZ);

            // Normalize the rotation vector if it's big enough to get the axis
            if (omegaMagnitude > EPSILON)
            {
                axisX /= omegaMagnitude;
                axisY /= omegaMagnitude;
                axisZ /= omegaMagnitude;
            }

            // Integrate around this axis with the angular speed by the timestep
            // in order to get a delta rotation from this sample over the
            // timestep. We will convert this axis-angle representation of the
            // delta rotation into a quaternion before turning it into the
            // rotation matrix.
            float thetaOverTwo = omegaMagnitude * dT / 2.0f;

            float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
            float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);

            deltaRotationVectorRaw[0] = sinThetaOverTwo * axisX;
            deltaRotationVectorRaw[1] = sinThetaOverTwo * axisY;
            deltaRotationVectorRaw[2] = sinThetaOverTwo * axisZ;
            deltaRotationVectorRaw[3] = cosThetaOverTwo;

            SensorManager.getRotationMatrixFromVector(deltaRotationMatrixRaw,
                    deltaRotationVectorRaw);

            currentRotationMatrixRaw = matrixMultiplication(
                    currentRotationMatrixRaw, deltaRotationMatrixRaw);

            SensorManager.getOrientation(currentRotationMatrixRaw,
                    gyroscopeOrientationRaw);
        }
        StringBuilder gyro2ori=new StringBuilder();
        gyro2ori.append(timestamp);
        for (int i=0;i < 3;i++)
        {
            gyro2ori.append(" "+gyroscopeOrientationRaw[i]);
        }
        gyro2ori.append("\n");
        try {
            doWriteToFile(gyro2oriFileName,gyro2ori.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
        timestampOldRaw = timestamp;
    }
    /**
     * Multiply matrix a by b. Android gives us matrices results in
     * one-dimensional arrays instead of two, so instead of using some (O)2 to
     * transfer to a two-dimensional array and then an (O)3 algorithm to
     * multiply, we just use a static linear time method.
     *
     * @param a
     * @param b
     * @return a*b
     */
    private float[] matrixMultiplication(float[] a, float[] b)
    {
        float[] result = new float[9];

        result[0] = a[0] * b[0] + a[1] * b[3] + a[2] * b[6];
        result[1] = a[0] * b[1] + a[1] * b[4] + a[2] * b[7];
        result[2] = a[0] * b[2] + a[1] * b[5] + a[2] * b[8];

        result[3] = a[3] * b[0] + a[4] * b[3] + a[5] * b[6];
        result[4] = a[3] * b[1] + a[4] * b[4] + a[5] * b[7];
        result[5] = a[3] * b[2] + a[4] * b[5] + a[5] * b[8];

        result[6] = a[6] * b[0] + a[7] * b[3] + a[8] * b[6];
        result[7] = a[6] * b[1] + a[7] * b[4] + a[8] * b[7];
        result[8] = a[6] * b[2] + a[7] * b[5] + a[8] * b[8];

        return result;
    }
    private void doStartScan() throws Exception{
        Log.d(TAG, "Create File");
        //Creat txt files for saving data
        String dateTime = android.text.format.DateFormat.format("yyyy_MM_dd_hhmm", new java.util.Date()).toString();
        accFileName = new File(getDirectory(),String.format("acc%s.txt",dateTime));
        linerAccFileName=new File(getDirectory(),String.format("linerAcc%s.txt",dateTime));
        gravityFileName=new File(getDirectory(),String.format("gravity%s.txt",dateTime));
        gyroFileName = new File(getDirectory(),String.format("gyro%s.txt",dateTime));
        gyroUnCalFileName = new File(getDirectory(),String.format("gyroUnCal%s.txt",dateTime));
        magFileName = new File(getDirectory(),String.format("mag%s.txt",dateTime));
        magUnCalFileName = new File(getDirectory(),String.format("magUnCal%s.txt",dateTime));
        vec2oriFileName=new File(getDirectory(),String.format("vec2ori%s.txt",dateTime));
        gyro2oriFileName=new File(getDirectory(),String.format("gyro2ori%s.txt",dateTime));
        accMag2oriFileName=new File(getDirectory(),String.format("accMag2ori%s.txt",dateTime));
        GPSLocFileName=new File(getDirectory(),String.format("GPSLoc%s.txt",dateTime));

        Log.d(TAG, "Start Listener");
        updateSelectedSensor();
        //设置两次sample间隔时间(ms)，仅作参考，实际可能快，可能慢
        /*int delayAcc=Integer.parseInt(edtDelay_acc.getText().toString());
        int delayGyro=Integer.parseInt(edtDelay_gyro.getText().toString());
        int delayMag=Integer.parseInt(edtDelay_mag.getText().toString());
        //注册Sensor监听器
        sensorManager.registerListener(mySensorListener, accSensor, delayAcc);
        sensorManager.registerListener(mySensorListener, gyroUnCalSensor, delayGyro);
        sensorManager.registerListener(mySensorListener, magSensor, delayMag);*/
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
    //更新显示
    private void updateSelectedSensor(){
        sensorManager.unregisterListener(mySensorListener);
        if (accSelected.isChecked()){
            sensorManager.registerListener(mySensorListener,accSensor,1000/sampleRate);
        }
        if (linerAccSelected.isChecked()){
            sensorManager.registerListener(mySensorListener,linerAccSensor,1000/sampleRate);
        }
        if(gravitySelected.isChecked()){
            sensorManager.registerListener(mySensorListener,gravitySensor,1000/sampleRate);
        }
        if(gyroUnCalSelected.isChecked()){
            sensorManager.registerListener(mySensorListener,gyroUnCalSensor,1000/sampleRate);
        }
        if(gyroSelected.isChecked()){
            sensorManager.registerListener(mySensorListener,gyroSensor,1000/sampleRate);
        }
        if(magSelected.isChecked()){
            sensorManager.registerListener(mySensorListener,magSensor,1000/sampleRate);
        }
        if(magUnCalSelected.isChecked()){
            sensorManager.registerListener(mySensorListener,magUnCalSensor,1000/sampleRate);
        }
        if(rotVecSelected.isChecked()){
            sensorManager.registerListener(mySensorListener,rotVecSensor,1000/sampleRate);
        }
        if(GPSSelected.isChecked()){
            initGPS();
        }
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



