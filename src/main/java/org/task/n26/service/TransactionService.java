package org.task.n26.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.task.n26.dto.StatisticsDto;
import org.task.n26.dto.TransactionDto;
import org.task.n26.exception.TransactionExpiredException;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory
            .getLogger(TransactionService.class);

    @Value("${transaction.ttl.millis}")
    private Long transactionTtl;

    private final Long ROUNDING_TO_SECONDS = 1000L;
    private final ConcurrentMap<Long, StatisticsDto> lastMinuteStats = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private volatile StatisticsDto newStatsDto;
    private final ReadWriteLock statsLock = new ReentrantReadWriteLock(true);

    @PostConstruct
    public void setup() {
        executor.scheduleWithFixedDelay((Runnable) () -> {
            try {
                lastMinuteStats.entrySet().removeIf(e -> {
                    Long currentTimestamp = System.currentTimeMillis() / ROUNDING_TO_SECONDS;
                    return currentTimestamp - e.getKey() >= transactionTtl / ROUNDING_TO_SECONDS;
                });
                recalculateOverallStats();
            } catch (Exception e) {
                logger.error("Exception occurred in cleanup thread", e);
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    public void addTransaction(TransactionDto transactionDto) throws TransactionExpiredException {
        long currentTimestamp = System.currentTimeMillis();
        if (currentTimestamp - transactionDto.getTimestamp() > transactionTtl) {
            throw new TransactionExpiredException();
        }
        calculateStats(transactionDto);
    }

    public StatisticsDto getRecentStats() {
        statsLock.readLock().lock();
        try {
            return this.newStatsDto;
        } finally {
            statsLock.readLock().unlock();
        }
    }

    private void calculateStats(TransactionDto dto) {
        Long seconds = dto.getTimestamp() / ROUNDING_TO_SECONDS;
        while (true) {
            StatisticsDto statsDto = lastMinuteStats.get(seconds);
            if (statsDto == null) {
                statsDto = lastMinuteStats.putIfAbsent(seconds, new StatisticsDto(dto.getAmount(), dto.getAmount(), dto.getAmount(), dto.getAmount(), 1L));
                if (statsDto == null) {
                    recalculateOverallStats();
                    return;
                }
            }
            BigDecimal savedSum = BigDecimal.valueOf(statsDto.getSum());
            BigDecimal newVal = BigDecimal.valueOf(dto.getAmount());

            BigDecimal newSum = savedSum.add(newVal);
            BigDecimal newCount = BigDecimal.valueOf(statsDto.getCount() + 1);

            StatisticsDto newStatsDto = new StatisticsDto(newSum.doubleValue(),
                    savedSum.setScale(2).divide(newCount, RoundingMode.HALF_EVEN).doubleValue(),
                    statsDto.getMax() < dto.getAmount() ? dto.getAmount() : statsDto.getMax(),
                    statsDto.getMin() > dto.getAmount() ? dto.getAmount() : statsDto.getMin(),
                    newCount.longValue());

            if (lastMinuteStats.replace(seconds, statsDto, newStatsDto)) {
                recalculateOverallStats();
                return;
            }
        }
    }

    private void recalculateOverallStats() {
        try {
            statsLock.writeLock().lock();

            Long currentTimestamp = System.currentTimeMillis() / ROUNDING_TO_SECONDS;
            Iterator<Map.Entry<Long, StatisticsDto>> it = lastMinuteStats.entrySet().iterator();

            Double max = 0., min = 0.;
            BigDecimal count = new BigDecimal(0);
            BigDecimal sum = new BigDecimal(0);

            while (it.hasNext()) {
                Map.Entry<Long, StatisticsDto> entry = it.next();
                Long timestamp = entry.getKey();
                if (currentTimestamp - timestamp < transactionTtl / ROUNDING_TO_SECONDS) {
                    StatisticsDto dto = entry.getValue();

                    max = max > dto.getMax() ? max : dto.getMax();
                    min = min == 0. ? dto.getMin() : min < dto.getMin() ? min : dto.getMin();
                    count = count.add(BigDecimal.valueOf(dto.getCount()));
                    sum = sum.add(BigDecimal.valueOf(dto.getSum()));
                }
            }
            this.newStatsDto = new StatisticsDto(sum.doubleValue(),
                    count.intValue() > 0 ?
                            sum.setScale(2).divide(count, RoundingMode.HALF_EVEN).doubleValue()
                            : sum.doubleValue(),
                    max.doubleValue(),
                    min.doubleValue(),
                    count.longValue());
        } finally {
            statsLock.writeLock().unlock();
        }
    }
}
