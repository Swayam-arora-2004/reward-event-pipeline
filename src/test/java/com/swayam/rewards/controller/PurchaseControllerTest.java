package com.swayam.rewards.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swayam.rewards.exception.ApiExceptionHandler;
import com.swayam.rewards.exception.PublishFailedException;
import com.swayam.rewards.messaging.PurchaseEvent;
import com.swayam.rewards.messaging.PurchaseProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PurchaseController.class)
@Import(ApiExceptionHandler.class)
class PurchaseControllerTest {

    private static final String VALID_BODY = "{\"eventId\":\"e-1\",\"memberId\":\"m-1\",\"amountRupees\":99}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseProducer producer;

    @Test
    void accept_validBody_returns202AndPublishes() throws Exception {
        mockMvc.perform(post("/purchases").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value("e-1"));

        verify(producer).publish(new PurchaseEvent("e-1", "m-1", 99));
    }

    @Test
    void accept_blankMemberId_returns400AndDoesNotPublish() throws Exception {
        String body = "{\"eventId\":\"e-1\",\"memberId\":\" \",\"amountRupees\":99}";
        mockMvc.perform(post("/purchases").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0]").value("memberId"));

        verify(producer, never()).publish(any());
    }

    @Test
    void accept_zeroAmount_returns400() throws Exception {
        String body = "{\"eventId\":\"e-1\",\"memberId\":\"m-1\",\"amountRupees\":0}";
        mockMvc.perform(post("/purchases").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accept_decimalAmount_returns400() throws Exception {
        String body = "{\"eventId\":\"e-1\",\"memberId\":\"m-1\",\"amountRupees\":10.5}";
        mockMvc.perform(post("/purchases").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accept_unknownField_returns400() throws Exception {
        String body = "{\"eventId\":\"e-1\",\"memberId\":\"m-1\",\"amountRupees\":99,\"extra\":true}";
        mockMvc.perform(post("/purchases").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accept_idWithLineBreak_returns400() throws Exception {
        String body = "{\"eventId\":\"e\\n1\",\"memberId\":\"m-1\",\"amountRupees\":99}";
        mockMvc.perform(post("/purchases").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accept_brokenJson_returns400WithSafeMessage() throws Exception {
        mockMvc.perform(post("/purchases").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("malformed request body"));
    }

    @Test
    void accept_kafkaDown_returns503WithoutInternalDetails() throws Exception {
        doThrow(new PublishFailedException("secret internal detail", new RuntimeException()))
                .when(producer).publish(any());

        mockMvc.perform(post("/purchases").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
}
