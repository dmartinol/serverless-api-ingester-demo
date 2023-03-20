package com.redhat.fsi.appeng.serverless.api;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.enterprise.event.Observes;

import org.jboss.logging.Logger;
import io.quarkus.runtime.StartupEvent;

@Path("/v1/demo")
public class DemoAPI {
    @Inject
    private Logger logger;

    @Inject
    private DemoService demoService;

    void onStart(@Observes StartupEvent ev) {               
        logger.info("DemoAPI is starting...");
    }

    @GET
    @Path("/data")
    public Response getData(){
        logger.infof("Invoked getData");
        return Response.ok(demoService.data()).build();
    }

    @GET
    @Path("/data/{index}")
    public Response getByIndex(@PathParam("index") int index, @HeaderParam("my-header") String myHeader, @QueryParam("upper") boolean upper) {
        logger.infof("Invoked getByIndex(%s)", index);
        logger.infof("myHeader is \"%s\"", myHeader);
        logger.infof("upper flag is \"%s\"", upper);

        List<String> data = demoService.data();
        if (index >= 0 && index < data.size()) {
            String value = data.get(index);
            return Response.ok(buildResponse(value, myHeader, upper)).build();
        }
        return Response.status(Status.NOT_FOUND)
                .entity(String.format("Invalid index %d (size is %d)", index, data.size())).build();
    }

    private String buildResponse(String value, String myHeader, boolean uppercase) {
        String output = (myHeader == null) ? value : String.format("%s %s", value, myHeader);
        return uppercase ? output.toUpperCase() : output;
    }
}
