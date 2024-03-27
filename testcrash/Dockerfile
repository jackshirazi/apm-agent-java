FROM tomcat:10.1.19-jdk17
ADD jakartaee-simple-webapp.war /usr/local/tomcat/webapps/
ADD elastic-apm-agent-1.48.2-SNAPSHOT.jar /
ADD run.sh /
ENV CATALINA_OPTS="$CATALINA_OPTS -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager -javaagent:/elastic-apm-agent-1.48.2-SNAPSHOT.jar -Delastic.apm.service_name=testing-tomcat -Delastic.apm.secret_token=H01VjHp6BrHKb7AnOr -Delastic.apm.server_url=https://f52b312dad5a47c4afd55d699770d64a.apm.us-central1.gcp.cloud.es.io:443"
EXPOSE 8080
CMD ["sh", "/run.sh"]
