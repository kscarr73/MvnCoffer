package com.progbits.mvn.coffer;

import io.helidon.common.Weighted;
import io.helidon.nima.webserver.http.FilterChain;
import io.helidon.nima.webserver.http.HttpFeature;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.RoutingRequest;
import io.helidon.nima.webserver.http.RoutingResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.ThreadContext;

/**
 *
 * @author scarr
 */
public class AccessLog4jFeature implements HttpFeature, Weighted {

    private static final Logger log = LogManager.getLogger(AccessLog4jFeature.class);

    private Pattern _ignoreRegEx;

    LinkedBlockingQueue<MapMessage> logMessages = new LinkedBlockingQueue<>();

    Thread msgThread;

    public AccessLog4jFeature() {
        init();
    }

    public AccessLog4jFeature(String ignoreRegEx) {
        if (ignoreRegEx != null) {
            _ignoreRegEx = Pattern.compile(ignoreRegEx);
        }

        init();
    }

    private void init() {
        Runnable msgTask = () -> {
            while (true) {
                try {
                    MapMessage msg = logMessages.take();

                    log.info(msg);
                } catch (InterruptedException ie) {
                    // nothing really to do here
                }
            }
        };

        msgThread = new Thread(msgTask);

        msgThread.start();
    }

    @Override
    public void setup(HttpRouting.Builder bldr) {
        bldr.addFilter(this::filter);
    }

    @Override
    public void beforeStart() {
        HttpFeature.super.beforeStart(); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
    }

    @Override
    public void afterStop() {
        HttpFeature.super.afterStop(); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
    }

    private void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        long currentTime = System.currentTimeMillis();

        try {
            chain.proceed();
        } finally {
            logEntry(req, res, currentTime);
        }
    }

    private void logEntry(RoutingRequest req, RoutingResponse res, long startTime) {
        MapMessage msg = new MapMessage()
                .with("status", res.status().code())
                .with("length", res.bytesWritten())
                .with("requestUri", req.path().path())
                .with("speed", System.currentTimeMillis() - startTime)
                .with("timestamp", startTime)
                .with("method", req.prologue().method().text())
                .with("clientip", req.remotePeer().address().toString())
                .with("reqhost", req.remotePeer().host() == null ? "" : req.remotePeer().host())
                .with("reqproto", req.prologue().protocol())
                .with("request", String.format("%s %s%s %s", req.prologue().method().text(), req.path().path(), req.prologue().query().value() == null ? "" : "?" + req.prologue().query().value(), req.prologue().protocol()))
                .with("sourceip", req.localPeer().address().toString());

        req.headers().forEach((hdr) -> {
            if (!"authorization".equals(hdr.name())) {
                msg.with("hdr_" + hdr.name(), hdr.value());
            }
        });

        String mdcFlowId = ThreadContext.get("X-FlowId");

        if (mdcFlowId != null) {
            msg.with("flowid", mdcFlowId);
        }

        if (_ignoreRegEx == null || !_ignoreRegEx.matcher(req.path().path()).matches()) {
            logMessages.offer(msg);
        }
    }
}
