// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.concurrent.*;

import io.grpc.*;
import io.grpc.Status.Code;
import io.grpc.stub.StreamObserver;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.*;
import pl.morgwai.base.guice.scopes.ContextBoundTask;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;



public class GrpcContextTrackingExecutorTest extends EasyMockSupport {



	final GrpcModule grpcModule = new GrpcModule();
	@Mock final ServerCall<Integer, Integer> mockRpc = mock(ServerCall.class);
	final ServerRpcContext rpcContext = new ServerRpcContext(mockRpc, new Metadata());
	final ListenerEventContext eventContext = grpcModule.newListenerEventContext(rpcContext);

	Runnable rejectedTask;
	Executor rejectingExecutor;
	final RejectedExecutionHandler rejectionHandler = (task, executor) -> {
		rejectedTask = task;
		rejectingExecutor = executor;
		throw new RejectedExecutionException("rejected " + task);
	};

	GrpcContextTrackingExecutor testSubject;
	CountDownLatch taskBlockingLatch;

	@Before
	public void setup() {
		taskBlockingLatch = new CountDownLatch(1);
		testSubject = grpcModule.newContextTrackingExecutor(
			"testExecutor",
			1,
			new LinkedBlockingQueue<>(1),
			rejectionHandler
		);
	}

	@After
	public void tryTerminate() {
		testSubject.shutdown();
		taskBlockingLatch.countDown();
		try {
			testSubject.awaitTermination(50L, TimeUnit.MILLISECONDS);
		} catch (InterruptedException ignored) {
		} finally {
			if ( !testSubject.isTerminated()) testSubject.shutdownNow();
		}
	}



	@Test
	public void testExecutionRejection() {
		final var outboundObserver = new StreamObserver<Long>() {

			Throwable capturedError;

			@Override public void onError(Throwable error) {
				capturedError = error;
			}

			@Override public void onCompleted() { throw new RuntimeException("unexpected call"); }
			@Override public void onNext(Long l) { throw new RuntimeException("unexpected call"); }
		};
		final Runnable overloadingTask = () -> {};
		try {
			eventContext.executeWithinSelf(() -> {
				testSubject.execute(() -> {  // make worker busy
					try {
						taskBlockingLatch.await();
					} catch (InterruptedException ignored) {}
				});
				testSubject.execute(() -> {});  // fill the queue

				testSubject.execute(outboundObserver, overloadingTask);  // method under test
			});
		} finally {
			taskBlockingLatch.countDown();
		}
		assertSame("rejectingExecutor should be testSubject",
				testSubject, rejectingExecutor);
		assertTrue("rejectedTask should be a ContextBoundTask",
				rejectedTask instanceof ContextBoundTask);
		assertSame("rejectedTask should be overloadingTask",
				overloadingTask, ((ContextBoundTask) rejectedTask).getBoundCallback());
		final var capturedError = outboundObserver.capturedError;
		assertTrue("argument passed to onError(...) should be a StatusException",
				capturedError instanceof StatusException
						|| capturedError instanceof StatusRuntimeException);
		final Status status = capturedError instanceof StatusException
				? ((StatusException) capturedError).getStatus()
				: ((StatusRuntimeException) capturedError).getStatus();
		assertSame("status reported to client should be UNAVAILABLE",
				Code.UNAVAILABLE, status.getCode());
	}



	@Test
	public void testTryForceTerminateUnwrapsTasks() throws InterruptedException {
		final var blockingTasksStarted = new CountDownLatch(1);
		final Runnable blockingTask = () -> {
			blockingTasksStarted.countDown();
			try {
				taskBlockingLatch.await();
			} catch (InterruptedException ignored) {}
		};
		final Runnable queuedTask = () -> {};
		try {
			eventContext.executeWithinSelf(() -> {
				testSubject.execute(blockingTask);
				testSubject.execute(queuedTask);
			});
			assertTrue("blocking task should start",
					blockingTasksStarted.await(50L, TimeUnit.MILLISECONDS));

			testSubject.shutdown();
			final var aftermath = testSubject.tryForceTerminate();
			assertEquals("1 task should be running in the aftermath",
					1, aftermath.runningTasks.size());
			assertEquals("1 task should be unexecuted in the aftermath",
					1, aftermath.unexecutedTasks.size());
			final var runningTask = aftermath.runningTasks.get(0);
			final var unexecutedTask = aftermath.unexecutedTasks.get(0);
			assertTrue("runningTask should be a ContextBoundTask",
					runningTask instanceof ContextBoundTask);
			assertTrue("unexecutedTask should be a ContextBoundTask",
					unexecutedTask instanceof ContextBoundTask);
			assertSame("runningTask should be blockingTask",
					blockingTask, ((ContextBoundTask) runningTask).getBoundCallback());
			assertSame("unexecutedTask should be queuedTask",
					queuedTask, ((ContextBoundTask) unexecutedTask).getBoundCallback());
		} finally {
			taskBlockingLatch.countDown();
		}
	}



	@Test
	public void testContextTracking() throws InterruptedException {
		final AssertionError[] asyncError = {null};
		final var taskFinished = new CountDownLatch(1);

		eventContext.executeWithinSelf(() -> testSubject.execute(() -> {
			try {
				assertSame("context should be transferred when passing task to executor",
						eventContext, grpcModule.listenerEventContextTracker.getCurrentContext());
			} catch (AssertionError e) {
				asyncError[0] = e;
			} finally {
				taskFinished.countDown();
			}
		}));
		assertTrue("task should complete", taskFinished.await(20L, TimeUnit.MILLISECONDS));
		if (asyncError[0] != null) throw asyncError[0];
	}
}
