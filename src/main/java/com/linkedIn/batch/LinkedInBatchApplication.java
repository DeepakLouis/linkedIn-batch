package com.linkedIn.batch;

import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.JobLauncherApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
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

	/*
		The below values will be fetched & prepareFlowers will not work
		VM options or system property -Dspring.batch.job.names=prepareFlowersJob
		spring.batch.job.names=prepareFlowersJob
	 */
//	@Value("${spring.batch.job.names}")
//	private String jobNames;

	@Bean
	public JobExecutionDecider validator() {
		return new ItemValidator();
	}
	@Bean
	public JobExecutionDecider decider() {
		return new DeliveryDecider();
	}
	@Bean
	public StepExecutionListener selectFlowerListener() {
		return new FlowerSelectionStepExecutionListener();
	}

	@Bean
	public Flow deliveryFlow() {
		return new FlowBuilder<SimpleFlow>("deliveryFlow")
				.start(driveToAddressStep())
					.on("FAILED").fail()
				.from(driveToAddressStep())
					.on("*").to(decider())
						.on("PRESENT").to(givePackageToCustomerStep())
					.next(validator()).on("CORRECT").to(thankCustomerStep())
					.from(validator()).on("INCORRECT").to(initiateRefundStep())
				.from(decider())
				.on("NOT PRESENT").to(leavePackageStep()).build();
	}

	@Bean
	public Flow billingFlow()  {
		return new FlowBuilder<SimpleFlow>("billingFlow")
				.start(sendInvoiceStep())
				.build();
	}

	@Bean
	public Step nestedBillingJobStep() {
		return new StepBuilder("nestedBillingJobStep", jobRepository).job(billingJob()).build();
	}
	@Bean
	public Step sendInvoiceStep() {
		return new StepBuilder("invoiceStep", jobRepository).tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
			System.out.println("Invoice is sent to the customer");
			return RepeatStatus.FINISHED;
		}, transactionManager).build();
	}
	@Bean
	public Job billingJob()  {
		return new JobBuilder("billingJob", jobRepository)
				.start(sendInvoiceStep())
				.build();
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
				//	.on("*").to(deliveryFlow()) -> External Flow
				//.next(nestedBillingJobStep()) -> Nested Job
				.split(new SimpleAsyncTaskExecutor())
				.add(deliveryFlow(), billingFlow())
				.end()
				.build();
	}

	@Bean
	public Step selectFlowersStep() {
		return new StepBuilder("selectFlowersStep", jobRepository).tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
			System.out.println("Gathering flowers for order");
			return RepeatStatus.FINISHED;
		}, transactionManager).listener(selectFlowerListener()).build();
	}
	@Bean
	public Step removeThornsStep() {
		return new StepBuilder("removeThornsStep", jobRepository).tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
			System.out.println("Remove thorns from roses");
			return RepeatStatus.FINISHED;
		}, transactionManager).build();
	}
	@Bean
	public Step arrangeFlowerStep() {
		return new StepBuilder("arrangeFlowerStep", jobRepository).tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
			System.out.println("Arrange flowers for order");
			return RepeatStatus.FINISHED;
		}, transactionManager).build();
	}
	@Bean
	public Job prepareFlowers() {
		return new JobBuilder("prepareFlowersJob", jobRepository)
				.start(selectFlowersStep())
					.on("TRIM_REQUIRED").to(removeThornsStep()).next(arrangeFlowerStep())
				.from(selectFlowersStep())
					.on("NO_TRIM_REQUIRED").to(arrangeFlowerStep())
				.next(deliveryFlow())
				.end()
				.build();
	}
	/*
	 When using value during multiple job
	 */
//	@Bean
//	public JobLauncherApplicationRunner jobLauncherApplicationRunner(JobLauncher jobLauncher, JobExplorer jobExplorer, JobRepository jobRepository) {
//		JobLauncherApplicationRunner runner = new JobLauncherApplicationRunner(jobLauncher, jobExplorer, jobRepository);
//		runner.setJobName(jobNames);
//		return runner;
//	}

	//Entry point for class when jar is built
	//https://docs.oracle.com/javase/tutorial/deployment/jar/appman.html
	public static void main(String[] args) throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
		/*
			Automatically runs the job
		 */
		SpringApplication.run(LinkedInBatchApplication.class, args);

		/*
		 * https://stackoverflow.com/questions/5951427/run-spring-batch-job-programmatically
		 *
		 * Disabling spring.batch.job.enabled=false
		 * Then you should manually run the job using below code
		 *
		 * If not manually run, only one job can be automatically run by spring batch
		 * https://stackoverflow.com/questions/25122103/how-to-select-which-spring-batch-job-to-run-based-on-application-argument-spri
		 */
//		SpringApplication app = new SpringApplication(LinkedInBatchApplication.class);
//		ConfigurableApplicationContext ctx = app.run(args);
//
//		JobLauncher jobLauncher = ctx.getBean(JobLauncher.class);
//
//		//Function name should be given
//		Job job = ctx.getBean("prepareFlowers", Job.class);
//		JobParameters jobParameters = new JobParametersBuilder().
//				addString("item", "mobiles")
//				.addString("run.date", "2023/02/19")
//				.toJobParameters();
//		jobLauncher.run(job, jobParameters);
	}

}
