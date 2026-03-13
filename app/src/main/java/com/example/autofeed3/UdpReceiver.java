package com.example.autofeed3;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * UDP消息接收器
 * 功能：在后台线程中持续监听UDP端口，接收二进制数据并转换为十六进制/二进制格式
 * 用途：接收体重秤传感器数据（sensor_data_t结构体）
 */
public class UdpReceiver implements Runnable {
    // 日志标签，用于Logcat输出
    private static final String TAG = "UdpReceiver";
    
    // 监听的UDP端口号
    private static final int PORT = 10000;
    
    // 接收缓冲区大小（字节）
    private static final int BUFFER_SIZE = 1024;
    
    // UDP套接字，用于接收数据
    private DatagramSocket socket;
    
    // 运行标志，控制接收循环
    private boolean running = false;
    
    // 主线程Handler，用于在主线程中执行回调
    private Handler mainHandler;
    
    // 消息监听器
    private OnMessageListener listener;
    
    /**
     * 消息监听接口
     * Activity实现此接口以接收UDP消息通知
     */
    public interface OnMessageListener {
        /**
         * 收到消息时回调（在主线程中执行，可以直接更新UI）
         * @param hexString 十六进制字符串格式的数据（如：01 A3 FF）
         * @param rawBytes 原始字节数组，用于进一步解析
         */
        void onMessage(String hexString, byte[] rawBytes);
    }
    
    /**
     * 构造函数
     * @param listener 消息监听器，用于接收UDP消息通知
     */
    public UdpReceiver(OnMessageListener listener) {
        this.listener = listener;
        // 创建主线程Handler，确保回调在主线程执行
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * 线程运行方法
     * 在后台线程中持续监听UDP端口，接收并处理消息
     */
    @Override
    public void run() {
        try {
            // 创建UDP套接字并绑定到指定端口
            socket = new DatagramSocket(PORT);
            running = true;
            
            Log.d(TAG, "UDP接收器启动成功，监听端口: " + PORT);
            
            // 创建接收缓冲区
            byte[] buffer = new byte[BUFFER_SIZE];
            
            // 持续接收消息循环
            while (running) {
                try {
                    // 创建数据包对象，用于接收数据
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    
                    Log.d(TAG, "等待接收UDP消息...");
                    
                    // 接收数据（阻塞方法，直到收到数据）
                    socket.receive(packet);
                    
                    // 获取实际接收到的字节数组（只复制有效数据部分）
                    byte[] receivedBytes = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), 0, receivedBytes, 0, packet.getLength());
                    
                    // 将字节数组转换为十六进制字符串，方便查看和调试
                    String hexString = bytesToHex(receivedBytes);
                    
                    // 获取发送方IP地址
                    String senderIP = packet.getAddress().getHostAddress();
                    
                    // 记录接收到的数据信息
                    Log.d(TAG, "收到消息 [" + packet.getLength() + " 字节]");
                    Log.d(TAG, "十六进制: " + hexString);
                    Log.d(TAG, "来自: " + senderIP);
                    
                    // 如果设置了监听器，在主线程中执行回调
                    if (listener != null) {
                        byte[] finalBytes = receivedBytes;
                        String finalHex = hexString;
                        // 使用Handler切换到主线程执行回调
                        mainHandler.post(() -> listener.onMessage(finalHex, finalBytes));
                    }
                    
                } catch (Exception e) {
                    // 如果仍在运行状态下发生异常，记录错误
                    if (running) {
                        Log.e(TAG, "接收消息出错: " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            // UDP接收器启动失败
            Log.e(TAG, "UDP接收器启动失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 关闭套接字，释放资源
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            Log.d(TAG, "UDP接收器已关闭");
        }
    }
    
    /**
     * 将字节数组转换为十六进制字符串
     * 每个字节转换为两位十六进制数，字节之间用空格分隔
     * 
     * @param bytes 字节数组
     * @return 十六进制字符串（如：01 A3 FF）
     * 
     * 示例：
     * 输入：[1, 163, 255]
     * 输出："01 A3 FF"
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            // 将每个字节转换为两位十六进制数（%02X表示至少2位，不足补0，大写）
            sb.append(String.format("%02X", bytes[i]));
            // 添加空格分隔（最后一个字节后不加空格）
            if (i < bytes.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }
    
    /**
     * 将字节数组转换为二进制字符串
     * 每个字节转换为8位二进制数，字节之间用空格分隔
     * 
     * @param bytes 字节数组
     * @return 二进制字符串（如：00000001 10100011）
     * 
     * 示例：
     * 输入：[1, 163]
     * 输出："00000001 10100011"
     */
    public static String bytesToBinary(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            // 将字节转换为8位二进制字符串
            // bytes[i] & 0xFF：将byte转换为无符号整数（0-255）
            // Integer.toBinaryString：转换为二进制字符串
            // String.format("%8s", ...)：格式化为8位，不足补空格
            // .replace(' ', '0')：将空格替换为0
            sb.append(String.format("%8s", Integer.toBinaryString(bytes[i] & 0xFF)).replace(' ', '0'));
            // 添加空格分隔（最后一个字节后不加空格）
            if (i < bytes.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }
    
    /**
     * 停止UDP接收器
     * 停止接收循环并关闭套接字
     */
    public void stop() {
        // 设置运行标志为false，结束接收循环
        running = false;
        // 关闭套接字，这会导致正在阻塞的receive()方法抛出异常并退出
        if (socket != null) {
            socket.close();
        }
    }
}
