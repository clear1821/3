package com.example.autofeed3;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class wifiTcp {
    private static final String TAG = "wifiTcp";
    
    // IP地址
    private String ip;
    // 端口
    private int port;
    // 连接标志
    private boolean isConnected = false;
    // 主线程Handler
    private Handler mainHandler;
    // TCP套接字
    private Socket tcpSocket;
    // 接收线程
    private Thread receiveThread;
    // 输入输出流
    private BufferedReader reader;
    private BufferedWriter writer;
    // 消息回调接口
    private MessageCallback messageCallback;

    /**
     * 消息回调接口
     */
    public interface MessageCallback {
        void onMessageReceived(String message);
        void onConnectionStatusChanged(boolean connected);
        void onError(String error);
    }

    /**
     * 构造函数
     */
    public wifiTcp() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 设置消息回调
     */
    public void setMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
    }

    /**
     * 连接到服务器
     * @param ip 服务器IP地址
     * @param port 服务器端口
     */
    public void connect(String ip, int port) {
        this.ip = ip;
        this.port = port;

        new Thread(() -> {
            try {
                // 创建Socket连接
                tcpSocket = new Socket(ip, port);
                tcpSocket.setKeepAlive(true);
                
                // 初始化输入输出流
                reader = new BufferedReader(new InputStreamReader(
                        tcpSocket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(
                        tcpSocket.getOutputStream(), StandardCharsets.UTF_8));

                isConnected = true;
                Log.d(TAG, "连接成功: " + ip + ":" + port);
                
                // 通知连接状态改变
                notifyConnectionStatus(true);
                
                // 启动接收线程
                startReceiveThread();

            } catch (IOException e) {
                Log.e(TAG, "连接失败: " + e.getMessage());
                isConnected = false;
                notifyError("连接失败: " + e.getMessage());
                notifyConnectionStatus(false);
            }
        }).start();
    }

    /**
     * 发送消息
     * @param message 要发送的消息
     * @return 是否发送成功
     */
    public boolean sendMessage(String message) {
        if (!isConnected || writer == null) {
            Log.e(TAG, "未连接，无法发送消息");
            notifyError("未连接，无法发送消息");
            return false;
        }

        new Thread(() -> {
            try {
                writer.write(message);
                writer.newLine();
                writer.flush();
                Log.d(TAG, "发送消息: " + message);
            } catch (IOException e) {
                Log.e(TAG, "发送消息失败: " + e.getMessage());
                isConnected = false;
                notifyError("发送消息失败: " + e.getMessage());
                notifyConnectionStatus(false);
            }
        }).start();

        return true;
    }

    /**
     * 启动接收线程
     */
//    食物剩余：0000  投喂次数：0000  手动投喂：00 手动投喂重量：0000 自动投喂：00
    private void startReceiveThread() {
        String ms;
        receiveThread = new Thread(() -> {
            String message;
            try {
                while (isConnected && (message = reader.readLine()) != null) {
                    Log.d(TAG, "接收消息: " + message);
                    String finalMessage = message;

                    // 在主线程回调
                    mainHandler.post(() -> {
                        if (messageCallback != null) {
                            messageCallback.onMessageReceived(finalMessage);

                        }
                    });
                }
            } catch (IOException e) {
                if (isConnected) {
                    Log.e(TAG, "接收消息异常: " + e.getMessage());
                    notifyError("接收消息异常: " + e.getMessage());
                }
            } finally {
                disconnect();
            }
        });
        receiveThread.start();

    }

    /**
     * 检测连接状态
     * @return 是否已连接
     */
    public boolean isConnected() {
        if (!isConnected || tcpSocket == null) {
            return false;
        }
        
        // 检查Socket是否真正连接
        return tcpSocket.isConnected() && !tcpSocket.isClosed();
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        isConnected = false;
        
        try {
            if (reader != null) {
                reader.close();
                reader = null;
            }
            if (writer != null) {
                writer.close();
                writer = null;
            }
            if (tcpSocket != null && !tcpSocket.isClosed()) {
                tcpSocket.close();
                tcpSocket = null;
            }
            if (receiveThread != null && receiveThread.isAlive()) {
                receiveThread.interrupt();
                receiveThread = null;
            }
            Log.d(TAG, "连接已断开");
            notifyConnectionStatus(false);
        } catch (IOException e) {
            Log.e(TAG, "断开连接异常: " + e.getMessage());
        }
    }

    /**
     * 通知连接状态改变
     */
    private void notifyConnectionStatus(boolean connected) {
        mainHandler.post(() -> {
            if (messageCallback != null) {
                messageCallback.onConnectionStatusChanged(connected);
            }
        });
    }

    /**
     * 通知错误
     */
    private void notifyError(String error) {
        mainHandler.post(() -> {
            if (messageCallback != null) {
                messageCallback.onError(error);
            }
        });
    }

    /**
     * 获取当前IP
     */
    public String getIp() {
        return ip;
    }

    /**
     * 获取当前端口
     */
    public int getPort() {
        return port;
    }
}

