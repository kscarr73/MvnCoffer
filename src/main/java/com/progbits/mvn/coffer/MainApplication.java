package com.progbits.mvn.coffer;

import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

public class MainApplication {

    public static void main(String[] args) {
        Integer webPort = 8080;
        
        if (System.getenv("PORT") != null) {
            webPort = Integer.valueOf(System.getenv("PORT"));
        }
        
        WebServer ws = WebServer.builder()
                .port(webPort)
                .routing(MainApplication::routing)
                .start();
    }

    static void routing(HttpRouting.Builder rules) {
        rules.addFeature(new AccessLog4jFeature());
        rules.register("/", new MvnCofferController());
    }
}
