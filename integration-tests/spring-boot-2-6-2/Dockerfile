FROM openjdk:11

COPY target/spring-boot-2-6-2-2.6.2.jar .
RUN jar xvf spring-boot-2-6-2-2.6.2.jar
COPY target/spring-boot-2-6-2-2.6.2.jar.original BOOT-INF/lib/spring-boot-2-6-2-2.6.2.jar

CMD ["java", "--show-module-resolution", "--module-path", "BOOT-INF/lib", "--class-path", "BOOT-INF/lib", "-m", "co.elastic.apm.spring.boot.moduleexample"]
