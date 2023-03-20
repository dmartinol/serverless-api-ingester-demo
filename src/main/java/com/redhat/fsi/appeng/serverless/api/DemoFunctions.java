package com.redhat.fsi.appeng.serverless.api;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.enterprise.event.Observes;

import org.jboss.logging.Logger;
import io.quarkus.runtime.StartupEvent;

import io.quarkus.funqy.Funq;

public class DemoFunctions {
    @Inject
    private Logger logger;

    @Inject
    private DemoService demoService;

    void onStart(@Observes StartupEvent ev) {               
        logger.info("DemoFunctions is starting...");
    }

    @Funq("getData")
    public List<String> getter(Map<String, String> params) {
        logger.infof("Invoked getter(%s)", params);
        return demoService.data();
    }

    @Funq("addValue")
    public void writer(String value) {
        logger.info("Invoked writer()");
        demoService.addValue(value);
    }
}
