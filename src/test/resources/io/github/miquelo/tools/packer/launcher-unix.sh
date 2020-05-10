#!/bin/sh

echo "$$"

case "$1" in

"successfully-launch")
if [ "$2" != "some-name" || "$3" != "first-arg" || "$4" != "second-arg" ]
then
  exit 1
fi
echo "success"
exit 0;
;;

"infinite-sleep")
sleep infinity
exit 0
;;

"false")
false
exit 0
;;

"stop")
kill -STOP "$$"
exit 0
;;

*)
exit 1
;;

esac

#echo "echo $(($(date '+%s%N') / 1000000)),target,type,data1,data2,data3"
