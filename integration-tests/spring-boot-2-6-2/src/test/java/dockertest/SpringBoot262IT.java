package dockertest;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringBoot262IT {
    private static final Logger logger = LoggerFactory.getLogger(SpringBoot262IT.class);
    private static final OkHttpClient httpClient;
    static {
        final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(logger::info);
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        httpClient = new okhttp3.OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    }

    private final GenericContainer<?> servletContainer;

    public SpringBoot262IT() {
        this.servletContainer = new GenericContainer<>(
            new ImageFromDockerfile()
                .withFileFromPath("target/spring-boot-2-6-2-2.6.2.jar", Path.of("target","spring-boot-2-6-2-2.6.2.jar"))
                .withFileFromPath("target/spring-boot-2-6-2-2.6.2.jar.original", Path.of("target","spring-boot-2-6-2-2.6.2.jar.original"))
                .withFileFromPath("elastic-apm-agent.jar", Path.of("",getTargetJar("elastic-apm-agent",".jar")))
                .withDockerfileFromBuilder(
                    builder -> builder
                        .from("openjdk:11")
                        .copy("target/spring-boot-2-6-2-2.6.2.jar", "spring-boot-temp.jar")
                        .run("jar xvf spring-boot-temp.jar")
                        .copy("target/spring-boot-2-6-2-2.6.2.jar.original", "BOOT-INF/lib/spring-boot-2-6-2-2.6.2.jar")
                        .copy("elastic-apm-agent.jar", "/elastic-apm-agent.jar")
                        .cmd("java", "-javaagent:/elastic-apm-agent.jar", "--show-module-resolution", "--module-path", "BOOT-INF/lib", "--class-path", "BOOT-INF/lib", "-m", "co.elastic.apm.spring.boot.moduleexample")
                        .build()
                )
        )
            .withExposedPorts(8080);
        this.servletContainer.withNetwork(Network.SHARED)
            .withLogConsumer(new StandardOutLogConsumer().withPrefix("SpringBoot262IT"))
            .start();
    }

//    public static void main(String[] args){
//        System.out.println(new File("integration-tests/spring-boot-2-6-2/target/spring-boot-2-6-2-2.6.2.jar").exists());
//    }
    public static String[] getTargetJar(String project, String extension) {
        File agentBuildDir = new File("../../" + project + "/target/");
        FileFilter fileFilter = file -> file.getName().matches(project + "-\\d\\.\\d+\\.\\d+(\\.RC\\d+)?(-SNAPSHOT)?" + extension);
        String path = Arrays.stream(agentBuildDir.listFiles(fileFilter)).findFirst()
            .map(File::getAbsolutePath)
            .orElse(null);
        return path.split("[/\\\\]");
    }


    @After
    public final void stopServer() {
        servletContainer.getDockerClient()
            .stopContainerCmd(servletContainer.getContainerId())
            .exec();
        servletContainer.stop();
    }

    @Test
    public void executeTestRequest() throws IOException {
        Map<String, String> headers = Collections.emptyMap();
        Response response = executeRequest("/", headers);
        assertThat(response.code()).isEqualTo(200);
    }

    public Response executeRequest(String pathToTest, Map<String, String> headersMap) throws IOException {
        Headers headers = Headers.of((headersMap != null) ? headersMap : new HashMap<>());
        if (!pathToTest.startsWith("/")) {
            pathToTest = "/"+pathToTest;
        }

        return httpClient.newCall(new Request.Builder()
                .get()
                .url(getBaseUrl() + pathToTest)
                .headers(headers)
                .build())
            .execute();
    }

    public String getBaseUrl() {
        return "http://localhost:"+this.servletContainer.getMappedPort(8080);
    }

    public static class StandardOutLogConsumer implements Consumer<OutputFrame> {
        private static final Pattern ANSI_CODE_PATTERN = Pattern.compile("\\[\\d[ABCD]");
        private String prefix = "";

        public StandardOutLogConsumer() {
        }

        public StandardOutLogConsumer withPrefix(String prefix) {
            this.prefix = "[" + prefix + "] ";
            return this;
        }

        @Override
        public void accept(OutputFrame outputFrame) {
            if (outputFrame != null) {
                String utf8String = outputFrame.getUtf8String();

                if (utf8String != null) {
                    OutputFrame.OutputType outputType = outputFrame.getType();
                    String message = utf8String.trim();

                    if (ANSI_CODE_PATTERN.matcher(message).matches()) {
                        return;
                    }

                    switch (outputType) {
                        case END:
                            break;
                        case STDOUT:
                        case STDERR:
                            System.out.println(String.format("%s%s", prefix, message));
                            break;
                        default:
                            throw new IllegalArgumentException("Unexpected outputType " + outputType);
                    }
                }
            }
        }
    }

}
