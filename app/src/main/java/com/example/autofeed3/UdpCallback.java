package com.example.autofeed3;

//回调接口

public  interface UdpCallback{
    void onReceive();
    void onError(String error);

    void onReceive(String received);
}