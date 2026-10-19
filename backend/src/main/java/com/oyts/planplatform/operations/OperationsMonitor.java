package com.oyts.planplatform.operations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OperationsMonitor {
    private final OperationsService service;private final boolean enabled;private boolean failed;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(OperationsMonitor.class);
    OperationsMonitor(OperationsService service,@Value("${app.operations.enabled:false}")boolean enabled,@Value("${app.operations.scheduler-enabled:true}")boolean scheduler){this.service=service;this.enabled=enabled&&scheduler;}
    @Scheduled(fixedDelay=300000,initialDelay=60000) public void inspect(){if(!enabled)return;try{service.evaluate();if(failed)LOG.info("Operational status evaluation recovered.");failed=false;}catch(RuntimeException failure){if(!failed)LOG.warn("Operational status evaluation failed; inspect restricted status. No cleanup executed.");failed=true;}}
}
