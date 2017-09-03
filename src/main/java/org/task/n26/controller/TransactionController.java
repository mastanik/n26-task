package org.task.n26.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.task.n26.dto.TransactionDto;
import org.task.n26.exception.TransactionExpiredException;
import org.task.n26.service.TransactionService;

@RestController
public class TransactionController {

    private static final Logger logger = LoggerFactory
            .getLogger(TransactionController.class);

    @Autowired
    private TransactionService transactionService;

    @RequestMapping(value = "/transactions", method = RequestMethod.POST)
    public ResponseEntity addTransaction(@RequestBody TransactionDto transactionDto) {
        try {
            transactionService.addTransaction(transactionDto);
        } catch (TransactionExpiredException e) {
            logger.error("Transaction expired");
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @RequestMapping(value = "/statistics", method = RequestMethod.GET)
    public ResponseEntity getStatistics() {
        return new ResponseEntity<>(transactionService.getRecentStats(), HttpStatus.OK);
    }
}
