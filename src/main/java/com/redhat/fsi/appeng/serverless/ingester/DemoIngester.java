package com.redhat.fsi.appeng.serverless.ingester;

import java.util.Map;
import java.util.function.Consumer;

import javax.enterprise.event.Observes;

import org.jboss.logging.Logger;

import io.quarkus.funqy.Funq;
import io.quarkus.funqy.knative.events.CloudEvent;
import io.quarkus.runtime.StartupEvent;

public class DemoIngester {
    private Logger logger = Logger.getLogger(DemoIngester.class);

    void onStart(@Observes StartupEvent ev) {
        logger.info("DemoIngester is starting...");
    }

    @Funq("eventA")
    public void onEventA(String data) {
        logger.infof("eventA invoked with %s", data);
    }

    @Funq("eventB")
    public void onEventB(String data) {
        logger.infof("eventB invoked with %s", data);
    }

    private Map<String, Consumer<String>> routingTable = Map.of("inbox", s -> onEventA(s), "avro", s -> onEventB(s));
    private Consumer<String> defaultConsumer = s -> logger.warnf("No mathcing consumer found for %s", s);

    @Funq("router")
    public void onRouterEvent(CloudEvent<byte[]> event) {
        String source = event.source();
        String data = new String(event.data());
        logger.infof("router invoked from %s with %s", source, data);
        if (source.contains("#")) {
            String topic = source.split("#")[1];
            logger.infof("Routing to topic %s", topic);
            routingTable.getOrDefault(topic, defaultConsumer).accept(data);
        } else {
            logger.warnf("Unmanaged source %s, missing # separator", source);
        }
    }
}
