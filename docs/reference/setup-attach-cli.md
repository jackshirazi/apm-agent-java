---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/setup-attach-cli.html
---

# Automatic setup with apm-agent-attach-cli.jar [setup-attach-cli]

The `apm-agent-attach-cli.jar` is an executable Java command line program which attaches the Elastic APM Java agent to a specific JVM or to all JVMs of the same host it runs on, it has the following properties:

* No application code changes required.
* Does not require to change JVM arguments nor application server configuration.
* Allows to instrument all JVMs on given host, both running and by monitoring new JVMs as they start.
* Requires to copy and execute the `apm-agent-attach-cli.jar` binary on the host.
* Relies on the [runtime attachment capabilities](https://docs.oracle.com/javase/8/docs/jdk/api/attach/spec/com/sun/tools/attach/VirtualMachine.md#attach-java.lang.String-) for java agents of the JVM but doesn’t require a full JDK or the `tools.jar`.
* Events that happen while the application starts up may not be captured.
* It is usually not possible to attach to JVMs that are running in containers or in Kubernetes pods using this mechanism. For Kubernetes we support auto-attachment using a [mutating webhook](apm-k8s-attacher://reference/index.md); for docker you could try the [script below](#setup-attach-cli-docker)


## Supported environments [setup-attach-cli-supported-environments]

On Unix operating systems, such as Linux and macOS, the attachment to HotSpot-based JVMs (like OpenJDK and Oracle JDK) and to OpenJ9 JVMs is supported. The user that runs the attacher has to be the same as the one that runs the target JVM or has to have permissions to switch to that user.

On Windows, only HotSpot-based JVMs are supported. The user that runs the attacher has to be the same as the one that runs the target JVM.

The target VM does not have to be the same as the one that starts the attacher. That means it is possible to run the attacher with Java 7 and attach the agent to an application that runs under Java 11. However, the type of the VM has to be the same: It’s not possible to attach to a J9 VM from a HotSpot-based VM and vice-versa.


## Download [setup-attach-cli-download]

You can download the attach program from maven central: [maven central](https://mvnrepository.com/artifact/co.elastic.apm/apm-agent-attach-cli/latest)

::::{note}
In versions prior to 1.22.0, you will have to download the `standalone` jar of the `apm-agent-attach` artifact.
::::



## Usage [setup-attach-cli-usage]

Attaches the Elastic APM Java agent to all running JVMs that match the `--include-*` / `--exclude-*` discovery rules.

For every running JVM, the discovery rules are evaluated in the order they are provided. The first rule that matches the currently evaluated Java application determines the outcome.

* If the first match is an exclude rule, the agent will not be attached.
* If the first match is an include rule, the agent will be attached.
* If no rule matches, the agent will not be attached.

Example: The following command attaches the agent to all JVMs whose main class contains `MyApplication` or which are started from a jar file named `my-application.jar` or have set the system property `-Delastic.apm.attach=true`, unless the JVM runs under the root user. It also makes the attacher run continuously so that it attaches the agent on starting JVMs that match discovery rules. Additionally, it applies some [configuration options](/reference/configuration.md).

```bash
java -jar apm-agent-attach-cli.jar \
    --exclude-user root \
    --include-main MyApplication my-application.jar \
    --include-vmarg elastic.apm.attach=true \
    --continuous \
    --config service_name=my-cool-service \
    --config server_url=http://127.0.0.1:8200
```


## Options [setup-attach-cli-usage-options]

**-l, --list**
:   This lets you do a dry run of the include/exclude discovery rules. Instead of attaching to matching JVMs, the programm will print JVMs that match the include/exclude discovery rules. Similar to `jps -l`, the output includes the PID and the main class name or the path to the jar file.


**-v, --list-vmargs**
:   When listing running JVMs via `--list`, include the arguments passed to the JVM. Provides an output similar to `jps -lv`.

::::{note}
The JVM arguments may contain sensitive information, such as passwords provided via system properties.
::::



**-c, --continuous**
:   If provided, this program continuously runs and attaches to all running and starting JVMs which match the `--exclude` and `--include` filters.


**--no-fork** [1.35.0]
:   By default, when the attacher program is ran by user A and the target process is ran by user B, the attacher will attempt to start another process as user B. If this configuration option is provided, the attacher will not fork. Instead, it will attempt to attach directly as the current user.


**--include-all**
:   Includes all JVMs for attachment.


**--include-pid <pid>…​**
:   A PID to include.


**--include-main/--exclude-main <pattern>…​**
:   A regular expression of fully qualified main class names or paths to JARs of applications the java agent should be attached to. Performs a partial match so that `foo` matches `/bin/foo.jar`.


**--include-vmarg/--exclude-vmarg <pattern>…​**
:   A regular expression that is matched against the arguments passed to the JVM, such as system properties. Performs a partial match so that `attach=true` matches the system property `-Delastic.apm.attach=true`.


**--include-user/--exclude-user <user>…​**
:   A username that is matched against the operating system user that run the JVM. For included users, make sure that the user this program is running under is either the same user or has permissions to switch to the user that runs the target JVM.


**-a, --args <agent_arguments>**
:   Deprecated in favor of --config.

If set, the arguments are used to configure the agent on the attached JVM (agentArguments of agentmain).

The syntax of the arguments is `key1=value1;key2=value1,value2`. See [*Configuration*](/reference/configuration.md) for all available configuration options.

::::{note}
This option cannot be used in conjunction with `--args-provider`
::::



**-C --config <key=value>…​**
:   This repeatable option sets one agent configuration option.

Example: `--config server_url=http://127.0.0.1:8200`


**-A, --args-provider <args_provider_script>**
:   The name of a program which is called when a new JVM starts up. The program gets the pid and the main class name or path to the JAR file as an argument and returns an arg string which is used to configure the agent on the attached JVM (agentArguments of agentmain). When returning a non-zero status code from this program, the agent will not be attached to the starting JVM.

The syntax of the arguments is `key1=value1;key2=value1,value2`. See [*Configuration*](/reference/configuration.md) for all available configuration options.

::::{note}
This option cannot be used in conjunction with `--pid` and `--config`
::::



**-g, --log-level <off|fatal|error|warn|info|debug|trace|all>**
:   Sets the log level. The logs are sent to stdout with an ECS JSON format.


**--log-file <file>**
:   To log into a file instead of the console, specify a path to a file that this program should log into. The log file rolls over once the file has reached a size of 10MB. One history file will be kept with the name `${logFile}.1`.


**--agent-jar <file>**
:   Instead of the bundled agent jar, attach the provided agent to the target JVMs.


**--download-agent-version <agent-version>**
:   Instead of the bundled agent jar, download and attach the specified agent version from maven central. <agent-version> can be either the explicit version (for example: `1.15.0`) or `latest`. The agent is authenticated and validated based on the published PGP signature. This option requires internet access.



## Docker [setup-attach-cli-docker]

Use this script to automatically attach to all docker containers running on a host. This script does not return but continuously listens for starting containers which it also attaches to.

::::{note}
This script is experimental and might not work with all containers. Especially the `jq --raw-output .[0].Config.Cmd[0]) == java` might vary.
::::


```bash
#!/usr/bin/env bash
set -ex

attach () {
    # only attempt attachment if this looks like a java container
    if [[ $(docker inspect ${container_id} | jq --raw-output .[0].Config.Cmd[0]) == java ]]
    then
        echo attaching to $(docker ps --no-trunc | grep ${container_id})
        docker cp ./apm-agent-attach-*-cli.jar ${container_id}:/apm-agent-attach-cli.jar
        docker exec ${container_id} java -jar /apm-agent-attach-cli.jar --config
    fi
}

# attach to running containers
for container_id in $(docker ps --quiet --no-trunc) ; do
    attach
done

# listen for starting containers and attach to those
docker events --filter 'event=start' --format '{{.ID}}' |
while IFS= read -r container_id
do
    attach
done
```


## Troubleshooting [setup-attach-cli-troubleshooting]

If you get a message like `no main manifest attribute, in apm-agent-attach.jar`, you are using the wrong artifact. Use the one which ends in `-cli.jar`.

