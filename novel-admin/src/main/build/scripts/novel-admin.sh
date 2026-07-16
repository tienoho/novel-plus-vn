#!/bin/sh
APP_NAME=novel-admin
JAR_NAME=$APP_NAME\.jar
#PID là tệp lưu mã tiến trình
PID=$APP_NAME\.pid


SCRIPT_DIR=$(dirname "$(readlink -f "$0")")
cd "$SCRIPT_DIR"/.. || exit 1

# Hướng dẫn sử dụng
usage() {
    echo "Usage: $0 [start|stop|restart|status]"
    exit 1
}

#Kiểm tra ứng dụng đang chạy
is_exist(){
  pid=`ps -ef|grep $JAR_NAME|grep -v grep|awk '{print $2}' `
  # Trả 1 nếu không tồn tại, trả 0 nếu tồn tại
  if [ -z "${pid}" ]; then
   return 1
  else
    return 0
  fi
}

#Hàm khởi động
start(){
  is_exist
  if [ $? -eq "0" ]; then 
    echo ">>> Trang quản trị Novel Plus đang chạy PID = ${pid} <<<"
  else 
    echo ">>> Đang khởi động trang quản trị Novel Plus <<<"
    nohup java -jar -Dspring.profiles.active=prod $JAR_NAME >/dev/null 2>&1 &
    sleep 20
    echo $! > $PID
    echo ">>> Đã khởi động trang quản trị Novel Plus PID = $! <<<"
    status
   fi
  }

#Hàm dừng
stop(){
  #is_exist
  pidf=$(cat $PID)
  #echo "$pidf"  
  echo ">>> Trang quản trị Novel Plus PID = $pidf bắt đầu dừng <<<"
  kill $pidf
  rm -rf $PID
  sleep 2
  is_exist
  if [ $? -eq "0" ]; then 
    echo ">>> Trang quản trị Novel Plus PID = $pid bắt đầu buộc dừng <<<"
    kill -9  $pid
    sleep 2
    status 
  else
    status
  fi  
}

#In trạng thái chạy
status(){
  is_exist
  if [ $? -eq "0" ]; then
    echo ">>> Trang quản trị Novel Plus đang chạy PID = ${pid} <<<"
  else
    echo ">>> Trang quản trị Novel Plus chưa chạy <<<"
  fi
}

#Khởi động lại
restart(){
  stop
  start
}

#Chọn hàm theo tham số đầu vào; nếu không có thì hiển thị hướng dẫn
case "$1" in
  "start")
    start
    ;;
  "stop")
    stop
    ;;
  "status")
    status
    ;;
  "restart")
    restart
    ;;
  *)
    usage
    ;;
esac
exit 0
