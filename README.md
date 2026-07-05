# gradle-switch-agent

A Java agent that overrides or transforms Gradle Wrapper properties at runtime, allowing you to switch Gradle distribution URLs without modifying checked-out source files.

## Usage

Download the latest jar or build it yourself:

```sh
./gradlew shadowJar
```

Then inject the agent using `GRADLE_OPTS`:

```sh
# Direct override
export GRADLE_OPTS=-javaagent:gradle-switch-agent.jar=distributionUrl=https://mymirror/gradle-8.0-all.zip

# With sha256 checksum
export GRADLE_OPTS=-javaagent:gradle-switch-agent.jar=distributionUrl=https://mymirror/gradle-8.0-all.zip,distributionSha256Sum=abc123...
```

Now run any Gradle command normally:

```sh
./gradlew --version
./gradlew build
```

## Transform mode

Replace parts of a property value using regex:

```sh
# Replace version in distribution URL
export GRADLE_OPTS=-javaagent:gradle-switch-agent.jar=distributionUrl~/8.0/8.1/

# Use a custom mirror, preserving the rest of the URL
export GRADLE_OPTS=-javaagent:gradle-switch-agent.jar=distributionUrl~@https://services.gradle.org@https://mymirror/gradle/@
```

The syntax uses `key~/separator regex replacement separator/` — the first character after `~/` is the delimiter.

## Debug options

Set `-Dgsa.debug.resolution=true` to see which properties are being overridden/transformed:

```sh
export GRADLE_OPTS=-Dgsa.debug.resolution=true -javaagent:gradle-switch-agent.jar=distributionUrl~/8.0/8.1/
```

Set `-Dgsa.debug.instrumentation=true` to debug ByteBuddy class matching.

## How it works

The agent uses ByteBuddy to instrument `org.gradle.wrapper.WrapperExecutor.getProperty()`, intercepting property lookups from `gradle-wrapper.properties` and applying the configured overrides or regex transforms before they reach the caller.
