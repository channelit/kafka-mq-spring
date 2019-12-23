# Docker based Kafka demo using Spring Integration, IBM MQ, Sping Kafka


#### Steps:
##### Build the app:
```shell script
docker build . -t kafka-mq-spring
docker tag kafka-mq kafka-mq-spring:v1
```

##### Build volume image with configuration (to work without physical mount):
```shell script
docker build . -t conf -f Dockerfile-config
docker tag conf conf:v1
```

##### Startup the environment:
```shell script
docker -compose up -d zoo1 zoo2 zoo3
docker-compose up -d
```
- Create topic FIFO_TOPIC with replication=2, partitions=3 using UI


##### Cleanup:
```shell script
docker-compose kill
docker-compose rm
rm -rf data
```