package com.linkedIn.batch;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;

public class ItemValidator implements JobExecutionDecider {
    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        String result = (int)(Math.random() * 10) < 7 ? "CORRECT" : "INCORRECT";
        System.out.println("The item delivered is " + result);
        return new FlowExecutionStatus(result);
    }
}
