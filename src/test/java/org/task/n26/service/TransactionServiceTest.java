package org.task.n26.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.task.n26.dto.StatisticsDto;
import org.task.n26.dto.TransactionDto;
import org.task.n26.exception.TransactionExpiredException;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class TransactionServiceTest {

    @Autowired
    private TransactionService transactionService;

    @Test
    public void addTransaction_test() throws TransactionExpiredException, InterruptedException {
        transactionService.addTransaction(createTransaction(1.50, System.currentTimeMillis()));
        transactionService.addTransaction(createTransaction(1.55, System.currentTimeMillis()));
        transactionService.addTransaction(createTransaction(1.45, System.currentTimeMillis() - 5000));

        StatisticsDto stats = transactionService.getRecentStats();
        StatisticsDto expected = new StatisticsDto(4.50, 1.50, 1.55, 1.45, 3L);
        assertEquals(expected, stats);
    }

    @Test
    public void addTransaction_test2() throws TransactionExpiredException, InterruptedException {
        transactionService.addTransaction(createTransaction(1.50, System.currentTimeMillis()));
        transactionService.addTransaction(createTransaction(1.60, System.currentTimeMillis()));
        try {
            transactionService.addTransaction(createTransaction(1.45, System.currentTimeMillis() - 6100));
        } catch (TransactionExpiredException e) {

        }

        StatisticsDto stats = transactionService.getRecentStats();

        StatisticsDto expected = new StatisticsDto(3.10, 1.55, 1.60, 1.50, 2L);

        assertEquals(expected, stats);
    }

    @Test
    public void addTransaction_test3() throws TransactionExpiredException, InterruptedException {
        transactionService.addTransaction(createTransaction(1.50, System.currentTimeMillis() - 5000));
        transactionService.addTransaction(createTransaction(1.60, System.currentTimeMillis()));

        StatisticsDto stats = transactionService.getRecentStats();

        StatisticsDto expected = new StatisticsDto(3.10, 1.55, 1.60, 1.50, 2L);

        assertEquals(expected, stats);

        TimeUnit.MILLISECONDS.sleep(2000);

        stats = transactionService.getRecentStats();
        expected = new StatisticsDto(1.60, 1.60, 1.60, 1.60, 1L);

        assertEquals(expected, stats);
    }

    @Test(expected = TransactionExpiredException.class)
    public void addTransaction_exception() throws TransactionExpiredException, InterruptedException {
        TransactionDto dto = new TransactionDto();
        dto.setAmount(1.23);
        dto.setTimestamp(System.currentTimeMillis() - 7000);

        transactionService.addTransaction(dto);
    }

    private TransactionDto createTransaction(Double amount, Long timestamp) {
        TransactionDto dto = new TransactionDto();
        dto.setAmount(amount);
        dto.setTimestamp(timestamp);
        return dto;
    }
}
