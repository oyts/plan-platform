package com.oyts.planplatform.metrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.common.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MetricsControllerTest {
 @Test void storageAndTransactionFailuresReturnPrivate503WithoutDetails(){for(RuntimeException failure:new RuntimeException[]{new org.springframework.dao.DataAccessResourceFailureException("private-db"),new org.springframework.transaction.CannotCreateTransactionException("private-tx")}){var service=mock(WebMetricsService.class);var req=new MockHttpServletRequest();var body=new ObjectMapper().createObjectNode();doThrow(failure).when(service).event(req,body);var response=new MetricsController(service,null,null).event(req,body);assertEquals(503,response.getStatusCode().value());assertNull(response.getBody());assertEquals("no-store",response.getHeaders().getCacheControl());}}
 @Test void explicitForbiddenIsNotTurnedIntoStorageFailure(){var service=mock(WebMetricsService.class);var req=new MockHttpServletRequest();var body=new ObjectMapper().createObjectNode();doThrow(new ForbiddenException("分析请求无效")).when(service).event(req,body);assertThrows(ForbiddenException.class,()->new MetricsController(service,null,null).event(req,body));}
}
