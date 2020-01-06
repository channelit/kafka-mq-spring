```shell script
docker cp mongo/conf/replica.js mongo1:/.
docker exec mongo1 bash -c 'mongo < /replica.js'
docker cp mongo/conf/replica.js mongo2:/.
docker exec mongo2 bash -c 'mongo --host localhost:27019 < /replica.js'
```

#### Debugging
```shell script
docker exec Hardiks-MBP /opt/kafka/bin/kafka-topics.sh --describe --topic fifo --zookeeper zoo1:2181,zoo2:2181,zoo3:2181
```