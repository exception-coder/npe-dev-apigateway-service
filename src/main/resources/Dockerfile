FROM openjdk:8-jdk-alpine
# 指向了一个/tmp的目录，由于 Spring Boot 使用内置的Tomcat容器，Tomcat 默认使用/tmp作为工作目录。这个命令的效果是：在宿主机的/var/lib/docker目录下创建一个临时文件并把它链接到容器中的/tmp目录
VOLUME /tmp
# 拷贝文件并且重命名
ADD ./asent-apigateway-service-0.0.1-SNAPSHOT.jar app.jar

RUN chmod +x app.jar
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

ENV JAVA_OPTS="\
-XX:MetaspaceSize=128m \
-XX:MaxMetaspaceSize=256m \
-Xms1024m \
-Xmx1024m \
-Xmn256m \
-Xss256k \
-XX:SurvivorRatio=8 \
-XX:+UseConcMarkSweepGC \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/root/jvmlogs \
-Duser.timezone=Asia/Shanghai"

# 为了缩短 Tomcat 的启动时间，添加java.security.egd的系统属性指向/dev/urandom作为 ENTRYPOINT
# ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Darthas.enable-detail-pages=true","-jar","/app.jar"]
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar