import socket

# 修改为你的Android设备IP地址
TARGET_IP = "192.168.1.100"  # 改成你的手机IP
TARGET_PORT = 10000
MESSAGE = "Hello UDP!"

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.sendto(MESSAGE.encode('utf-8'), (TARGET_IP, TARGET_PORT))
print(f"已发送: {MESSAGE} 到 {TARGET_IP}:{TARGET_PORT}")
sock.close()
