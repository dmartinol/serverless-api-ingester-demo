package com.redhat.fsi.appeng.serverless.api;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DemoService {
    private List<String> data = new ArrayList<>();
    
     List<String> data() {
        return new ArrayList<>(data);
    }

    void addValue(String value) {
        data.add(value);
    }
}
