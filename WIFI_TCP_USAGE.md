# wifiTcp 类使用说明

## 功能概述

wifiTcp 类封装了通过 WiFi 使用 TCP 协议进行通信的功能，包括：
- 连接到 TCP 服务器
- 发送消息
- 接收消息
- 检测连接状态
- 断开连接

## 主要方法

### 1. 构造函数
```java
wifiTcp tcp = new wifiTcp();
```

### 2. 设置消息回调
```java
tcp.setMessageCallback(new wifiTcp.MessageCallback() {
    @Override
    public void onMessageReceived(String message) {
        // 接收到消息时的处理
        Log.d("TCP", "收到消息: " + message);
    }

    @Override
    public void onConnectionStatusChanged(boolean connected) {
        // 连接状态改变时的处理
        if (connected) {
            Log.d("TCP", "已连接");
        } else {
            Log.d("TCP", "已断开");
        }
    }

    @Override
    public void onError(String error) {
        // 发生错误时的处理
        Log.e("TCP", "错误: " + error);
    }
});
```

### 3. 连接服务器
```java
String serverIp = "192.168.1.100";
int serverPort = 8080;
tcp.connect(serverIp, serverPort);
```

### 4. 发送消息
```java
String message = "Hello Server";
boolean success = tcp.sendMessage(message);
```

### 5. 检测连接状态
```java
boolean connected = tcp.isConnected();
if (connected) {
    // 已连接
} else {
    // 未连接
}
```

### 6. 断开连接
```java
tcp.disconnect();
```

## 在 MainActivity 中使用示例

```java
public class MainActivity extends AppCompatActivity {
    private wifiTcp tcp;
    private Button btnConnect;
    private Button btnSend;
    private Button btnDisconnect;
    private EditText etIp;
    private EditText etPort;
    private EditText etMessage;
    private TextView tvStatus;
    private TextView tvReceived;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化控件
        btnConnect = findViewById(R.id.btnConnect);
        btnSend = findViewById(R.id.btnSend);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        etIp = findViewById(R.id.etIp);
        etPort = findViewById(R.id.etPort);
        etMessage = findViewById(R.id.etMessage);
        tvStatus = findViewById(R.id.tvStatus);
        tvReceived = findViewById(R.id.tvReceived);

        // 初始化 TCP 客户端
        tcp = new wifiTcp();
        tcp.setMessageCallback(new wifiTcp.MessageCallback() {
            @Override
            public void onMessageReceived(String message) {
                tvReceived.append("收到: " + message + "\n");
            }

            @Override
            public void onConnectionStatusChanged(boolean connected) {
                tvStatus.setText(connected ? "已连接" : "未连接");
                btnConnect.setEnabled(!connected);
                btnSend.setEnabled(connected);
                btnDisconnect.setEnabled(connected);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });

        // 连接按钮
        btnConnect.setOnClickListener(v -> {
            String ip = etIp.getText().toString();
            String portStr = etPort.getText().toString();
            if (!ip.isEmpty() && !portStr.isEmpty()) {
                int port = Integer.parseInt(portStr);
                tcp.connect(ip, port);
            } else {
                Toast.makeText(this, "请输入IP和端口", Toast.LENGTH_SHORT).show();
            }
        });

        // 发送按钮
        btnSend.setOnClickListener(v -> {
            String message = etMessage.getText().toString();
            if (!message.isEmpty()) {
                tcp.sendMessage(message);
                etMessage.setText("");
            }
        });

        // 断开按钮
        btnDisconnect.setOnClickListener(v -> {
            tcp.disconnect();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 页面销毁时断开连接
        if (tcp != null) {
            tcp.disconnect();
        }
    }
}
```

## 注意事项

1. **网络权限**：确保 AndroidManifest.xml 中已添加网络权限（已配置）
2. **线程安全**：所有网络操作都在子线程中执行，回调在主线程中执行
3. **生命周期管理**：在 Activity 的 onDestroy() 中记得断开连接
4. **错误处理**：通过 MessageCallback 接口处理各种错误情况
5. **消息格式**：当前实现使用换行符分隔消息，可根据需要修改
