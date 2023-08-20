package com.progbits.mvn.coffer;

import com.progbits.jetty.embedded.JettyEmbedded;
import com.progbits.jetty.embedded.router.ServletRouter;

public class MainApplication {

    public static void main(String[] args) {
        Integer webPort = 8080;

        if (System.getenv("PORT") != null) {
            webPort = Integer.valueOf(System.getenv("PORT"));
        }

        ServletRouter routes = new ServletRouter();

        routes.addServletController(new MvnCofferController());
        
        try {
            JettyEmbedded.builder()
                    .setPort(webPort)
                    .setContextPath("/coffer")
                    .useVirtualThreads()
                    .useServletRoutes(routes)
                    .build()
                    .waitForInterrupt();
        } catch (InterruptedException iex) {
            // Nothing to do here
        }

    }

}
