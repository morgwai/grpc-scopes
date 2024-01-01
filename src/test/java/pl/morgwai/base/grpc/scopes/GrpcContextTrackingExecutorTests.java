// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.concurrent.*;

import io.grpc.*;
import io.grpc.Status.Code;
import io.grpc.stub.StreamObserver;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.*;
import pl.morgwai.base.guice.scopes.ContextBoundRunnable;
import pl.morgwai.base.utils.concurrent.NamingThreadFactory;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static org.junit.Assert.*;



public class GrpcContextTrackingExecutorTests extends EasyMockSupport {



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
			1, 1,
			0L, MILLISECONDS,
			new LinkedBlockingQueue<>(1),
			new NamingThreadFactory("testExecutor"),
			rejectionHandler
		);
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
		assertTrue("task should complete", taskFinished.await(20L, MILLISECONDS));
		if (asyncError[0] != null) throw asyncError[0];
	}



	@Test
	public void testExecutionRejection() {
		final var outboundObserver = new StreamObserver<Long>() {

			Throwable capturedError;

			@Override public void onError(Throwable error) {
				capturedError = error;
			}

			@Override public void onCompleted() { fail("unexpected call"); }
			@Override public void onNext(Long l) { fail("unexpected call"); }
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
		assertTrue("rejectedTask should be a ContextBoundRunnable",
				rejectedTask instanceof ContextBoundRunnable);
		assertSame("rejectedTask should be overloadingTask",
				overloadingTask, ((ContextBoundRunnable) rejectedTask).getBoundClosure());
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
	public void testTryForceTerminatePreservesContext() throws InterruptedException {
		final var blockingTasksStarted = new CountDownLatch(1);
		final var blockingTaskFinished = new CountDownLatch(1);
		final Runnable blockingTask = () -> {
			blockingTasksStarted.countDown();
			try {
				taskBlockingLatch.await();
			} catch (InterruptedException ignored) {}
			blockingTaskFinished.countDown();
		};
		final Runnable queuedTask = () -> {};
		eventContext.executeWithinSelf(() -> {
			testSubject.execute(blockingTask);
			testSubject.execute(queuedTask);
		});
		assertTrue("blocking task should start",
				blockingTasksStarted.await(50L, MILLISECONDS));

		testSubject.shutdown();
		final var aftermath = testSubject.tryForceTerminate();
		assertTrue("blockingTask should finish after tryForceTerminate()",
				blockingTaskFinished.await(50L, MILLISECONDS));
		assertEquals("1 task should be running in the aftermath",
				1, aftermath.runningTasks.size());
		assertEquals("1 task should be unexecuted in the aftermath",
				1, aftermath.unexecutedTasks.size());
		final var runningTask = aftermath.runningTasks.get(0);
		final var unexecutedTask = aftermath.unexecutedTasks.get(0);
		assertTrue("runningTask should be a ContextBoundRunnable",
				runningTask instanceof ContextBoundRunnable);
		final var contextBoundRunningTask = (ContextBoundRunnable) runningTask;
		assertTrue("unexecutedTask should be a ContextBoundRunnable",
			unexecutedTask instanceof ContextBoundRunnable);
		final var contextBoundUnexecutedTask = (ContextBoundRunnable) unexecutedTask;
		assertSame("runningTask should be blockingTask",
				blockingTask, contextBoundRunningTask.getBoundClosure());
		assertSame("unexecutedTask should be queuedTask",
				queuedTask, contextBoundUnexecutedTask.getBoundClosure());
		assertSame("ctx should be preserved during tryForceTerminate()",
				eventContext, contextBoundRunningTask.getContexts().get(0));
		assertSame("ctx should be preserved during tryForceTerminate()",
				eventContext, contextBoundUnexecutedTask.getContexts().get(0));
	}



	@After
	public void tryTerminate() {
		testSubject.shutdown();
		taskBlockingLatch.countDown();
		try {
			grpcModule.enforceTerminationOfAllExecutors(50L, MILLISECONDS);
		} catch (InterruptedException ignored) {
		} finally {
			if ( !testSubject.isTerminated()) testSubject.shutdownNow();
		}
	}
}
