package org.task.n26.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.task.n26.dto.StatisticsDto;
import org.task.n26.dto.TransactionDto;
import org.task.n26.exception.TransactionExpiredException;
import org.task.n26.service.TransactionService;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.BDDMockito.*;
import static org.mockito.Matchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(TransactionController.class)
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    private String json;

    @Before
    public void setup() throws JsonProcessingException {
        TransactionDto dto = new TransactionDto();
        dto.setAmount(1.23);
        dto.setTimestamp(System.currentTimeMillis());

        ObjectMapper om = new ObjectMapper();
        json = om.writeValueAsString(dto);
    }

    @Test
    public void addTransaction() throws Exception {
        willDoNothing().given(transactionService).addTransaction(any(TransactionDto.class));
        this.mockMvc.perform(post("/transactions").content(json).contentType(MediaType.parseMediaType("application/json")))
                .andExpect(status().isCreated());
    }

    @Test
    public void addTransaction_Exception() throws Exception {
        willThrow(new TransactionExpiredException()).given(transactionService).addTransaction(any(TransactionDto.class));
        this.mockMvc.perform(post("/transactions").content(json).contentType(MediaType.parseMediaType("application/json")))
                .andExpect(status().isNoContent());
    }

    @Test
    public void getStatistics() throws Exception {
        StatisticsDto dto = new StatisticsDto(10.0, 5.0, 10.0, 10.0, 2L);
        given(transactionService.getRecentStats()).willReturn(dto);
        this.mockMvc.perform(get("/statistics").contentType(MediaType.parseMediaType("application/json")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sum", is(dto.getSum())))
                .andExpect(jsonPath("$.avg", is(dto.getAvg())))
                .andExpect(jsonPath("$.max", is(dto.getMax())))
                .andExpect(jsonPath("$.min", is(dto.getMin())))
                .andExpect(jsonPath("$.count", is(dto.getCount().intValue())));
    }

}
