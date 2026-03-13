package com.example.autofeed3;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.SocketException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private wifiTcp tcp;
    private EditText etIp;
    private EditText etPort;
    private Button btnConnect;
    private Button btnClearData;
    private Button btnRefreshChart;
    
    // 统计图切换按钮
    private Button btnDaily;
    private Button btnWeekly;
    private Button btnMonthly;

    private ToggleButton btnManualFeed;
    private ToggleButton btnLight;
    
    private TextView tvStatus;
    private TextView tvFoodRemaining;
    private TextView tvPetName;
    
    private LineChart lineChart;

    private spData data;

    // UDP接收器
    private UdpReceiver receiver;
    private Thread udpThread;
    
    // 当前体重值
    private int currentWeight = 0;
    
    // 每个宠物的上一次体重值（用于比较计算进食量）
    private Map<String, Integer> lastWeightMap = new HashMap<>();
    
    // 当前宠物信息
    private String currentPetName = "阿柴";
    
    // 数据文件名
    private static final String DATA_FILE_NAME = "weight_data.txt";
    private static final String FEED_DATA_FILE_PREFIX = ""; // 进食数据文件前缀（空，直接使用文件名）
    
    // 宠物数据文件名
    private static final String[] PET_FILE_NAMES = {"achai.txt", "tusong.txt"};
    
    // 图表显示模式
    private enum ChartMode {
        DAILY,   // 日统计
        WEEKLY,  // 周统计
        MONTHLY  // 月统计
    }
    private ChartMode currentChartMode = ChartMode.DAILY;
    
    // 宠物RFID UID定义
    private static final byte[][] PET_UIDS = {
        {(byte)0xF5, (byte)0x95, (byte)0x25, (byte)0x07},  // 阿柴
        {(byte)0xBB, (byte)0xFB, (byte)0x01, (byte)0x07}   // 土松
    };
    
    private static final String[] PET_NAMES = {"阿柴", "土松"};
    
    // 所有可能的宠物名称（包括默认的"阿柴"）
    private static final String[] ALL_PET_NAMES = {"阿柴", "土松"};
    
    // 折线图颜色
    private static final int[] PET_COLORS = {
        0xFF2196F3,  // 阿柴：蓝色
        0xFFFF9800   // 土松：橙色
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 初始化控件
        etIp = findViewById(R.id.etIp);
        etPort = findViewById(R.id.etPort);
        btnConnect = findViewById(R.id.btnConnect);
        tvStatus = findViewById(R.id.tvStatus);
        tvFoodRemaining = findViewById(R.id.tvFoodRemaining);
        tvPetName = findViewById(R.id.petName);
        btnManualFeed = findViewById(R.id.btnManualFeed);
        btnLight = findViewById(R.id.btnLight);
        btnClearData = findViewById(R.id.btnClearData);
        btnRefreshChart = findViewById(R.id.btnRefreshChart);
        btnDaily = findViewById(R.id.btnDaily);
        btnWeekly = findViewById(R.id.btnWeekly);
        btnMonthly = findViewById(R.id.btnMonthly);
        lineChart = findViewById(R.id.lineChart);

        data = new spData();
        data.init(this); // 初始化 SharedPreferences
        
        // 初始化折线图
        setupChart();
        
        // 加载并显示图表数据（默认日统计）
        loadChartData();
        
        // 设置清除数据按钮事件
        setupClearButton();
        
        // 设置统计图切换按钮事件
        setupChartButtons();
        
        // 设置刷新图表按钮事件
        btnRefreshChart.setOnClickListener(v -> {
            loadChartData();
            Toast.makeText(this, "图表已刷新", Toast.LENGTH_SHORT).show();
        });
        // 初始化 TCP 客户端
//        食物剩余：0000    投喂次数：0000  手动投喂：00 手动投喂重量：0000 自动投喂：00
//        tvFoodRemaining  tvFeedCount
//        foodRemaining    feedCount
//        substring(0,n) 零可以取到，n取不到

        tcp = new wifiTcp();

        // 初始化UDP接收器
        initUdpReceiver();

        tcp.setMessageCallback(new wifiTcp.MessageCallback() {
            @Override
            public void onMessageReceived(String message) {
                // 接收到TCP消息的处理
                // 更新食物余量
                updateFoodRemaining(message);
            }
//                feedCount=message.substring(4,8);
//
//                tvFoodRemaining.setText(foodRemainingt);
//                tvFeedCount.setText(feedCount);
   //         }

            @Override
            public void onConnectionStatusChanged(boolean connected) {
                // 更新连接状态显示
                if (connected) {
                    tvStatus.setText("已连接");
                    tvStatus.setTextColor(0xFF00FF00); // 绿色
                    btnConnect.setText("断开");
                } else {
                    tvStatus.setText("未连接");
                    tvStatus.setTextColor(0xFFFF0000); // 红色
                    btnConnect.setText("连接");
                }
            }

            @Override
            public void onError(String error) {
                // 显示错误信息
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });


        // 连接/断开按钮点击事件
        btnConnect.setOnClickListener(v -> {
            if (tcp.isConnected()) {
                // 如果已连接，则断开
                tcp.disconnect();
            } else {
                // 如果未连接，则连接
                connectToServer();
            }
        });

        // 可选：启动时自动连接（取消注释下面这行）
        // connectToServer();

        // 手动投喂按钮
        btnManualFeed.setOnCheckedChangeListener((buttonView,isChecked) -> {
            if (!tcp.isConnected()) {
                Toast.makeText(this, "请先连接服务器", Toast.LENGTH_SHORT).show();
                return;
            }
            if(isChecked){
                //开
                tcp.sendMessage("MOTOR_ON");
                Toast.makeText(this, "手动喂食开", Toast.LENGTH_SHORT).show();
            }else {
                //关
                tcp.sendMessage("MOTOR_OFF");
                Toast.makeText(this, "手动喂食关", Toast.LENGTH_SHORT).show();

            }

        });
        
        // 灯开关
        btnLight.setOnCheckedChangeListener((buttonView,isChecked) -> {
            if (!tcp.isConnected()) {
                Toast.makeText(this, "请先连接服务器", Toast.LENGTH_SHORT).show();
                return;
            }
            if(isChecked){
                //开
                tcp.sendMessage("RED_ON");
                Toast.makeText(this, "灯开", Toast.LENGTH_SHORT).show();
            }else {
                //关
                tcp.sendMessage("RED_OFF");
                Toast.makeText(this, "灯关", Toast.LENGTH_SHORT).show();

            }

        });
    }

    /**
     * 连接到服务器
     */
    private void connectToServer() {
        String ip = etIp.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        
        if (ip.isEmpty() || portStr.isEmpty()) {
            Toast.makeText(this, "请输入IP和端口", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            int port = Integer.parseInt(portStr);
            // 直接调用 connect 函数连接
            tcp.connect(ip, port);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "端口格式错误", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 页面销毁时断开TCP连接
        if (tcp != null) {
            tcp.disconnect();
        }
        
        // 停止UDP接收器
        if (receiver != null) {
            receiver.stop();
        }
        
        // 中断UDP线程
        if (udpThread != null && udpThread.isAlive()) {
            udpThread.interrupt();
        }
    }
    
    /**
     * 设置统计图切换按钮事件
     */
    private void setupChartButtons() {
        // 日统计按钮
        btnDaily.setOnClickListener(v -> {
            currentChartMode = ChartMode.DAILY;
            loadChartData();
            Toast.makeText(this, "切换到日统计", Toast.LENGTH_SHORT).show();
        });
        
        // 周统计按钮
        btnWeekly.setOnClickListener(v -> {
            currentChartMode = ChartMode.WEEKLY;
            loadChartData();
            Toast.makeText(this, "切换到周统计", Toast.LENGTH_SHORT).show();
        });
        
        // 月统计按钮
        btnMonthly.setOnClickListener(v -> {
            currentChartMode = ChartMode.MONTHLY;
            loadChartData();
            Toast.makeText(this, "切换到月统计", Toast.LENGTH_SHORT).show();
        });
    }
    
    /**
     * 设置清除数据按钮事件
     */
    private void setupClearButton() {
        // 清除数据按钮
        btnClearData.setOnClickListener(v -> {
            // 弹出确认对话框
            new android.app.AlertDialog.Builder(this)
                .setTitle("确认清除")
                .setMessage("确定要清除所有保存的数据文件吗？此操作不可恢复。")
                .setPositiveButton("确定", (dialog, which) -> {
                    // 清除所有数据文件
                    clearAllDataFiles();
                    // 刷新图表（显示空图表）
                    loadChartData();
                    Toast.makeText(this, "已清除所有数据", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
        });
    }
    
    /**
     * 初始化折线图设置
     */
    private void setupChart() {
        // 图表描述（不显示，因为标题已经在上方）
        Description description = new Description();
        description.setEnabled(false);
        lineChart.setDescription(description);

        // 启用触摸手势
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);

        // X轴设置
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(10f);
        xAxis.setLabelRotationAngle(-45f); // 标签旋转45度，避免重叠

        // Y轴设置
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextSize(10f);

        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false);

        // 图例设置（显示在图表上方）
        lineChart.getLegend().setEnabled(true);
        lineChart.getLegend().setTextSize(14f);
        lineChart.getLegend().setVerticalAlignment(com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP);
        lineChart.getLegend().setHorizontalAlignment(com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER);
        lineChart.getLegend().setOrientation(com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL);
        lineChart.getLegend().setDrawInside(false);
        lineChart.getLegend().setXEntrySpace(20f);
    }
    
    /**
     * 从文件中加载数据并更新图表
     * 根据当前图表模式（日/周/月）加载对应的进食量统计数据
     * 支持多个宠物的数据同时显示，使用不同颜色区分
     */
    private void loadChartData() {
        switch (currentChartMode) {
            case DAILY:
                loadDailyChartData();
                break;
            case WEEKLY:
                loadWeeklyChartData();
                break;
            case MONTHLY:
                loadMonthlyChartData();
                break;
        }
    }
    
    /**
     * 加载日统计数据（今天每小时的进食量）
     */
    private void loadDailyChartData() {
        List<String> labels = new ArrayList<>();
        List<LineDataSet> dataSets = new ArrayList<>();
        
        // 为每个宠物创建数据集（包括默认的"猫"）
        for (int petIndex = 0; petIndex < ALL_PET_NAMES.length; petIndex++) {
            String petName = ALL_PET_NAMES[petIndex];
            List<Entry> entries = new ArrayList<>();
            
            // 初始化24小时的数据（0-23点）
            int[] hourlyFeed = new int[24];
            
            try {
                // 读取宠物的进食数据文件
                String fileName = getPetFileName(petName);
                File file = new File(getFilesDir(), fileName);
                
                if (file.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String line;
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    String today = dateFormat.format(new Date());
                    
                    // 读取今天的数据
                    while ((line = reader.readLine()) != null) {
                        try {
                            String[] parts = line.split(",");
                            if (parts.length == 2) {
                                String timestamp = parts[0].trim();
                                int feedAmount = Integer.parseInt(parts[1].trim());
                                
                                // 检查是否是今天的数据
                                if (timestamp.startsWith(today)) {
                                    Date date = sdf.parse(timestamp);
                                    if (date != null) {
                                        Calendar cal = Calendar.getInstance();
                                        cal.setTime(date);
                                        int hour = cal.get(Calendar.HOUR_OF_DAY);
                                        hourlyFeed[hour] += feedAmount;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e("MainActivity", "解析数据行出错: " + line);
                        }
                    }
                    reader.close();
                }
            } catch (IOException e) {
                Log.e("MainActivity", "读取" + petName + "数据文件失败: " + e.getMessage());
            }
            
            // 将小时数据转换为图表数据点
            for (int hour = 0; hour < 24; hour++) {
                entries.add(new Entry(hour, hourlyFeed[hour]));
            }
            
            // 创建数据集（只有有数据的宠物才添加到图表）
            boolean hasData = false;
            for (int feed : hourlyFeed) {
                if (feed > 0) {
                    hasData = true;
                    break;
                }
            }
            
            if (hasData) {
                LineDataSet dataSet = new LineDataSet(entries, petName);
                dataSet.setColor(PET_COLORS[petIndex]);
                dataSet.setCircleColor(PET_COLORS[petIndex]);
                dataSet.setLineWidth(2f);
                dataSet.setCircleRadius(3f);
                dataSet.setDrawCircleHole(false);
                dataSet.setValueTextSize(9f);
                dataSet.setDrawFilled(true);
                dataSet.setFillColor(PET_COLORS[petIndex]);
                dataSet.setFillAlpha(30);
                dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                dataSets.add(dataSet);
            }
        }
        
        // 生成X轴标签（0-23点）
        for (int hour = 0; hour < 24; hour++) {
            labels.add(hour + "时");
        }
        
        // 更新图表
        updateMultiLineChart(dataSets, labels, "今日进食量统计(g)");
    }
    
    /**
     * 加载周统计数据（过去7天每天的进食量）
     */
    private void loadWeeklyChartData() {
        List<String> labels = new ArrayList<>();
        List<LineDataSet> dataSets = new ArrayList<>();
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat labelFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());
        
        // 为每个宠物创建数据集（包括默认的"猫"）
        for (int petIndex = 0; petIndex < ALL_PET_NAMES.length; petIndex++) {
            String petName = ALL_PET_NAMES[petIndex];
            List<Entry> entries = new ArrayList<>();
            
            boolean hasData = false;
            
            // 获取过去7天的数据
            for (int dayOffset = 6; dayOffset >= 0; dayOffset--) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, -dayOffset);
                String dateKey = dateFormat.format(cal.getTime());
                
                int dailyFeed = getDailyFeedAmount(petName, dateKey);
                entries.add(new Entry(6 - dayOffset, dailyFeed));
                
                if (dailyFeed > 0) {
                    hasData = true;
                }
                
                // 只在第一个宠物时生成标签
                if (petIndex == 0) {
                    labels.add(labelFormat.format(cal.getTime()));
                }
            }
            
            // 创建数据集（只有有数据的宠物才添加到图表）
            if (hasData) {
                LineDataSet dataSet = new LineDataSet(entries, petName);
                dataSet.setColor(PET_COLORS[petIndex]);
                dataSet.setCircleColor(PET_COLORS[petIndex]);
                dataSet.setLineWidth(2f);
                dataSet.setCircleRadius(4f);
                dataSet.setDrawCircleHole(false);
                dataSet.setValueTextSize(9f);
                dataSet.setDrawFilled(true);
                dataSet.setFillColor(PET_COLORS[petIndex]);
                dataSet.setFillAlpha(30);
                dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                dataSets.add(dataSet);
            }
        }
        
        // 更新图表
        updateMultiLineChart(dataSets, labels, "周进食量统计(g)");
    }
    
    /**
     * 加载月统计数据（过去30天每天的进食量）
     */
    private void loadMonthlyChartData() {
        List<String> labels = new ArrayList<>();
        List<LineDataSet> dataSets = new ArrayList<>();
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat labelFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());
        
        // 为每个宠物创建数据集（包括默认的"猫"）
        for (int petIndex = 0; petIndex < ALL_PET_NAMES.length; petIndex++) {
            String petName = ALL_PET_NAMES[petIndex];
            List<Entry> entries = new ArrayList<>();
            
            boolean hasData = false;
            
            // 获取过去30天的数据
            for (int dayOffset = 29; dayOffset >= 0; dayOffset--) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, -dayOffset);
                String dateKey = dateFormat.format(cal.getTime());
                
                int dailyFeed = getDailyFeedAmount(petName, dateKey);
                entries.add(new Entry(29 - dayOffset, dailyFeed));
                
                if (dailyFeed > 0) {
                    hasData = true;
                }
                
                // 只在第一个宠物时生成标签（每3天显示一个标签）
                if (petIndex == 0) {
                    if (dayOffset % 3 == 0) {
                        labels.add(labelFormat.format(cal.getTime()));
                    } else {
                        labels.add("");
                    }
                }
            }
            
            // 创建数据集（只有有数据的宠物才添加到图表）
            if (hasData) {
                LineDataSet dataSet = new LineDataSet(entries, petName);
                dataSet.setColor(PET_COLORS[petIndex]);
                dataSet.setCircleColor(PET_COLORS[petIndex]);
                dataSet.setLineWidth(2f);
                dataSet.setCircleRadius(3f);
                dataSet.setDrawCircleHole(false);
                dataSet.setValueTextSize(9f);
                dataSet.setDrawFilled(true);
                dataSet.setFillColor(PET_COLORS[petIndex]);
                dataSet.setFillAlpha(30);
                dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                dataSets.add(dataSet);
            }
        }
        
        // 更新图表
        updateMultiLineChart(dataSets, labels, "月进食量统计(g)");
    }
    
    /**
     * 获取指定日期的进食总量
     * 
     * @param petName 宠物名称
     * @param dateKey 日期键（格式：yyyy-MM-dd）
     * @return 当天进食总量
     */
    private int getDailyFeedAmount(String petName, String dateKey) {
        int totalFeed = 0;
        
        try {
            String fileName = getPetFileName(petName);
            File file = new File(getFilesDir(), fileName);
            
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                
                while ((line = reader.readLine()) != null) {
                    try {
                        String[] parts = line.split(",");
                        if (parts.length == 2) {
                            String timestamp = parts[0].trim();
                            int feedAmount = Integer.parseInt(parts[1].trim());
                            
                            // 检查是否是指定日期的数据
                            if (timestamp.startsWith(dateKey)) {
                                totalFeed += feedAmount;
                            }
                        }
                    } catch (Exception e) {
                        // 忽略无效数据
                    }
                }
                reader.close();
            }
        } catch (IOException e) {
            Log.e("MainActivity", "读取" + petName + "数据失败: " + e.getMessage());
        }
        
        return totalFeed;
    }
    
    /**
     * 更新多条折线的图表显示
     * 
     * @param dataSets 数据集列表（每个宠物一个数据集）
     * @param labels X轴标签列表
     * @param description 图表描述（不再使用，标题已在上方）
     */
    private void updateMultiLineChart(List<LineDataSet> dataSets, List<String> labels, String description) {
        // 如果没有数据，显示提示
        if (dataSets.isEmpty()) {
            List<Entry> emptyEntries = new ArrayList<>();
            emptyEntries.add(new Entry(0, 0));
            LineDataSet emptyDataSet = new LineDataSet(emptyEntries, "暂无数据");
            emptyDataSet.setColor(0xFF999999);
            dataSets.add(emptyDataSet);
            labels.clear();
            labels.add("暂无数据");
        }
        
        // 创建LineData
        LineData lineData = new LineData();
        for (LineDataSet dataSet : dataSets) {
            lineData.addDataSet(dataSet);
        }
        
        lineChart.setData(lineData);

        // 设置X轴标签
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(Math.min(labels.size(), 10), false);

        // 刷新图表
        lineChart.invalidate();
        
        // 打印调试信息
        Log.d("MainActivity", "图表更新：" + dataSets.size() + " 条折线");
        for (LineDataSet dataSet : dataSets) {
            Log.d("MainActivity", "折线：" + dataSet.getLabel() + ", 颜色：" + String.format("0x%08X", dataSet.getColor()));
        }
    }

    /**
     * 初始化UDP接收器
     * 创建UDP接收器实例，设置消息回调，并启动接收线程
     * 用于接收体重秤传感器数据（sensor_data_t结构体，25字节）
     */
    private void initUdpReceiver() {
        // 创建UDP接收器，并设置消息回调
        receiver = new UdpReceiver(new UdpReceiver.OnMessageListener() {
            @Override
            public void onMessage(String hexString, byte[] rawBytes) {
                // 收到UDP消息时解析并显示（已在主线程，可以直接更新UI）
                Log.d("MainActivity", "UDP收到 [" + rawBytes.length + " 字节]");
                Log.d("MainActivity", "十六进制: " + hexString);
                
                // 解析传感器数据结构体（sensor_data_t）
                // 结构体定义：
                // typedef struct {
                //     int32_t weight;           // 体重秤数据，单位：g (4字节)
                //     int32_t adc_value;        // ADC采集值 (4字节) - 未使用
                //     uint8_t rfid_uid[10];     // RFID卡片UID (10字节)
                //     uint8_t rfid_uid_len;     // RFID卡片UID长度 (1字节)
                //     uint8_t rfid_status;      // RFID状态 (1字节)
                //     uint8_t sensor_status;    // 传感器状态 (1字节) 1=靠近 0=未靠近
                //     uint32_t timestamp;       // 时间戳 (4字节)
                // } sensor_data_t;
                // 总共：25字节
                
                if (rawBytes.length >= 25) {
                    try {
                        // 解析体重数据
                        int weightLE = bytesToInt32LE(rawBytes, 0);
                        int weightBE = bytesToInt32BE(rawBytes, 0);
                        
                        int weight;
                        if (weightLE >= 0 && weightLE <= 10000) {
                            weight = weightLE;
                        } else if (weightBE >= 0 && weightBE <= 10000) {
                            weight = weightBE;
                        } else {
                            weight = weightLE;
                        }
                        
                        // 解析RFID UID（字节8-17，10字节数组）
                        byte[] rfidUid = new byte[10];
                        System.arraycopy(rawBytes, 8, rfidUid, 0, 10);
                        
                        // 解析rfid_uid_len（字节18，uint8）
                        int rfidUidLen = rawBytes[18] & 0xFF;
                        
                        // 解析rfid_status（字节19，uint8）
                        int rfidStatus = rawBytes[19] & 0xFF;
                        
                        // 解析sensor_status（字节20，uint8）
                        // 1 = 靠近，0 = 未靠近
                        int sensorStatus = rawBytes[20] & 0xFF;
                        
                        // 识别宠物
                        int petIndex = identifyPet(rfidUid, rfidUidLen);
                        
                        // 确定宠物名称
                        String petName;
                        
                        if (petIndex >= 0) {
                            // 识别到宠物
                            petName = PET_NAMES[petIndex];
                        } else {
                            // 未识别到卡片，默认为"阿柴"
                            petName = "当前无宠物";
                        }
                        
                        // 记录解析结果到日志
                        Log.d("MainActivity", "========== 传感器数据 ==========");
                        Log.d("MainActivity", "体重: " + weight + "g");
                        Log.d("MainActivity", "RFID长度: " + rfidUidLen);
                        Log.d("MainActivity", "RFID状态: " + rfidStatus);
                        Log.d("MainActivity", "传感器状态: " + (sensorStatus == 1 ? "靠近" : "未靠近"));
                        Log.d("MainActivity", "识别宠物: " + petName);
                        Log.d("MainActivity", "================================");
                        
                        // 更新宠物信息显示
                        updatePetInfo(petName);
                        
                        // 与上次体重比较，如果减少则记录进食量
                        checkAndRecordFeeding(petName, weight);
                        
                        // 更新食物剩余量显示
                        updateFoodRemainingFromWeight(weight);
                        
                    } catch (Exception e) {
                        // 解析数据时发生错误
                        Log.e("MainActivity", "解析UDP数据出错: " + e.getMessage());
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // 数据长度不足25字节
                    Log.w("MainActivity", "数据长度不足，期望25字节，实际" + rawBytes.length + "字节");
                    Toast.makeText(MainActivity.this, "数据格式错误", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        // 创建并启动UDP接收线程
        udpThread = new Thread(receiver);
        udpThread.start();
        
        // 提示用户UDP接收器已启动
        Toast.makeText(this, "UDP接收器已启动，监听端口10000", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 将4个字节转换为int32（小端序）
     * 小端序（Little Endian）：低字节在前，高字节在后
     * 
     * @param bytes 字节数组
     * @param offset 起始偏移量
     * @return int32值（-2147483648 到 2147483647）
     * 
     * 示例：
     * 字节数组：[0x31, 0x00, 0x00, 0x00] (49的小端序)
     * 小端序解析：0x00000031 = 49
     */
    private int bytesToInt32LE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) |                    // 字节0（低字节）
               ((bytes[offset + 1] & 0xFF) << 8) |         // 字节1
               ((bytes[offset + 2] & 0xFF) << 16) |        // 字节2
               ((bytes[offset + 3] & 0xFF) << 24);         // 字节3（高字节）
    }
    
    /**
     * 将4个字节转换为int32（大端序）
     * 大端序（Big Endian）：高字节在前，低字节在后
     * 
     * @param bytes 字节数组
     * @param offset 起始偏移量
     * @return int32值（-2147483648 到 2147483647）
     * 
     * 示例：
     * 字节数组：[0x00, 0x00, 0x00, 0x31] (49的大端序)
     * 大端序解析：0x00000031 = 49
     */
    private int bytesToInt32BE(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |            // 字节0（高字节）
               ((bytes[offset + 1] & 0xFF) << 16) |        // 字节1
               ((bytes[offset + 2] & 0xFF) << 8) |         // 字节2
               (bytes[offset + 3] & 0xFF);                 // 字节3（低字节）
    }
    
    /**
     * 将4个字节转换为uint32（小端序）
     * 小端序（Little Endian）：低字节在前，高字节在后
     * 
     * @param bytes 字节数组
     * @param offset 起始偏移量
     * @return uint32值（使用long存储，范围：0 到 4294967295）
     * 
     * 注意：Java没有无符号整数类型，所以使用long来存储uint32
     * 
     * 示例：
     * 字节数组：[0xFF, 0xFF, 0xFF, 0xFF]
     * 小端序解析：0xFFFFFFFF = 4294967295
     */
    private long bytesToUInt32LE(byte[] bytes, int offset) {
        return ((long)(bytes[offset] & 0xFF)) |            // 字节0（低字节）
               ((long)(bytes[offset + 1] & 0xFF) << 8) |   // 字节1
               ((long)(bytes[offset + 2] & 0xFF) << 16) |  // 字节2
               ((long)(bytes[offset + 3] & 0xFF) << 24);   // 字节3（高字节）
    }
    
    /**
     * 根据体重秤数据更新食物剩余量显示
     * 将体重值显示到tvFoodRemaining，并根据重量改变文字颜色
     * 
     * @param weight 体重（克）
     * 
     * 颜色规则：
     * - < 100g：红色警告（食物严重不足）
     * - < 300g：橙色提醒（食物偏少）
     * - >= 300g：绿色正常（食物充足）
     */
    private void updateFoodRemainingFromWeight(int weight) {
        // 更新当前体重值
        currentWeight = weight;
        
        // 实时保存体重数据到文件
        saveWeightDataToFile(weight);
        
        // 确保在主线程中更新UI
        runOnUiThread(() -> {
            // 显示体重值
            tvFoodRemaining.setText(String.valueOf(weight));
            
            // 根据重量改变文字颜色
            if (weight < 100) {
                // 红色警告：食物严重不足
                tvFoodRemaining.setTextColor(0xFFFF0000);
            } else if (weight < 300) {
                // 橙色提醒：食物偏少
                tvFoodRemaining.setTextColor(0xFFFF9800);
            } else {
                // 绿色正常：食物充足
                tvFoodRemaining.setTextColor(0xFF4CAF50);
            }
        });
    }
    
    /**
     * 将体重数据保存到文件
     * 文件格式：每行一条记录，格式为"时间戳,体重值"
     * 例如：2024-02-23 14:30:25,350
     * 
     * @param weight 体重（克）
     */
    private void saveWeightDataToFile(int weight) {
        try {
            // 获取应用的内部存储目录
            File file = new File(getFilesDir(), DATA_FILE_NAME);
            
            // 创建文件写入器，追加模式
            FileWriter writer = new FileWriter(file, true);
            
            // 获取当前时间戳
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            
            // 写入数据：时间戳,体重值
            String dataLine = timestamp + "," + weight + "\n";
            writer.write(dataLine);
            writer.close();
            
            Log.d("MainActivity", "保存体重数据到文件: " + dataLine.trim());
            
        } catch (IOException e) {
            Log.e("MainActivity", "保存体重数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 检查并记录进食量
     * 每次收到消息都与上次体重比较，如果减少则记录进食量
     * 
     * @param petName 宠物名称
     * @param currentWeight 当前体重
     */
    private void checkAndRecordFeeding(String petName, int currentWeight) {
        // 获取该宠物上一次的体重
        Integer lastWeight = lastWeightMap.get(petName);
        
        if (lastWeight != null && lastWeight > currentWeight) {
            // 体重减少，计算进食量
            int feedAmount = lastWeight - currentWeight;
            
            // 只记录合理的进食量（大于0且小于500g，避免误判）
            if (feedAmount > 0 && feedAmount < 500) {
                // 保存进食数据
                saveFeedData(petName, feedAmount);
                Log.d("MainActivity", petName + " 进食: " + feedAmount + "g (从" + lastWeight + "g减少到" + currentWeight + "g)");
            }
        }
        
        // 更新该宠物的上一次体重值
        lastWeightMap.put(petName, currentWeight);
    }
    
    /**
     * 识别宠物
     * 根据RFID UID识别是哪只宠物
     * 
     * @param rfidUid RFID UID数组
     * @param rfidUidLen RFID UID有效长度
     * @return 宠物索引，如果未识别返回-1
     */
    private int identifyPet(byte[] rfidUid, int rfidUidLen) {
        // 如果RFID长度小于4，无法识别
        if (rfidUidLen < 4) {
            return -1;
        }
        
        // 遍历所有已知宠物的UID
        for (int i = 0; i < PET_UIDS.length; i++) {
            boolean match = true;
            // 比较前4个字节
            for (int j = 0; j < 4; j++) {
                if (rfidUid[j] != PET_UIDS[i][j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        
        return -1; // 未识别
    }
    
    /**
     * 更新宠物信息显示
     * 
     * @param petName 宠物名称
     */
    private void updatePetInfo(String petName) {
        currentPetName = petName;
        
        runOnUiThread(() -> {
            tvPetName.setText(petName);
        });
    }
    
    /**
     * 保存进食数据到文件
     * 文件格式：每行一条记录，格式为"时间戳,进食量"
     * 每个宠物有独立的数据文件：achai.txt, tusong.txt
     * 
     * @param petName 宠物名称
     * @param feedAmount 进食量（克）
     */
    private void saveFeedData(String petName, int feedAmount) {
        try {
            // 获取宠物专属的进食数据文件
            String fileName = getPetFileName(petName);
            File file = new File(getFilesDir(), fileName);
            
            // 创建文件写入器，追加模式
            FileWriter writer = new FileWriter(file, true);
            
            // 获取当前时间戳
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            
            // 写入数据：时间戳,进食量
            String dataLine = timestamp + "," + feedAmount + "\n";
            writer.write(dataLine);
            writer.close();
            
            Log.d("MainActivity", "保存" + petName + "进食数据到" + fileName + ": " + dataLine.trim());
            
            // 自动刷新图表
            loadChartData();
            
        } catch (IOException e) {
            Log.e("MainActivity", "保存进食数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 根据宠物名称获取对应的文件名
     * 
     * @param petName 宠物名称
     * @return 文件名
     */
    private String getPetFileName(String petName) {
        switch (petName) {
            case "阿柴":
                return PET_FILE_NAMES[0]; // achai.txt
            case "土松":
                return PET_FILE_NAMES[1]; // tusong.txt
            default:
                return PET_FILE_NAMES[0]; // 默认为阿柴的文件 achai.txt
        }
    }
    
    /**
     * 清除所有数据文件
     * 删除体重数据文件和所有宠物的进食数据文件
     */
    private void clearAllDataFiles() {
        try {
            // 删除体重数据文件
            File weightFile = new File(getFilesDir(), DATA_FILE_NAME);
            if (weightFile.exists()) {
                weightFile.delete();
                Log.d("MainActivity", "体重数据文件已删除");
            }
            
            // 删除所有宠物的进食数据文件
            for (String fileName : PET_FILE_NAMES) {
                File feedFile = new File(getFilesDir(), fileName);
                if (feedFile.exists()) {
                    feedFile.delete();
                    Log.d("MainActivity", "进食数据文件已删除: " + fileName);
                }
            }
            
        } catch (Exception e) {
            Log.e("MainActivity", "清除数据文件出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 更新食物剩余量
     * @param remaining 剩余量（克）
     * 食物剩余：0000    投喂次数：0000 自动投喂：0000  手动投喂：0000 手动投喂重量：0000
     * tvFoodRemaining  tvFeedCount
     * foodRemaining    feedCount
     */
    public void updateFoodRemaining(String remaining) {
        try {
            // 检查消息长度
            if (remaining == null || remaining.length() < 4) {
                Toast.makeText(MainActivity.this, "食物剩余量数据格式错误", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String foodRemainingt;
            // 从接收到的数据中解析食物剩余量
            foodRemainingt = remaining.substring(0, 4);
            // string 转 int
            int tmp = Integer.valueOf(foodRemainingt);
            
            runOnUiThread(() -> {
                tvFoodRemaining.setText(foodRemainingt);
                // 根据剩余量改变颜色
                if (tmp < 100) {
                    tvFoodRemaining.setTextColor(0xFFFF0000); // 红色警告
                } else if (tmp < 300) {
                    tvFoodRemaining.setTextColor(0xFFFF9800); // 橙色提醒
                } else {
                    tvFoodRemaining.setTextColor(0xFFFF5722); // 正常颜色
                }
            });
            
        } catch (StringIndexOutOfBoundsException e) {
            Toast.makeText(MainActivity.this, "食物剩余量解析错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(MainActivity.this, "食物剩余量数字格式错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "食物剩余量更新错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}