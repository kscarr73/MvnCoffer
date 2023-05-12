cd target

export COFFER_REPO_DIR=/home/scarr/.m2
export COFFER_USER_scarr="cevine94;repository_WRITE"


JVM_ARGS="-agentlib:jdwp=transport=dt_socket,server=y,address=8000"

/usr/lib/jvm/java-20-temurin/bin/java --enable-preview -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -DLog4jContextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector $JVM_ARGS -jar MvnCoffer-2.0.0.jar
