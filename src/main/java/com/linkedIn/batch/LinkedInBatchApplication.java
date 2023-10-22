package com.linkedIn.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/*
https://docs.spring.io/spring-batch/docs/current/api/org/springframework/batch/core/configuration/annotation/EnableBatchProcessing.html#transactionManagerRef()
 */

@SpringBootApplication
public class LinkedInBatchApplication {

	/*
	https://stackoverflow.com/questions/75589969/execute-method-in-tasklet-is-not-invoking
	https://docs.spring.io/spring-boot/docs/2.0.x/reference/html/using-boot-auto-configuration.html#:~:text=Spring%20Boot%20auto%2Dconfiguration%20attempts,configures%20an%20in%2Dmemory%20database.
	 */

	@Autowired
	JobRepository jobRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Bean
	public JobExecutionDecider validator() {
		return new ItemValidator();
	}
	@Bean
	public JobExecutionDecider decider() {
		return new DeliveryDecider();
	}

	@Bean
	public Step initiateRefundStep() {
		return new StepBuilder("initiateRefundStep", jobRepository).tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
			System.out.println("Your refund has been initiated");
			return RepeatStatus.FINISHED;
		}, transactionManager).build();
	}
	@Bean
	public Step thankCustomerStep() {
		return new StepBuilder("thankCustomerStep", jobRepository).tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
			System.out.println("Thank you for your purchase");
			return RepeatStatus.FINISHED;
		}, transactionManager).build();
	}
	@Bean
	public Step leavePackageStep() {
		return new StepBuilder("leavePackageStep", jobRepository).tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
			System.out.println("Leave package at the door");
			return RepeatStatus.FINISHED;
		}, transactionManager).build();
	}
	@Bean
	public Step storePackageStep() {
		return new StepBuilder("storePackageStep", jobRepository).tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
			System.out.println("Store the package while the customer address is located");
			return RepeatStatus.FINISHED;
		}, transactionManager).build();
	}
	@Bean
	public Step givePackageToCustomerStep() {
		return new StepBuilder("givePackageToCustomer", jobRepository).tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
			System.out.println("Given the package to the customer");
			return RepeatStatus.FINISHED;
		}, transactionManager).build();
	}
	@Bean
	public Step driveToAddressStep() {
		boolean gotLost = false;
		return new StepBuilder("driveToAddressStep", jobRepository).tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
			if(gotLost) {
				throw new RuntimeException("Got lost while delivering the package");
			}
			System.out.println("Successfully arrived at the address");
			return RepeatStatus.FINISHED;
		}, transactionManager).build();
	}
	@Bean
	public Step packageItemStep() {
		return new StepBuilder("packageItemStep", jobRepository).tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
			String item = chunkContext.getStepContext().getJobParameters().get("item").toString();
			String date = chunkContext.getStepContext().getJobParameters().get("run.date").toString();
			System.out.println(String.format("The %s has been packaged at %s", item, date));
			return RepeatStatus.FINISHED;
		}, transactionManager).build();
	}

	@Bean
	public Job deliverPackageJob() {
		return new JobBuilder("deliverPackageJob", jobRepository)
				.start(packageItemStep())
				.next(driveToAddressStep())
					.on("FAILED").to(storePackageStep())
				.from(driveToAddressStep())
					.on("*").to(decider())
						.on("PRESENT").to(givePackageToCustomerStep())
							.next(validator()).on("CORRECT").to(thankCustomerStep())
							.from(validator()).on("INCORRECT").to(initiateRefundStep())
					.from(decider())
						.on("NOT PRESENT").to(leavePackageStep())
				.end()
				.build();
	}

	//Entry point for class when jar is built
	//https://docs.oracle.com/javase/tutorial/deployment/jar/appman.html
	public static void main(String[] args) {
		SpringApplication.run(LinkedInBatchApplication.class, args);
	}

}
